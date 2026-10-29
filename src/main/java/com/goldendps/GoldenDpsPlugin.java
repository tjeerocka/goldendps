package com.goldendps;

import com.goldendps.model.AttackSource;
import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HealTable;
import com.goldendps.model.HealType;
import com.goldendps.model.HealthWatcher;
import com.goldendps.model.HistoryStore;
import com.goldendps.model.HitAttributor;
import com.goldendps.model.MeterMode;
import com.goldendps.model.PartyLeader;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.RaidParty;
import com.goldendps.model.RaidZone;
import com.goldendps.model.ReportFormatter;
import com.goldendps.model.TargetMatcher;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
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
	/** A click on Eat / Drink must be followed by the item leaving the inventory within this many ticks. */
	private static final int EAT_CONFIRM_TICKS = 3;
	private static final int WEAPON_SLOT = 3;
	/** VarPlayerID.SA_ENERGY: special attack energy x10. */
	private static final int SPEC_ENERGY_VARP = 300;
	private static final int[] SANGUINESTI_HEAL = {SpotanimID.SANGUINESTI_STAFF_HEAL, SpotanimID.SANGUINESTI_STAFF_HEAL_JUSTICIAR};
	private static final int[] BLOOD_SPELL_IMPACT = {SpotanimID.BLOOD_RUSH_IMPACT, SpotanimID.BLOOD_BLITZ_IMPACT,
		SpotanimID.SPELL_BLOOD_BURST_IMPACT, SpotanimID.SPELL_BLOOD_BARRAGE_IMPACT};

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

	@Inject
	private ChatMessageManager chatMessageManager;

	private final FightTracker tracker = new FightTracker();
	private final HealthWatcher health = new HealthWatcher();
	private final Bosses bosses = new Bosses();
	private final HitAttributor attributor = new HitAttributor();
	private final TargetMatcher matcher = new TargetMatcher();
	private final PartyLeader partyLeader = new PartyLeader();
	private final RaidParty raidParty = new RaidParty();
	/** Your member id in the current party (a new one per party), 0 = not in one. */
	private long lastPartyMemberId;
	/** Party members who send GoldenDPS data: name to time of their last message. */
	private final Map<String, Long> reporters = new HashMap<>();
	/** Your damage per NPC this tick (for Sanguinesti / blood spell heals). */
	private final Map<NPC, Integer> myDamageThisTick = new HashMap<>();
	/** Eat / Drink clicks: item id to tick, waiting for the item to leave the inventory. */
	private final Map<Integer, Integer> eatClicks = new HashMap<>();
	/** Inventory item counts before the last change. */
	private Map<Integer, Integer> lastInventory;

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
	private volatile BossMeter bossCache;
	/** Which weapon / spec / spell made your hits (for the breakdown). Client thread. */
	private final AttackSource attackSource = new AttackSource();
	private int lastSpecEnergy = -1;
	/** Knows when you are inside a raid instance (one fight per raid). */
	private final RaidZone raidZone = new RaidZone();
	/** After login: join the party you were in before (once). */
	private boolean rejoinPending = true;
	/** Personal bests of the account. Client thread. */
	private BossRecords records = new BossRecords();
	private boolean recordsLoaded;
	/** Kills that ended while the records were still loading. */
	private final List<BossRecords.Kill> pendingKills = new ArrayList<>();
	/** How each boss kill compared with the PBs (fight id → compare). */
	private final Map<String, BossRecords.Compare> killCompares = new HashMap<>();

	/** What the boss meter shows (made once per tick on the client thread). */
	static class BossMeter
	{
		final FightReport report;
		final boolean dead;
		/** Seconds until the boss meter hides, after the boss died. */
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
		store = new HistoryStore(gson, new File(RuneLite.RUNELITE_DIR, "goldendps").toPath());
		tracker.setListener(this);
		applyConfig();

		overlayManager.add(overlay);
		wsClient.registerMessage(GoldenDpsUpdate.class);
		wsClient.registerMessage(GoldenDpsPartyMessage.class);

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
			bossCache = null;
			health.reset();
			outgoing = new GoldenDpsUpdate();
		});
		wsClient.unregisterMessage(GoldenDpsUpdate.class);
		wsClient.unregisterMessage(GoldenDpsPartyMessage.class);
		overlayManager.remove(overlay);
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
				String source = attackSource.sourceForHit(client.getTickCount(), weaponName());
				tracker.playerHit(now, localName(), PlayerKind.LOCAL, key, npc.getId(), name, boss, amount, miss, source);
				tracker.seenHit(now, key, npc.getId(), name, boss, amount);
				if (!miss)
				{
					health.myHit(client.getTickCount());
					myDamageThisTick.merge(npc, amount, Integer::sum);
				}
				GoldenDpsUpdate.Hit sent = new GoldenDpsUpdate.Hit(npc.getIndex(), npc.getId(), name, amount, miss, boss, pos);
				sent.s = source;
				outgoing.h.add(sent);
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
		if (actor != null && actor == client.getLocalPlayer())
		{
			int anim = actor.getAnimation();
			if (anim != -1 && anim != EAT_ANIMATION && actor.getInteracting() instanceof NPC)
			{
				attackSource.attack(client.getTickCount(), weaponName());
			}
			return;
		}
		if (!(actor instanceof Player) || !config.estimateOthers()
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
			health.setMaxHp(e.getLevel());
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
			if (e.getItemId() > 0)
			{
				eatClicks.put(e.getItemId(), tick);
			}
		}
		else if (option.equals("Cast"))
		{
			String target = Text.removeTags(e.getMenuTarget());
			if (target != null && (target.startsWith("Heal Other") || target.startsWith("Heal Group")))
			{
				health.healOtherCast(tick);
			}
			else if (target != null && target.contains("->"))
			{
				attackSource.castClicked(tick, target);
			}
		}
	}

	/** Food / potion really eaten: the clicked item left the inventory. */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged e)
	{
		if (e.getContainerId() != InventoryID.INV)
		{
			return;
		}
		Map<Integer, Integer> now = new HashMap<>();
		for (Item item : e.getItemContainer().getItems())
		{
			if (item.getId() > 0)
			{
				now.merge(item.getId(), Math.max(1, item.getQuantity()), Integer::sum);
			}
		}
		Map<Integer, Integer> before = lastInventory;
		lastInventory = now;
		if (before == null || eatClicks.isEmpty())
		{
			return;
		}
		int tick = client.getTickCount();
		int hpLevel = client.getRealSkillLevel(Skill.HITPOINTS);
		Iterator<Map.Entry<Integer, Integer>> it = eatClicks.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Integer, Integer> click = it.next();
			if (tick - click.getValue() > EAT_CONFIRM_TICKS)
			{
				it.remove();
				continue;
			}
			int id = click.getKey();
			if (now.getOrDefault(id, 0) < before.getOrDefault(id, 0))
			{
				String name = itemName(id);
				health.foodEaten(tick, HealTable.foodHeal(name, hpLevel), HealTable.foodBoost(name, hpLevel));
				it.remove();
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

		checkLifesteal(tick);
		int spec = client.getVarpValue(SPEC_ENERGY_VARP);
		if (lastSpecEnergy >= 0 && spec < lastSpecEnergy)
		{
			attackSource.specUsed(tick, weaponName());
		}
		lastSpecEnergy = spec;
		for (HealthWatcher.Heal heal : health.endTick(tick))
		{
			if (heal.type == HealType.RECEIVED || tracker.getCurrent() == null)
			{
				continue;
			}
			tracker.heal(now, localName(), PlayerKind.LOCAL, heal.type, heal.amount, heal.overheal);
			switch (heal.type)
			{
				case FOOD:
					outgoing.food += heal.amount;
					outgoing.of += heal.overheal;
					break;
				case LIFESTEAL:
					outgoing.life += heal.amount;
					outgoing.ol += heal.overheal;
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
		rememberParty();
		raidFightTick(now);
		raidPartyTick();
		partyTick(now);
		if (++ticks % PANEL_EVERY_TICKS == 0)
		{
			updatePanel();
		}
	}

	/**
	 * Sanguinesti heal (heal graphic on the NPC you hit, or on you) and blood spells
	 * (blood impact graphic on an NPC you hit with a magic weapon): tell the health watcher
	 * how much they would heal, so wasted healing can be counted.
	 */
	private void checkLifesteal(int tick)
	{
		if (myDamageThisTick.isEmpty())
		{
			return;
		}
		Player me = client.getLocalPlayer();
		boolean sangOnMe = me != null && hasAny(me, SANGUINESTI_HEAL);
		String weapon = null;
		int bloodbark = 0;
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item[] items = worn.getItems();
			for (int i = 0; i < items.length; i++)
			{
				if (items[i].getId() <= 0)
				{
					continue;
				}
				String name = itemName(items[i].getId());
				if (i == WEAPON_SLOT)
				{
					weapon = name;
				}
				if (name != null && name.startsWith("Bloodbark"))
				{
					bloodbark++;
				}
			}
		}
		int hpLevel = client.getRealSkillLevel(Skill.HITPOINTS);
		int sang = 0;
		int blood = 0;
		for (Map.Entry<NPC, Integer> e : myDamageThisTick.entrySet())
		{
			NPC npc = e.getKey();
			if (sangOnMe || hasAny(npc, SANGUINESTI_HEAL))
			{
				sang += HealTable.sanguinesti(e.getValue());
			}
			else if (HealTable.isMagicWeapon(weapon) && hasAny(npc, BLOOD_SPELL_IMPACT))
			{
				blood += HealTable.bloodSpell(e.getValue(), bloodbark, weapon);
			}
		}
		myDamageThisTick.clear();
		health.lifesteal(tick, sang, 0);
		health.lifesteal(tick, blood, HealTable.bloodBoost(weapon, hpLevel));
	}

	private static boolean hasAny(Actor actor, int[] spotAnims)
	{
		for (int id : spotAnims)
		{
			if (actor.hasSpotAnim(id))
			{
				return true;
			}
		}
		return false;
	}

	/** Name of the weapon you hold now, or null. Client thread. */
	private String weaponName()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn == null)
		{
			return null;
		}
		Item[] items = worn.getItems();
		return items.length > WEAPON_SLOT && items[WEAPON_SLOT].getId() > 0 ? itemName(items[WEAPON_SLOT].getId()) : null;
	}

	private String itemName(int itemId)
	{
		ItemComposition def = client.getItemDefinition(itemId);
		return def == null ? null : def.getName();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			checkAccount();
			rejoinPending = true;
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			health.reset();
			attributor.clear();
			matcher.clear();
			myDamageThisTick.clear();
			eatClicks.clear();
			lastInventory = null;
			attackSource.reset();
			raidZone.reset();
			tracker.endRaid(System.currentTimeMillis(), state == GameState.HOPPING ? "World hop" : "Logged out");
			lastSpecEnergy = -1;
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
		partyLeader.sawPlugin(u.getMemberId());
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
					boss, hit.a, hit.miss, hit.s == null || hit.s.length() > 64 ? null : hit.s);
			}
		}
		tracker.damageTaken(now, name, PlayerKind.PARTY, u.taken);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.FOOD, u.food, u.of);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.LIFESTEAL, u.life, u.ol);
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
		BossRecords.Kill k = BossRecords.kill(fight, config.countRegen());
		if (k == null || account == -1)
		{
			return;
		}
		if (!recordsLoaded)
		{
			pendingKills.add(k);
			return;
		}
		killCompares.put(fight.id, records.add(k));
		saveRecordsAsync();
	}

	private void saveRecordsAsync()
	{
		if (account == -1 || store == null || !recordsLoaded)
		{
			return;
		}
		final long acc = account;
		final BossRecords copy = records.copy();
		executor.execute(() ->
		{
			try
			{
				store.saveRecords(acc, copy);
			}
			catch (Exception ex)
			{
				log.warn("GoldenDPS: could not save personal bests", ex);
			}
		});
	}

	/** The personal bests, for the panel. Client thread. */
	List<BossRecords.Record> personalBests()
	{
		return records.list();
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
		records = new BossRecords();
		recordsLoaded = false;
		pendingKills.clear();
		killCompares.clear();
		executor.execute(() ->
		{
			try
			{
				List<Fight> loaded = store.load(hash);
				BossRecords savedRecords = store.loadRecords(hash);
				clientThread.invokeLater(() ->
				{
					if (account != hash)
					{
						return;
					}
					// personal bests: saved ones, or (first time) built from the saved fights
					BossRecords rec = savedRecords;
					if (rec == null)
					{
						rec = new BossRecords();
						rec.seed(loaded, config.countRegen());
					}
					records = rec;
					recordsLoaded = true;
					List<Fight> ended = tracker.getHistory();
					for (int i = 0; i < pendingKills.size(); i++)
					{
						BossRecords.Kill k = pendingKills.get(i);
						BossRecords.Compare c = records.add(k);
						for (Fight f : ended)
						{
							if (f.bossDiedMs() == k.whenMs)
							{
								killCompares.put(f.id, c);
							}
						}
					}
					pendingKills.clear();
					saveRecordsAsync();
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

		// boss meter: always the current / last fight, only boss damage and boss time
		Fight shown = tracker.getBossDisplayFight();
		long hideMs = config.bossHideSeconds() * 1000L;
		if (config.bossMeter() && !config.bossOnly() && shown != null && shown.bossMeterVisible(now, hideMs))
		{
			long over = shown.bossDiedMs();
			if (over == 0 && !shown.isActive())
			{
				over = shown.endMs;
			}
			int hidesIn = over == 0 ? -1 : (int) Math.max(0, (over + hideMs - now + 999) / 1000);
			FightReport br = FightReport.of(shown, true, config.countRegen(), now);
			String pb = BossRecords.meterTag(records.get(br.getTitle()), killCompares.get(shown.id));
			bossCache = new BossMeter(br, shown.bossDiedMs() > 0, hidesIn, pb);
		}
		else
		{
			bossCache = null;
		}
	}

	/** Boss meter for the overlay, or null when it is hidden. */
	BossMeter bossMeter()
	{
		return bossCache;
	}

	boolean isFightRunning()
	{
		return tracker.getCurrent() != null;
	}

	/** Fight runs, but nobody hit for a while: the clock is stopped (AFK). */
	boolean isFightPaused()
	{
		Fight f = tracker.getCurrent();
		return f != null && f.isPaused(System.currentTimeMillis());
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

		Fight fight = panelFight(selected, now);

		GoldenDpsPanel.Data data = new GoldenDpsPanel.Data();
		data.choices = choices;
		data.bossOnly = config.bossOnly();
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
			data.killLines = BossRecords.killLines(killCompares.get(fight.id));
		}
		data.localName = localName();
		data.personalBests = records.list();
		data.party = partyView();
		SwingUtilities.invokeLater(() -> p.update(data));
	}

	/** The fight picked in the panel's list. Client thread. */
	private Fight panelFight(String selected, long now)
	{
		if (SESSION.equals(selected))
		{
			return tracker.getSessionTotal(now);
		}
		if (LIVE.equals(selected) || selected == null)
		{
			return tracker.getDisplayFight();
		}
		return tracker.findFight(selected);
	}

	// ---- party (the same RuneLite party as the Party plugin, plus a GoldenDPS leader and kick) ----

	/** Join (or start) the party with this name (Swing thread). Spaces are fine. */
	void joinParty(String name)
	{
		clientThread.invoke(() ->
		{
			if (partyService.isInParty() && name.equals(partyService.getPartyPassphrase()))
			{
				return;
			}
			partyService.changeParty(name);
			partyLeader.left();
			localMessage("GoldenDPS: joined party \"" + name + "\".");
			updatePanel();
		});
	}

	/** Fill the party name box with random words (the user then presses Join). */
	void randomPartyName()
	{
		clientThread.invoke(() ->
		{
			String name = partyService.generatePassphrase().replace('-', ' ');
			GoldenDpsPanel p = panel;
			if (p != null)
			{
				SwingUtilities.invokeLater(() -> p.setPartyName(name));
			}
		});
	}

	void leaveParty()
	{
		clientThread.invoke(() ->
		{
			if (partyService.isInParty())
			{
				partyService.changeParty(null);
				partyLeader.left();
				localMessage("GoldenDPS: left the party.");
			}
			updatePanel();
		});
	}

	/** Leader only: ask this member's GoldenDPS to leave the party. */
	void kickFromParty(long memberId, String name)
	{
		clientThread.invoke(() ->
		{
			if (!partyLeader.isLeader() || !partyService.isInParty())
			{
				localMessage("GoldenDPS: only the party leader can kick.");
				return;
			}
			partyService.send(new GoldenDpsPartyMessage(GoldenDpsPartyMessage.KICK, partyLeader.getLeader(), memberId));
			localMessage("GoldenDPS: removed " + name + " from the party.");
		});
	}

	@Subscribe
	public void onGoldenDpsPartyMessage(GoldenDpsPartyMessage m)
	{
		// websocket thread: move to the client thread
		clientThread.invokeLater(() -> handlePartyMessage(m));
	}

	private void handlePartyMessage(GoldenDpsPartyMessage m)
	{
		PartyMember local = partyService.getLocalMember();
		if (m.type == null || local == null || m.getMemberId() == local.getMemberId())
		{
			return;
		}
		switch (m.type)
		{
			case GoldenDpsPartyMessage.HI:
				partyLeader.announce(m.getMemberId(), m.leader);
				if (partyLeader.isLeader())
				{
					// tell the new GoldenDPS member who leads
					sendPartyControl(GoldenDpsPartyMessage.LEADER, 0);
				}
				break;
			case GoldenDpsPartyMessage.LEADER:
				partyLeader.announce(m.getMemberId(), m.leader);
				break;
			case GoldenDpsPartyMessage.KICK:
				partyLeader.sawPlugin(m.getMemberId());
				if (partyLeader.acceptKick(m.getMemberId(), m.target))
				{
					PartyMember by = partyService.getMemberById(m.getMemberId());
					partyService.changeParty(null);
					partyLeader.left();
					localMessage("GoldenDPS: you were removed from the party"
						+ (by != null ? " by " + cleanName(by.getDisplayName()) : "") + ".");
					updatePanel();
				}
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onUserPart(UserPart e)
	{
		clientThread.invokeLater(() -> partyLeader.memberLeft(e.getMemberId(), partyMemberIds()));
	}

	private void sendPartyControl(String type, long target)
	{
		if (partyService.isInParty() && partyService.getLocalMember() != null)
		{
			partyService.send(new GoldenDpsPartyMessage(type, partyLeader.getLeader(), target));
		}
	}

	private List<Long> partyMemberIds()
	{
		List<Long> ids = new ArrayList<>();
		for (PartyMember m : partyService.getMembers())
		{
			ids.add(m.getMemberId());
		}
		return ids;
	}

	/**
	 * The raids that show their members' names to every member: raid name, party status varbit
	 * (0 = no raid party) and the first and last name string. Same numbers as RuneLite's gameval
	 * TOB_ / TOA_ / TFA_CLIENT_PARTYSTATUS and _CLIENT_NAME0..
	 */
	private static final Object[][] RAIDS = {
		{"ToB", 6440, 330, 334},
		{"ToA", 14345, 1099, 1106},
		{"Archive", 16386, 1573, 1580},
	};

	/** Every game tick: auto party in raids (see RaidParty). */
	/** One fight per raid: start it when you enter the raid instance, end it when you leave. */
	private void raidFightTick(long now)
	{
		String raidNow = null;
		Player me = client.getLocalPlayer();
		if (config.raidFight() && me != null && me.getLocalLocation() != null)
		{
			WorldPoint p = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
			WorldView wv = client.getTopLevelWorldView();
			if (p != null && wv != null)
			{
				raidNow = RaidZone.raidFor(p.getRegionID(), wv.isInstance());
			}
		}
		RaidZone.Change c = raidZone.tick(raidNow);
		if (c.start != null)
		{
			tracker.startRaid(now, c.start);
			localMessage("GoldenDPS: " + c.start + " started. The meter counts the whole raid until you leave.");
			refreshOverlay();
		}
		else if (c.end)
		{
			tracker.endRaid(now, "Left the raid");
			refreshOverlay();
		}
	}

	private void raidPartyTick()
	{
		if (!config.autoRaidParty())
		{
			return;
		}
		String name = null;
		String raid = null;
		for (Object[] r : RAIDS)
		{
			if (client.getVarbitValue((int) r[1]) > 0)
			{
				List<String> names = new ArrayList<>();
				for (int v = (int) r[2]; v <= (int) r[3]; v++)
				{
					names.add(client.getVarcStrValue(v));
				}
				raid = (String) r[0];
				name = RaidParty.partyName(raid, client.getWorld(), names);
				break;
			}
		}
		String current = partyService.isInParty() ? partyService.getPartyPassphrase() : null;
		RaidParty.Action a = raidParty.tick(name, current);
		if (a.join != null)
		{
			partyService.changeParty(a.join);
			partyLeader.left();
			localMessage("GoldenDPS: joined the " + raid + " party automatically (everyone in your raid with GoldenDPS).");
		}
	}

	/**
	 * Stay in your party: remember it, and join it again after a relog or a client restart.
	 * Only leaving yourself (while logged in) forgets it.
	 */
	private void rememberParty()
	{
		String saved = configManager.getConfiguration(GoldenDpsConfig.GROUP, GoldenDpsConfig.LAST_PARTY_KEY);
		String current = partyService.isInParty() ? partyService.getPartyPassphrase() : null;
		if (rejoinPending)
		{
			rejoinPending = false;
			if (current == null && saved != null && !saved.isEmpty())
			{
				partyService.changeParty(saved);
				partyLeader.left();
				localMessage("GoldenDPS: back in your party \"" + saved + "\".");
				return;
			}
		}
		if (current != null && !current.equals(saved))
		{
			configManager.setConfiguration(GoldenDpsConfig.GROUP, GoldenDpsConfig.LAST_PARTY_KEY, current);
		}
		else if (current == null && saved != null)
		{
			// you left while logged in (our Leave button, the Party plugin or a kick)
			configManager.unsetConfiguration(GoldenDpsConfig.GROUP, GoldenDpsConfig.LAST_PARTY_KEY);
		}
	}

	/** Every game tick: notice joining / leaving, and work out the leader. */
	private void partyTick(long now)
	{
		PartyMember local = partyService.isInParty() ? partyService.getLocalMember() : null;
		if (local == null)
		{
			if (partyLeader.isJoined())
			{
				partyLeader.left();
			}
			lastPartyMemberId = 0;
			return;
		}
		if (local.getMemberId() != lastPartyMemberId)
		{
			// a new party (RuneLite gives you a new member id per party)
			lastPartyMemberId = local.getMemberId();
			partyLeader.joined(local.getMemberId(), now);
			sendPartyControl(GoldenDpsPartyMessage.HI, 0);
		}
		if (partyLeader.tick(now, partyMemberIds()) && partyLeader.isLeader())
		{
			sendPartyControl(GoldenDpsPartyMessage.LEADER, 0);
		}
	}

	/** The party card for the panel. Client thread. */
	private GoldenDpsPartyPanel.View partyView()
	{
		GoldenDpsPartyPanel.View v = new GoldenDpsPartyPanel.View();
		v.inParty = partyService.isInParty();
		if (!v.inParty)
		{
			return v;
		}
		v.code = partyService.getPartyPassphrase();
		v.youLead = partyLeader.isLeader();
		v.auto = RaidParty.isAuto(v.code);
		PartyMember local = partyService.getLocalMember();
		for (PartyMember m : partyService.getMembers())
		{
			GoldenDpsPartyPanel.Member row = new GoldenDpsPartyPanel.Member();
			row.id = m.getMemberId();
			row.you = local != null && m.getMemberId() == local.getMemberId();
			String name = cleanName(m.getDisplayName());
			row.name = name != null ? name : row.you ? localName() : "(joining...)";
			row.online = m.isLoggedIn();
			row.leader = m.getMemberId() == partyLeader.getLeader();
			row.hasPlugin = row.you || partyLeader.hasPlugin(m.getMemberId())
				|| (name != null && isReporter(name, System.currentTimeMillis()));
			v.members.add(row);
		}
		// leader first, then you, then by name
		v.members.sort((a, b) -> a.leader != b.leader ? (a.leader ? -1 : 1)
			: a.you != b.you ? (a.you ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
		return v;
	}

	/** Overlay right-click "Report to party": what the overlay shows. */
	void reportOverlayToParty()
	{
		clientThread.invoke(() ->
		{
			long now = System.currentTimeMillis();
			boolean session = config.overlayScope() == GoldenDpsConfig.Scope.SESSION;
			Fight f = session ? tracker.getSessionTotal(now) : tracker.getDisplayFight();
			report(f, session ? "Session" : null, now);
		});
	}

	/** Panel button "Report to party": the fight picked in the panel. */
	void reportPanelToParty()
	{
		GoldenDpsPanel p = panel;
		final String selected = p == null ? null : p.getSelectedId();
		clientThread.invoke(() ->
		{
			long now = System.currentTimeMillis();
			report(panelFight(selected, now), SESSION.equals(selected) ? "Session" : null, now);
		});
	}

	/**
	 * Send the top 5 to the RuneLite party chat (like Details! "Report"). OSRS does not allow plugins
	 * to send messages in the game chat, so this only goes to the RuneLite party. Client thread.
	 */
	private void report(Fight f, String title, long now)
	{
		if (f == null)
		{
			localMessage("GoldenDPS: nothing to report yet.");
			return;
		}
		if (!partyService.isInParty() || partyService.getLocalMember() == null)
		{
			localMessage("GoldenDPS: join a RuneLite party (Party plugin) to report to your party.");
			return;
		}
		FightReport r = FightReport.of(f, config.bossOnly(), config.countRegen(), now);
		MeterMode mode = config.overlayMode();
		String name = title != null ? title : r.getTitle();
		for (String line : ReportFormatter.lines(r, mode, name, ReportFormatter.DEFAULT_TOP))
		{
			partyService.send(new PartyChatMessage(line));
		}
	}

	private void localMessage(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(text)
			.build());
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
