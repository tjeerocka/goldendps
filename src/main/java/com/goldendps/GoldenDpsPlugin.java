package com.goldendps;

import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HealType;
import com.goldendps.model.HealthWatcher;
import com.goldendps.model.HistoryStore;
import com.goldendps.model.HitAttributor;
import com.goldendps.model.MeterMode;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.TargetMatcher;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * GoldenDPS: a WoW-style damage and healing meter.
 *
 * <p>Threads: all game data is read and all counting is done on the client thread.
 * Party messages come in on the websocket thread and are moved to the client thread.
 * Files are written on the executor with a copy of the data. The panel gets a
 * ready-made {@link FightReport} on the Swing thread.</p>
 */
@Slf4j
@PluginDescriptor(
	name = "GoldenDPS",
	description = "WoW-style damage, DPS and healing meter for groups (shares through RuneLite Party)",
	tags = {"dps", "damage", "meter", "party", "healing", "boss", "recount", "details", "pvm"}
)
public class GoldenDpsPlugin extends Plugin implements FightTracker.Listener
{
	static final String LIVE = "live";
	static final String SESSION = "session";

	private static final long SAVE_EVERY_MS = 30_000;
	private static final int PANEL_EVERY_TICKS = 2;
	/** A name counts as "runs GoldenDPS" for this long after their last party message. */
	private static final long REPORTER_MS = 120_000;
	/** Eat / drink animation: not an attack. */
	private static final int EAT_ANIMATION = 829;

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private GoldenDpsConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private GoldenDpsOverlay overlay;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PartyService partyService;

	@Inject
	private WSClient wsClient;

	@Inject
	private Gson gson;

	@Inject
	private ScheduledExecutorService executor;

	private final FightTracker tracker = new FightTracker();
	private final HealthWatcher health = new HealthWatcher();
	private final Bosses bosses = new Bosses();
	private final HitAttributor attributor = new HitAttributor();
	private final TargetMatcher matcher = new TargetMatcher();
	/** Party members who send GoldenDPS data: name to time of their last message. */
	private final Map<String, Long> reporters = new HashMap<>();

	private HistoryStore store;
	private GoldenDpsPanel panel;
	private NavigationButton navButton;

	/** Account whose history is loaded now. -1 = none. */
	private long account = -1;
	private boolean dirty;
	private long lastSaveMs;
	private int ticks;
	private GoldenDpsUpdate outgoing = new GoldenDpsUpdate();
	private volatile FightReport overlayCache;

	@Provides
	GoldenDpsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GoldenDpsConfig.class);
	}

	@Override
	protected void startUp()
	{
		store = new HistoryStore(gson, new File(RuneLite.RUNELITE_DIR, "goldendps").toPath());
		tracker.setListener(this);
		applyConfig();

		overlayManager.add(overlay);
		wsClient.registerMessage(GoldenDpsUpdate.class);

		panel = new GoldenDpsPanel(this);
		BufferedImage icon = ImageUtil.loadImageResource(GoldenDpsPlugin.class, "icon.png");
		navButton = NavigationButton.builder()
			.tooltip("GoldenDPS")
			.icon(icon)
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				checkAccount();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(() ->
		{
			tracker.endNow(System.currentTimeMillis(), "Plugin turned off");
			saveAsync();
			tracker.clearHistory();
			account = -1;
			overlayCache = null;
			health.reset();
			outgoing = new GoldenDpsUpdate();
		});
		wsClient.unregisterMessage(GoldenDpsUpdate.class);
		overlayManager.remove(overlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	private void applyConfig()
	{
		tracker.setIdleTimeoutMs(config.idleSeconds() * 1000L);
		tracker.setMaxHistory(config.maxHistory());
		bosses.setExtraNames(config.extraBosses());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (!GoldenDpsConfig.GROUP.equals(e.getGroup()))
		{
			return;
		}
		clientThread.invoke(() ->
		{
			applyConfig();
			refreshOverlay();
			updatePanel();
		});
	}

	// ---------------------------------------------------------------- game events

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		Actor actor = e.getActor();
		Hitsplat hitsplat = e.getHitsplat();
		int type = hitsplat.getHitsplatType();
		int amount = hitsplat.getAmount();
		long now = System.currentTimeMillis();

		if (actor instanceof NPC)
		{
			NPC npc = (NPC) actor;
			String key = targetKey(client.getWorld(), npc.getIndex());
			String name = npc.getName();
			boolean boss = bosses.isBoss(npc.getId(), name);
			WorldPoint pos = npc.getWorldLocation();
			if (pos != null)
			{
				matcher.seen(key, pos.getX(), pos.getY(), pos.getPlane());
			}

			if (hitsplat.isMine())
			{
				boolean miss = type == HitsplatID.BLOCK_ME || amount <= 0;
				// playerHit first, then seenHit (see FightTracker)
				tracker.playerHit(now, localName(), PlayerKind.LOCAL, key, npc.getId(), name, boss, amount, miss);
				tracker.seenHit(now, key, npc.getId(), name, boss, amount);
				if (!miss)
				{
					health.myHit(client.getTickCount());
				}
				outgoing.h.add(new GoldenDpsUpdate.Hit(npc.getIndex(), npc.getId(), name, amount, miss, boss, pos));
			}
			else if (hitsplat.isOthers() && isGroupMode())
			{
				// other people only count in a party or an instance (raid, boss room)
				tracker.seenHit(now, key, npc.getId(), name, boss, amount);
				if (config.estimateOthers())
				{
					// a guess: who started an attack on this NPC a few ticks ago?
					String who = attributor.assign(client.getTickCount(), key);
					if (who != null && !isReporter(who, now) && !who.equals(localName()))
					{
						boolean miss = type == HitsplatID.BLOCK_OTHER || amount <= 0;
						tracker.estimateHit(now, who, key, amount, miss);
					}
				}
			}
			else if (isEffect(type))
			{
				tracker.effectHit(now, key, npc.getId(), name, boss, amount);
			}
		}
		else if (actor instanceof Player && actor != client.getLocalPlayer())
		{
			if (!isGroupMode())
			{
				return;
			}
			String who = cleanName(actor.getName());
			if (config.nearbyDamageTaken() && who != null && amount > 0 && isDamageOnPlayer(hitsplat)
				&& !isReporter(who, now))
			{
				tracker.seenDamageTaken(now, who, amount);
			}
		}
		else if (actor instanceof Player)
		{
			if (amount > 0 && isDamageOnPlayer(hitsplat))
			{
				health.damageOnMe(amount);
				if (tracker.getCurrent() != null)
				{
					tracker.damageTaken(now, localName(), PlayerKind.LOCAL, amount);
					outgoing.taken += amount;
				}
			}
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Actor actor = e.getActor();
		if (!(actor instanceof Player) || actor == client.getLocalPlayer() || !config.estimateOthers()
			|| !isGroupMode())
		{
			return;
		}
		int anim = actor.getAnimation();
		Actor target = actor.getInteracting();
		if (anim == -1 || anim == EAT_ANIMATION || !(target instanceof NPC))
		{
			return;
		}
		String who = cleanName(actor.getName());
		if (who != null)
		{
			attributor.attack(client.getTickCount(), who, targetKey(client.getWorld(), ((NPC) target).getIndex()));
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		if (e.getSkill() == Skill.HITPOINTS)
		{
			health.hpChanged(e.getBoostedLevel());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked e)
	{
		String option = Text.removeTags(e.getMenuOption());
		if (option == null)
		{
			return;
		}
		int tick = client.getTickCount();
		if (option.equals("Eat") || option.equals("Drink") || option.equals("Quaff"))
		{
			health.consumed(tick);
		}
		else if (option.equals("Cast"))
		{
			String target = Text.removeTags(e.getMenuTarget());
			if (target != null && (target.startsWith("Heal Other") || target.startsWith("Heal Group")))
			{
				health.healOtherCast(tick);
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		NPC npc = e.getNpc();
		if (npc.isDead())
		{
			tracker.npcDied(System.currentTimeMillis(), targetKey(client.getWorld(), npc.getIndex()),
				bosses.isBoss(npc.getId(), npc.getName()));
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		long now = System.currentTimeMillis();
		int tick = client.getTickCount();

		for (HealthWatcher.Heal heal : health.endTick(tick))
		{
			if (heal.type == HealType.RECEIVED || tracker.getCurrent() == null)
			{
				continue;
			}
			tracker.heal(now, localName(), PlayerKind.LOCAL, heal.type, heal.amount);
			switch (heal.type)
			{
				case FOOD:
					outgoing.food += heal.amount;
					break;
				case LIFESTEAL:
					outgoing.life += heal.amount;
					break;
				case REGEN:
					outgoing.regen += heal.amount;
					break;
				case HEAL_OTHER:
					outgoing.other += heal.amount;
					break;
				default:
					break;
			}
		}

		tracker.tick(now);
		sendPartyUpdate();

		if (tracker.getCurrent() != null)
		{
			dirty = true;
		}
		if (dirty && now - lastSaveMs >= SAVE_EVERY_MS)
		{
			saveAsync();
		}

		refreshOverlay();
		if (++ticks % PANEL_EVERY_TICKS == 0)
		{
			updatePanel();
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			checkAccount();
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			health.reset();
			attributor.clear();
			matcher.clear();
			outgoing = new GoldenDpsUpdate();
			tracker.endNow(System.currentTimeMillis(), state == GameState.HOPPING ? "World hop" : "Logged out");
			saveAsync();
			refreshOverlay();
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown e)
	{
		// Last save before the client closes. The client thread is idle by now.
		try
		{
			tracker.endNow(System.currentTimeMillis(), "Client closed");
			final long acc = account;
			final List<Fight> snapshot = snapshot();
			if (acc != -1)
			{
				e.waitFor(executor.submit(() -> write(acc, snapshot)));
			}
		}
		catch (ConcurrentModificationException ex)
		{
			log.warn("GoldenDPS: could not save on shutdown", ex);
		}
	}

	// ---------------------------------------------------------------- party

	@Subscribe
	public void onGoldenDpsUpdate(GoldenDpsUpdate update)
	{
		// websocket thread: move to the client thread
		clientThread.invokeLater(() -> applyPartyUpdate(update));
	}

	private void applyPartyUpdate(GoldenDpsUpdate u)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && u.getMemberId() == local.getMemberId())
		{
			return;
		}
		String name = cleanName(u.n);
		if (name == null)
		{
			PartyMember m = partyService.getMemberById(u.getMemberId());
			name = m == null ? null : cleanName(m.getDisplayName());
		}
		if (name == null || name.equals(localName()))
		{
			return;
		}

		long now = System.currentTimeMillis();
		reporters.put(name, now);
		if (u.h != null)
		{
			for (GoldenDpsUpdate.Hit hit : u.h)
			{
				boolean boss = hit.boss || bosses.isBoss(hit.id, hit.nm);
				tracker.playerHit(now, name, PlayerKind.PARTY, partyTargetKey(u.w, hit, name), hit.id, hit.nm,
					boss, hit.a, hit.miss);
			}
		}
		tracker.damageTaken(now, name, PlayerKind.PARTY, u.taken);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.FOOD, u.food);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.LIFESTEAL, u.life);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.REGEN, u.regen);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.HEAL_OTHER, u.other);
	}

	private void sendPartyUpdate()
	{
		GoldenDpsUpdate u = outgoing;
		outgoing = new GoldenDpsUpdate();
		if (u.isEmpty() || !config.shareWithParty() || !partyService.isInParty()
			|| partyService.getLocalMember() == null)
		{
			return;
		}
		u.w = client.getWorld();
		u.n = localName();
		partyService.send(u);
	}

	// ---------------------------------------------------------------- saving

	@Override
	public void fightEnded(Fight fight)
	{
		dirty = true;
		saveAsync();
	}

	/** Loads the history of the account that is logged in (once per account). */
	private void checkAccount()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == account)
		{
			return;
		}
		if (account != -1)
		{
			saveAsync();
		}
		tracker.endNow(System.currentTimeMillis(), "Account changed");
		tracker.clearHistory();
		account = hash;
		executor.execute(() ->
		{
			try
			{
				List<Fight> loaded = store.load(hash);
				clientThread.invokeLater(() ->
				{
					if (account != hash)
					{
						return;
					}
					// fights that ended while loading come first (newest first)
					List<Fight> all = new ArrayList<>(tracker.getHistory());
					all.addAll(loaded);
					tracker.setHistory(all);
					refreshOverlay();
					updatePanel();
				});
			}
			catch (Exception ex)
			{
				log.warn("GoldenDPS: could not load history", ex);
			}
		});
	}

	/** Copy of the history (plus the running fight) for saving. Client thread. */
	private List<Fight> snapshot()
	{
		List<Fight> out = new ArrayList<>();
		Fight current = tracker.getCurrent();
		if (current != null && current.knownDamage() > 0)
		{
			Fight c = current.copy();
			c.endMs = Math.max(c.startMs, c.lastActivityMs);
			c.endReason = "Not finished (saved)";
			out.add(c);
		}
		for (Fight f : tracker.getHistory())
		{
			out.add(f.copy());
		}
		return out;
	}

	/** Copy on the client thread, write on the executor. */
	private void saveAsync()
	{
		if (account == -1 || store == null)
		{
			return;
		}
		final long acc = account;
		final List<Fight> snapshot = snapshot();
		dirty = false;
		lastSaveMs = System.currentTimeMillis();
		executor.execute(() -> write(acc, snapshot));
	}

	private void write(long acc, List<Fight> fights)
	{
		try
		{
			store.save(acc, fights, System.currentTimeMillis());
		}
		catch (Exception ex)
		{
			log.warn("GoldenDPS: could not save history", ex);
		}
	}

	// ---------------------------------------------------------------- for overlay and panel

	/** Report for the overlay, made once per tick (not every frame). Null when there is nothing. */
	FightReport overlayReport()
	{
		return overlayCache;
	}

	/** Client thread. */
	private void refreshOverlay()
	{
		long now = System.currentTimeMillis();
		Fight f = config.overlayScope() == GoldenDpsConfig.Scope.SESSION
			? tracker.getSessionTotal(now)
			: tracker.getDisplayFight();
		overlayCache = f == null ? null : FightReport.of(f, config.bossOnly(), config.countRegen(), now);
	}

	boolean isFightRunning()
	{
		return tracker.getCurrent() != null;
	}

	/** Builds the panel data on the client thread and hands it to Swing. */
	void updatePanel()
	{
		GoldenDpsPanel p = panel;
		if (p == null || !p.isPanelOpen())
		{
			return;
		}
		long now = System.currentTimeMillis();
		String selected = p.getSelectedId();

		List<GoldenDpsPanel.Choice> choices = new ArrayList<>();
		Fight current = tracker.getCurrent();
		choices.add(new GoldenDpsPanel.Choice(LIVE, current != null ? "Current fight (live)" : "Current / last fight"));
		choices.add(new GoldenDpsPanel.Choice(SESSION, "Session total"));
		for (Fight f : tracker.getHistory())
		{
			choices.add(new GoldenDpsPanel.Choice(f.id, GoldenDpsPanel.label(f, now)));
		}

		Fight fight;
		if (SESSION.equals(selected))
		{
			fight = tracker.getSessionTotal(now);
		}
		else if (LIVE.equals(selected) || selected == null)
		{
			fight = tracker.getDisplayFight();
		}
		else
		{
			fight = tracker.findFight(selected);
		}

		GoldenDpsPanel.Data data = new GoldenDpsPanel.Data();
		data.choices = choices;
		data.bossOnly = config.bossOnly();
		if (fight != null)
		{
			data.report = FightReport.of(fight, config.bossOnly(), config.countRegen(), now);
			data.running = fight.isActive();
			data.endReason = fight.endReason;
			data.fights = fight.mergedFights;
			data.startMs = fight.startMs;
		}
		data.localName = localName();
		SwingUtilities.invokeLater(() -> p.update(data));
	}

	void setMode(MeterMode mode)
	{
		configManager.setConfiguration(GoldenDpsConfig.GROUP, GoldenDpsConfig.MODE_KEY, mode);
	}

	void setBossOnly(boolean bossOnly)
	{
		configManager.setConfiguration(GoldenDpsConfig.GROUP, GoldenDpsConfig.BOSS_ONLY_KEY, bossOnly);
	}

	void setScope(GoldenDpsConfig.Scope scope)
	{
		configManager.setConfiguration(GoldenDpsConfig.GROUP, GoldenDpsConfig.SCOPE_KEY, scope);
	}

	void resetSession()
	{
		clientThread.invoke(() ->
		{
			tracker.resetSession(System.currentTimeMillis());
			saveAsync();
			refreshOverlay();
			updatePanel();
		});
	}

	void endFight()
	{
		clientThread.invoke(() ->
		{
			tracker.endNow(System.currentTimeMillis(), "Ended by you");
			refreshOverlay();
			updatePanel();
		});
	}

	void clearHistory()
	{
		clientThread.invoke(() ->
		{
			tracker.clearHistory();
			saveAsync();
			refreshOverlay();
			updatePanel();
		});
	}

	void requestPanelUpdate()
	{
		clientThread.invoke(this::updatePanel);
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Count other people only in a RuneLite party or in an instance (raids, boss rooms).
	 * Everywhere else GoldenDPS only counts you.
	 */
	private boolean isGroupMode()
	{
		if (partyService.isInParty())
		{
			return true;
		}
		WorldView wv = client.getTopLevelWorldView();
		return wv != null && wv.isInstance();
	}

	/** Your target key if the party member hit the same NPC as you see, else a separate key. */
	private String partyTargetKey(int world, GoldenDpsUpdate.Hit hit, String sender)
	{
		if (world == client.getWorld())
		{
			// the NPC may be near you but not hit by you yet: remember where it is
			WorldView wv = client.getTopLevelWorldView();
			NPC npc = wv == null ? null : wv.npcs().byIndex(hit.i);
			WorldPoint pos = npc == null ? null : npc.getWorldLocation();
			if (pos != null && npc.getId() == hit.id)
			{
				matcher.seen(targetKey(world, hit.i), pos.getX(), pos.getY(), pos.getPlane());
			}
		}
		return matcher.partyKey(client.getWorld(), world, hit.i, hit.x, hit.y, hit.p, sender);
	}

	private boolean isReporter(String name, long now)
	{
		Long last = reporters.get(name);
		return last != null && now - last <= REPORTER_MS;
	}

	static String targetKey(int world, int npcIndex)
	{
		return TargetMatcher.key(world, npcIndex);
	}

	private String localName()
	{
		Player p = client.getLocalPlayer();
		String n = p == null ? null : cleanName(p.getName());
		return n == null ? "You" : n;
	}

	private static String cleanName(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = Text.removeTags(name).replace(' ', ' ').trim();
		return n.isEmpty() || n.equals("<unknown>") ? null : n;
	}

	/** Damage over time hitsplats: nobody owns them. */
	private static boolean isEffect(int type)
	{
		return type == HitsplatID.POISON || type == HitsplatID.VENOM || type == HitsplatID.DISEASE
			|| type == HitsplatID.BURN || type == HitsplatID.BLEED;
	}

	private static boolean isDamageOnPlayer(Hitsplat h)
	{
		int type = h.getHitsplatType();
		return h.isMine() || h.isOthers() || isEffect(type) || type == HitsplatID.DOOM;
	}
}
