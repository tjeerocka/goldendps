package com.goldendps;

import com.goldendps.model.AttackSource;
import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HealType;
import com.goldendps.model.HealthWatcher;
import com.goldendps.model.HistoryStore;
import com.goldendps.model.HitAttributor;
import com.goldendps.model.MeterMode;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.RaidZone;
import com.goldendps.model.ReportFormatter;
import com.goldendps.model.TargetMatcher;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.File;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.concurrent.Future;
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
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
import net.runelite.client.party.messages.PartyChatMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

/**
 * Damage and healing meter. Game data is read and counted on the client thread; party messages
 * are moved there from the websocket thread, and the panel receives finished reports on the
 * Swing thread.
 */
@Slf4j
@PluginDescriptor(
	name = "GoldenDPS",
	description = "DPS, damage and healing meter for groups and individuals",
	tags = {"dps", "damage", "meter", "party", "healing", "boss", "pvm", "raid"}
)
public class GoldenDpsPlugin extends Plugin implements FightTracker.Listener
{
	static final String LIVE = "live";
	static final String SESSION = "session";

	private static final int PANEL_EVERY_TICKS = 2;
	private static final int EAT_ANIMATION = 829;
	private static final int MAX_SOURCE_LENGTH = 64;

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
	private HeadIconOverlay headIconOverlay;

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

	@Inject
	private ChatNotifier chat;

	@Inject
	private PartyManager party;

	@Inject
	private LocalHealing healing;

	private final FightTracker tracker = new FightTracker();
	private final Bosses bosses = new Bosses();
	private final HitAttributor attributor = new HitAttributor();
	private final TargetMatcher matcher = new TargetMatcher();
	private final AttackSource attackSource = new AttackSource();
	private final RaidZone raidZone = new RaidZone();
	private final FunEffects fun = new FunEffects();

	private FightStorage storage;
	private GoldenDpsPanel panel;
	private NavigationButton navButton;
	private GoldenDpsUpdate outgoing = new GoldenDpsUpdate();
	private int lastSpecEnergy = -1;
	private int ticks;
	private volatile FightReport overlayCache;
	private volatile BossMeter bossCache;

	/** The boss meter as the overlay draws it, built once per tick. */
	static class BossMeter
	{
		final FightReport report;
		final boolean dead;
		/** Seconds until it hides, or -1 while the boss lives. */
		final int hidesIn;
		/** "NEW PB!", "PB 1:10" or null. */
		final String pb;

		BossMeter(FightReport report, boolean dead, int hidesIn, String pb)
		{
			this.report = report;
			this.dead = dead;
			this.hidesIn = hidesIn;
			this.pb = pb;
		}
	}

	@Provides
	GoldenDpsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GoldenDpsConfig.class);
	}

	@Override
	protected void startUp()
	{
		HistoryStore store = new HistoryStore(gson, new File(RuneLite.RUNELITE_DIR, "goldendps").toPath());
		storage = new FightStorage(tracker, store, executor, clientThread, () ->
		{
			refreshOverlay();
			updatePanel();
		});
		tracker.setListener(this);
		applyConfig();

		overlayManager.add(overlay);
		overlayManager.add(headIconOverlay);
		wsClient.registerMessage(GoldenDpsUpdate.class);
		wsClient.registerMessage(GoldenDpsPartyMessage.class);

		panel = new GoldenDpsPanel(this);
		navButton = NavigationButton.builder()
			.tooltip("GoldenDPS")
			.icon(ImageUtil.loadImageResource(GoldenDpsPlugin.class, "icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				storage.loadAccount(client.getAccountHash(), config.countRegen());
			}
		});
	}

	@Override
	protected void shutDown()
	{
		clientThread.invoke(() ->
		{
			tracker.endNow(System.currentTimeMillis(), "Plugin turned off");
			storage.save();
			storage.unload();
			tracker.clearHistory();
			overlayCache = null;
			bossCache = null;
			healing.reset();
			outgoing = new GoldenDpsUpdate();
		});
		wsClient.unregisterMessage(GoldenDpsUpdate.class);
		wsClient.unregisterMessage(GoldenDpsPartyMessage.class);
		overlayManager.remove(overlay);
		overlayManager.remove(headIconOverlay);
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	private void applyConfig()
	{
		tracker.setIdleTimeoutMs(config.idleSeconds() * 1000L);
		tracker.setPauseAfterMs(config.pauseSeconds() * 1000L);
		tracker.setMaxHistory(config.maxHistory());
		bosses.setExtraNames(config.extraBosses());
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged e)
	{
		if (GoldenDpsConfig.GROUP.equals(e.getGroup()))
		{
			clientThread.invoke(() ->
			{
				applyConfig();
				refreshOverlay();
				updatePanel();
			});
		}
	}

	// ---- game events ----

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied e)
	{
		Actor actor = e.getActor();
		Hitsplat hitsplat = e.getHitsplat();
		long now = System.currentTimeMillis();
		if (actor instanceof NPC)
		{
			npcHit((NPC) actor, hitsplat, now);
		}
		else if (actor == client.getLocalPlayer())
		{
			int amount = hitsplat.getAmount();
			if (amount > 0 && isDamageOnPlayer(hitsplat))
			{
				healing.damageOnYou(amount);
				if (tracker.getCurrent() != null)
				{
					tracker.damageTaken(now, localName(), PlayerKind.LOCAL, amount);
					outgoing.taken += amount;
				}
			}
		}
		else if (actor instanceof Player && config.nearbyDamageTaken() && isGroupMode())
		{
			String who = Names.clean(actor.getName());
			if (who != null && hitsplat.getAmount() > 0 && isDamageOnPlayer(hitsplat) && !party.isReporter(who, now))
			{
				tracker.seenDamageTaken(now, who, hitsplat.getAmount());
			}
		}
	}

	private void npcHit(NPC npc, Hitsplat hitsplat, long now)
	{
		int type = hitsplat.getHitsplatType();
		int amount = hitsplat.getAmount();
		String key = TargetMatcher.key(client.getWorld(), npc.getIndex());
		String name = npc.getName();
		boolean boss = bosses.isBoss(npc.getId(), name);
		WorldPoint pos = npc.getWorldLocation();
		if (pos != null)
		{
			matcher.seen(key, pos.getX(), pos.getY(), pos.getPlane());
		}

		if (hitsplat.isMine())
		{
			int tick = client.getTickCount();
			boolean miss = type == HitsplatID.BLOCK_ME || amount <= 0;
			String source = attackSource.sourceForHit(tick, healing.weaponName());
			// playerHit before seenHit, so the first hit of a fight is also seen
			tracker.playerHit(now, localName(), PlayerKind.LOCAL, key, npc.getId(), name, boss, amount, miss, source);
			tracker.seenHit(now, key, npc.getId(), name, boss, amount);
			if (!miss)
			{
				healing.youHit(npc, amount, tick);
			}
			GoldenDpsUpdate.Hit sent = new GoldenDpsUpdate.Hit(npc.getIndex(), npc.getId(), name, amount, miss, boss, pos);
			sent.source = source;
			outgoing.hits.add(sent);
		}
		else if (hitsplat.isOthers() && isGroupMode())
		{
			tracker.seenHit(now, key, npc.getId(), name, boss, amount);
			if (config.estimateOthers())
			{
				String who = attributor.assign(client.getTickCount(), key);
				if (who != null && !party.isReporter(who, now) && !who.equals(localName()))
				{
					tracker.estimateHit(now, who, key, amount, type == HitsplatID.BLOCK_OTHER || amount <= 0);
				}
			}
		}
		else if (isEffect(type))
		{
			tracker.effectHit(now, key, npc.getId(), name, boss, amount);
		}
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		Actor actor = e.getActor();
		if (actor == null)
		{
			return;
		}
		int anim = actor.getAnimation();
		Actor target = actor.getInteracting();
		if (anim == -1 || anim == EAT_ANIMATION || !(target instanceof NPC))
		{
			return;
		}
		if (actor == client.getLocalPlayer())
		{
			attackSource.attack(client.getTickCount(), healing.weaponName());
		}
		else if (actor instanceof Player && config.estimateOthers() && isGroupMode())
		{
			String who = Names.clean(actor.getName());
			if (who != null)
			{
				attributor.attack(client.getTickCount(), who, TargetMatcher.key(client.getWorld(), ((NPC) target).getIndex()));
			}
		}
	}

	@Subscribe
	public void onStatChanged(StatChanged e)
	{
		if (e.getSkill() == Skill.HITPOINTS)
		{
			healing.hpChanged(e.getLevel(), e.getBoostedLevel());
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
			healing.eatClicked(e.getItemId(), tick);
		}
		else if (option.equals("Cast"))
		{
			String target = Text.removeTags(e.getMenuTarget());
			if (target == null)
			{
				return;
			}
			if (target.startsWith("Heal Other") || target.startsWith("Heal Group"))
			{
				healing.healOtherCast(tick);
			}
			else if (target.contains("->"))
			{
				attackSource.castClicked(tick, target);
			}
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() == InventoryID.INV)
		{
			healing.inventoryChanged(e.getItemContainer(), client.getTickCount());
		}
	}

	/** While GoldenDPS shows a line over a player, their typed chat only shows in the chatbox. */
	@Subscribe
	public void onOverheadTextChanged(OverheadTextChanged e)
	{
		if (e.getActor() instanceof Player && hasFunLine((Player) e.getActor()))
		{
			e.getActor().setOverheadText("");
		}
	}

	private boolean hasFunLine(Player p)
	{
		String name = Names.clean(p.getName());
		return name != null && config.healerSnack() && fun.line(name, System.currentTimeMillis()) != null;
	}

	/** Hides chat that was already over a head when a GoldenDPS line starts. */
	private void hideOverheadChat()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || !fun.anyone(System.currentTimeMillis()))
		{
			return;
		}
		for (Player p : wv.players())
		{
			String text = p == null ? null : p.getOverheadText();
			if (text != null && !text.isEmpty() && hasFunLine(p))
			{
				p.setOverheadText("");
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned e)
	{
		NPC npc = e.getNpc();
		if (npc.isDead())
		{
			tracker.npcDied(System.currentTimeMillis(), TargetMatcher.key(client.getWorld(), npc.getIndex()),
				bosses.isBoss(npc.getId(), npc.getName()));
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		long now = System.currentTimeMillis();
		int tick = client.getTickCount();

		int spec = client.getVarpValue(VarPlayerID.SA_ENERGY);
		if (lastSpecEnergy >= 0 && spec < lastSpecEnergy)
		{
			attackSource.specUsed(tick, healing.weaponName());
		}
		lastSpecEnergy = spec;

		List<HealthWatcher.Heal> heals = healing.endTick(tick);
		if (tracker.getCurrent() != null)
		{
			heals.forEach(heal -> addOwnHeal(now, heal));
		}

		tracker.tick(now);
		sendPartyUpdate();
		storage.tick(now);
		raidTick(now);
		party.tick(now);
		refreshOverlay();
		hideOverheadChat();
		if (++ticks % PANEL_EVERY_TICKS == 0)
		{
			updatePanel();
		}
	}

	private void addOwnHeal(long now, HealthWatcher.Heal heal)
	{
		if (heal.type == HealType.RECEIVED)
		{
			return;
		}
		tracker.heal(now, localName(), PlayerKind.LOCAL, heal.type, heal.amount, heal.overheal);
		switch (heal.type)
		{
			case FOOD:
				outgoing.food += heal.amount;
				outgoing.foodOverheal += heal.overheal;
				break;
			case LIFESTEAL:
				outgoing.lifesteal += heal.amount;
				outgoing.lifestealOverheal += heal.overheal;
				break;
			case REGEN:
				outgoing.regen += heal.amount;
				break;
			case HEAL_OTHER:
				outgoing.healOther += heal.amount;
				break;
			default:
				break;
		}
	}

	/** One fight per raid: starts when you enter the raid instance and ends when you leave it. */
	private void raidTick(long now)
	{
		String raid = null;
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (config.raidFight() && me != null && me.getLocalLocation() != null && wv != null)
		{
			WorldPoint p = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
			if (p != null)
			{
				raid = RaidZone.raidFor(p.getRegionID(), wv.isInstance());
			}
		}
		RaidZone.Change c = raidZone.tick(raid);
		if (c.start != null)
		{
			tracker.startRaid(now, c.start);
			chat.info(c.start + " started. The meter counts the whole raid until you leave.");
		}
		else if (c.end)
		{
			tracker.endRaid(now, "Left the raid");
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			storage.loadAccount(client.getAccountHash(), config.countRegen());
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			long now = System.currentTimeMillis();
			String reason = state == GameState.HOPPING ? "World hop" : "Logged out";
			healing.reset();
			attributor.clear();
			matcher.clear();
			attackSource.reset();
			raidZone.reset();
			party.loggedOut();
			lastSpecEnergy = -1;
			outgoing = new GoldenDpsUpdate();
			tracker.endRaid(now, reason);
			tracker.endNow(now, reason);
			storage.save();
			refreshOverlay();
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown e)
	{
		// not on the client thread, but the game loop has stopped by now
		try
		{
			tracker.endNow(System.currentTimeMillis(), "Client closed");
			Future<?> write = storage.saveNow();
			if (write != null)
			{
				e.waitFor(write);
			}
		}
		catch (ConcurrentModificationException ex)
		{
			log.warn("Could not save on shutdown", ex);
		}
	}

	@Override
	public void fightEnded(Fight fight)
	{
		storage.fightEnded(fight, config.countRegen());
	}

	// ---- party ----

	@Subscribe
	public void onGoldenDpsUpdate(GoldenDpsUpdate update)
	{
		clientThread.invokeLater(() -> applyPartyUpdate(update));
	}

	@Subscribe
	public void onGoldenDpsPartyMessage(GoldenDpsPartyMessage message)
	{
		clientThread.invokeLater(() ->
		{
			party.onMessage(message);
			updatePanel();
		});
	}

	@Subscribe
	public void onUserPart(UserPart e)
	{
		clientThread.invokeLater(() -> party.onMemberLeft(e.getMemberId()));
	}

	private void applyPartyUpdate(GoldenDpsUpdate u)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && u.getMemberId() == local.getMemberId())
		{
			return;
		}
		party.pluginSeen(u.getMemberId());
		String name = Names.clean(u.name);
		if (name == null)
		{
			PartyMember m = partyService.getMemberById(u.getMemberId());
			name = m == null ? null : Names.clean(m.getDisplayName());
		}
		if (name == null || name.equals(localName()))
		{
			return;
		}

		long now = System.currentTimeMillis();
		party.reporterSeen(name, now);
		boolean together = !config.othersOnlyTogether() || party.isInstanceMate(name);
		boolean counted = together;
		if (u.hits != null)
		{
			for (GoldenDpsUpdate.Hit hit : u.hits)
			{
				boolean boss = hit.boss || bosses.isBoss(hit.npcId, hit.npcName);
				if (!together && !(boss && isSameNpc(u.world, hit)))
				{
					continue;
				}
				counted = true;
				String source = hit.source == null || hit.source.length() > MAX_SOURCE_LENGTH ? null : hit.source;
				tracker.playerHit(now, name, PlayerKind.PARTY, partyTargetKey(u.world, hit, name), hit.npcId, hit.npcName,
					boss, hit.amount, hit.miss, source);
			}
		}
		Fight current = tracker.getCurrent();
		if (!counted && (current == null || !current.players.containsKey(name)))
		{
			return;
		}
		tracker.damageTaken(now, name, PlayerKind.PARTY, u.taken);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.FOOD, u.food, u.foodOverheal);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.LIFESTEAL, u.lifesteal, u.lifestealOverheal);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.REGEN, u.regen);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.HEAL_OTHER, u.healOther);
	}

	private void sendPartyUpdate()
	{
		GoldenDpsUpdate u = outgoing;
		outgoing = new GoldenDpsUpdate();
		if (u.isEmpty() || !config.shareWithParty() || !party.inParty())
		{
			return;
		}
		u.world = client.getWorld();
		u.name = localName();
		partyService.send(u);
	}

	/**
	 * Other players count in an instance, and in a party while you fight a boss
	 * (or always in a party when "others only when together" is off).
	 */
	private boolean isGroupMode()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv != null && wv.isInstance())
		{
			return true;
		}
		if (!partyService.isInParty())
		{
			return false;
		}
		Fight f = tracker.getCurrent();
		return !config.othersOnlyTogether() || (f != null && f.hasBossTarget());
	}

	/** The NPC in your scene that a party member hit, or null when you cannot see it. */
	private NPC npcInScene(int world, GoldenDpsUpdate.Hit hit)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (world != client.getWorld() || wv == null)
		{
			return null;
		}
		NPC npc = wv.npcs().byIndex(hit.npcIndex);
		return npc != null && npc.getId() == hit.npcId ? npc : null;
	}

	private boolean isSameNpc(int world, GoldenDpsUpdate.Hit hit)
	{
		return npcInScene(world, hit) != null;
	}

	/** Your key for the NPC when it is the same one you see, otherwise a key of its own. */
	private String partyTargetKey(int world, GoldenDpsUpdate.Hit hit, String sender)
	{
		NPC npc = npcInScene(world, hit);
		WorldPoint pos = npc == null ? null : npc.getWorldLocation();
		if (pos != null)
		{
			matcher.seen(TargetMatcher.key(world, hit.npcIndex), pos.getX(), pos.getY(), pos.getPlane());
		}
		return matcher.partyKey(client.getWorld(), world, hit.npcIndex, hit.x, hit.y, hit.plane, sender);
	}

	// ---- overlay and panel ----

	FightReport overlayReport()
	{
		return overlayCache;
	}

	BossMeter bossMeter()
	{
		return bossCache;
	}

	boolean isFightRunning()
	{
		return tracker.getCurrent() != null;
	}

	boolean isFightPaused()
	{
		Fight f = tracker.getCurrent();
		return f != null && f.isPaused(System.currentTimeMillis());
	}

	private void refreshOverlay()
	{
		long now = System.currentTimeMillis();
		Fight f = config.overlayScope() == GoldenDpsConfig.Scope.SESSION
			? tracker.getSessionTotal(now)
			: tracker.getDisplayFight();
		overlayCache = f == null ? null : FightReport.of(f, config.bossOnly(), config.countRegen(), now);
		updateFun(now);

		Fight shown = tracker.getBossDisplayFight();
		long hideMs = config.bossHideSeconds() * 1000L;
		if (!config.bossMeter() || config.bossOnly() || shown == null || !shown.bossMeterVisible(now, hideMs))
		{
			bossCache = null;
			return;
		}
		long over = shown.bossDiedMs();
		if (over == 0 && !shown.isActive())
		{
			over = shown.endMs;
		}
		int hidesIn = over == 0 ? -1 : (int) Math.max(0, (over + hideMs - now + 999) / 1000);
		FightReport report = FightReport.of(shown, true, config.countRegen(), now);
		String pb = BossRecords.meterTag(storage.record(report.getTitle()), storage.compare(shown.id));
		bossCache = new BossMeter(report, shown.bossDiedMs() > 0, hidesIn, pb);
	}

	private void updateFun(long now)
	{
		Fight fight = tracker.getDisplayFight();
		Fight boss = tracker.getBossDisplayFight();
		FightReport fightReport = fight == null ? null : FightReport.of(fight, false, config.countRegen(), now);
		FightReport bossReport = boss == null || !boss.hasBossTarget()
			? null : FightReport.of(boss, true, config.countRegen(), now);
		fun.update(fightReport, fight == null ? 0 : fight.endMs, bossReport, boss == null ? 0 : boss.endMs,
			boss == null ? "" : boss.id, now);
	}

	FunEffects fun()
	{
		return fun;
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

		GoldenDpsPanel.Data data = new GoldenDpsPanel.Data();
		data.choices.add(new GoldenDpsPanel.Choice(LIVE,
			tracker.getCurrent() != null ? "Current fight (live)" : "Current / last fight"));
		data.choices.add(new GoldenDpsPanel.Choice(SESSION, "Session total"));
		for (Fight f : tracker.getHistory())
		{
			data.choices.add(new GoldenDpsPanel.Choice(f.id, GoldenDpsPanel.label(f, now)));
		}
		data.bossOnly = config.bossOnly();

		Fight fight = panelFight(p.getSelectedId(), now);
		if (fight != null)
		{
			data.report = FightReport.of(fight, config.bossOnly(), config.countRegen(), now);
			data.running = fight.isActive();
			data.paused = fight.isPaused(now);
			data.endReason = fight.endReason;
			data.fights = fight.mergedFights;
			data.startMs = fight.startMs;
			if (!config.bossOnly() && fight.hasBossTarget())
			{
				data.bossReport = FightReport.of(fight, true, config.countRegen(), now);
			}
			data.killLines = BossRecords.killLines(storage.compare(fight.id));
		}
		data.personalBests = storage.personalBests();
		data.party = party.view(localName());
		SwingUtilities.invokeLater(() -> p.update(data));
	}

	private Fight panelFight(String selected, long now)
	{
		if (SESSION.equals(selected))
		{
			return tracker.getSessionTotal(now);
		}
		if (selected == null || LIVE.equals(selected))
		{
			return tracker.getDisplayFight();
		}
		return tracker.findFight(selected);
	}

	// ---- actions from the overlay menu and the panel (Swing thread) ----

	void joinParty(String name)
	{
		clientThread.invoke(() ->
		{
			party.join(name);
			updatePanel();
		});
	}

	void joinRaidParty()
	{
		clientThread.invoke(() ->
		{
			party.joinRaidParty();
			updatePanel();
		});
	}

	void randomPartyName()
	{
		clientThread.invoke(() ->
		{
			String name = party.randomName();
			GoldenDpsPanel p = panel;
			if (name != null && p != null)
			{
				SwingUtilities.invokeLater(() -> p.setPartyName(name));
			}
		});
	}

	void leaveParty()
	{
		clientThread.invoke(() ->
		{
			party.leave();
			updatePanel();
		});
	}

	void kickFromParty(long memberId, String name)
	{
		clientThread.invoke(() ->
		{
			party.kick(memberId, name);
			updatePanel();
		});
	}

	void reportOverlayToParty()
	{
		clientThread.invoke(() ->
		{
			long now = System.currentTimeMillis();
			boolean session = config.overlayScope() == GoldenDpsConfig.Scope.SESSION;
			report(session ? tracker.getSessionTotal(now) : tracker.getDisplayFight(), session ? "Session" : null, now);
		});
	}

	void reportPanelToParty()
	{
		GoldenDpsPanel p = panel;
		String selected = p == null ? null : p.getSelectedId();
		clientThread.invoke(() ->
		{
			long now = System.currentTimeMillis();
			report(panelFight(selected, now), SESSION.equals(selected) ? "Session" : null, now);
		});
	}

	/** Sends the top players to the RuneLite party chat; plugins may not write to the game chat. */
	private void report(Fight f, String title, long now)
	{
		if (f == null)
		{
			chat.info("nothing to report yet.");
			return;
		}
		if (!party.inParty())
		{
			chat.info("join a party to report to it.");
			return;
		}
		FightReport r = FightReport.of(f, config.bossOnly(), config.countRegen(), now);
		MeterMode mode = config.overlayMode();
		for (String line : ReportFormatter.lines(r, mode, title != null ? title : r.getTitle(), ReportFormatter.DEFAULT_TOP))
		{
			partyService.send(new PartyChatMessage(line));
		}
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
		onClientThread(() ->
		{
			tracker.resetSession(System.currentTimeMillis());
			storage.save();
		});
	}

	void endFight()
	{
		onClientThread(() -> tracker.endNow(System.currentTimeMillis(), "Ended by you"));
	}

	void clearHistory()
	{
		onClientThread(() ->
		{
			tracker.clearHistory();
			storage.save();
		});
	}

	void testCrown()
	{
		clientThread.invoke(() -> fun.testCrown(localName(), System.currentTimeMillis()));
	}

	void testSnacks()
	{
		clientThread.invoke(() -> fun.testSnacks(localName(), System.currentTimeMillis()));
	}

	void requestPanelUpdate()
	{
		clientThread.invoke(this::updatePanel);
	}

	private void onClientThread(Runnable action)
	{
		clientThread.invoke(() ->
		{
			action.run();
			refreshOverlay();
			updatePanel();
		});
	}

	// ---- helpers ----

	private String localName()
	{
		Player p = client.getLocalPlayer();
		String n = p == null ? null : Names.clean(p.getName());
		return n == null ? "You" : n;
	}

	/** Damage over time; the game does not say who caused it. */
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
