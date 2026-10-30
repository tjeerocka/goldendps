package com.goldendps;

import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HealType;
import com.goldendps.model.HealthWatcher;
import com.goldendps.model.HistoryStore;
import com.goldendps.model.KillCounts;
import com.goldendps.model.MeterMode;
import com.goldendps.model.PlayerKind;
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
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.OverheadTextChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.party.events.UserPart;
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
	private static final int PANEL_EVERY_TICKS = 2;
	private static final String PANEL_TOGGLES_KEY = "panelToggles";

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

	@Inject
	private BarrowsTracker barrows;

	private final FightTracker tracker = new FightTracker();
	private final Bosses bosses = new Bosses();
	private final TargetMatcher matcher = new TargetMatcher();
	private final FunEffects fun = new FunEffects();
	/** Kept over plugin restarts, like the game chat it is read from. */
	private final KillCounts killCounts = new KillCounts();

	// made in startUp, when the injected fields are set
	private FightStorage storage;
	private PartyUpdates partyUpdates;
	private CombatEvents combat;
	private RaidFights raids;
	private PartyReports reports;
	private MeterViews views;

	private GoldenDpsPanel panel;
	private NavigationButton navButton;
	private int ticks;
	private volatile FightReport overlayCache;
	private volatile BossMeter bossCache;
	/** Worked out on the client thread: the overlay must not read the fight history. */
	private volatile boolean overallGoingOn;
	private volatile boolean overallPaused;

	@Provides
	GoldenDpsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GoldenDpsConfig.class);
	}

	@Override
	protected void startUp()
	{
		createParts();
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

	private void createParts()
	{
		HistoryStore store = new HistoryStore(gson, new File(RuneLite.RUNELITE_DIR, "goldendps").toPath());
		storage = new FightStorage(tracker, store, executor, clientThread, () ->
		{
			refreshOverlay();
			updatePanel();
		});
		partyUpdates = new PartyUpdates(client, partyService, party, config, tracker, bosses, matcher);
		combat = new CombatEvents(client, partyService, config, tracker, bosses, matcher, healing, barrows, party,
			partyUpdates);
		raids = new RaidFights(client, config, chat, tracker);
		reports = new PartyReports(config, partyService, party, chat, storage, killCounts);
		views = new MeterViews(config, tracker, storage, barrows, party, fun);
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
			reports.clear();
			healing.reset();
			partyUpdates.clear();
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
		combat.hitsplatApplied(e.getActor(), e.getHitsplat());
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged e)
	{
		combat.animationChanged(e.getActor());
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
				combat.spellCastClicked(tick, target);
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
		if (wv == null || !fun.anyone())
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
			barrows.died(System.currentTimeMillis(), npc.getName());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage e)
	{
		if (e.getType() == ChatMessageType.GAMEMESSAGE)
		{
			long now = System.currentTimeMillis();
			killCounts.message(now, e.getMessage());
			barrows.gameMessage(now, e.getMessage(), tracker);
		}
	}

	@Subscribe
	public void onGameTick(GameTick e)
	{
		long now = System.currentTimeMillis();
		int tick = client.getTickCount();

		combat.tick(tick);

		List<HealthWatcher.Heal> heals = healing.endTick(tick);
		if (tracker.getCurrent() != null)
		{
			heals.forEach(heal -> addOwnHeal(now, heal));
		}

		tracker.tick(now);
		barrows.tick(now, tick);
		partyUpdates.send();
		reports.sendDue(tick);
		storage.tick(now);
		raids.tick(now);
		party.tick(now, raids.isInsideRaid());
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
		partyUpdates.addHeal(heal);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState state = e.getGameState();
		if (state == GameState.LOGGED_IN)
		{
			storage.loadAccount(client.getAccountHash(), config.countRegen());
			barrows.loggedIn();
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			long now = System.currentTimeMillis();
			String reason = state == GameState.HOPPING ? "World hop" : "Logged out";
			healing.reset();
			combat.reset();
			matcher.clear();
			raids.reset();
			barrows.loggedOut(now);
			party.loggedOut(state == GameState.LOGIN_SCREEN);
			partyUpdates.clear();
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
			long now = System.currentTimeMillis();
			barrows.clientClosed(now);
			tracker.endNow(now, "Client closed");
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
		reports.fightEnded(fight, client.getTickCount());
	}

	// ---- party ----

	@Subscribe
	public void onGoldenDpsUpdate(GoldenDpsUpdate update)
	{
		clientThread.invokeLater(() -> partyUpdates.receive(update));
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
		return overallGoingOn;
	}

	boolean isFightPaused()
	{
		return overallPaused;
	}

	String barrowsRunTime()
	{
		return barrows.liveTime();
	}

	boolean isBarrowsRunPaused()
	{
		return barrows.isPaused();
	}

	/** The overlays draw on their own thread, so what they show is built here on the client thread. */
	private void refreshOverlay()
	{
		long now = System.currentTimeMillis();
		overlayCache = views.overlayReport(now);
		overallGoingOn = tracker.isOverallGoingOn(now);
		// between two fights of the overall meter, or no hits for a while in the running fight
		Fight running = tracker.getCurrent();
		overallPaused = running != null ? running.isPaused(now) : overallGoingOn;
		views.updateFun(now);
		bossCache = views.bossMeter(now);
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
		GoldenDpsPanel.Data data = views.panelData(p.getSelectedId(), localName(), System.currentTimeMillis());
		SwingUtilities.invokeLater(() -> p.update(data));
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

	/** Reports what the overlay shows: the fight (or session), or the boss of the boss meter. */
	void reportOverlayToParty(ReportKind kind)
	{
		clientThread.invoke(() ->
		{
			long now = System.currentTimeMillis();
			reports.send(views.overlayFight(kind, now), kind, views.isSessionOverlay(), now);
		});
	}

	/** Reports the fight picked in the panel. */
	void reportPanelToParty(ReportKind kind)
	{
		GoldenDpsPanel p = panel;
		String selected = p == null ? null : p.getSelectedId();
		clientThread.invoke(() ->
		{
			long now = System.currentTimeMillis();
			reports.send(views.panelFight(selected, now), kind, GoldenDpsPanel.SESSION_CHOICE.equals(selected), now);
		});
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
		runAndRefresh(() ->
		{
			tracker.resetSession(System.currentTimeMillis());
			storage.save();
		});
	}

	void endFight()
	{
		runAndRefresh(() -> tracker.endNow(System.currentTimeMillis(), "Ended by you"));
	}

	void resetPersonalBest(String boss, boolean party)
	{
		runAndRefresh(() -> storage.resetRecord(boss, party));
	}

	void clearHistory()
	{
		runAndRefresh(() ->
		{
			tracker.clearHistory();
			storage.save();
		});
	}

	/** The panel sections that are shown or hidden against their default, comma separated. */
	String panelToggles()
	{
		String toggles = configManager.getConfiguration(GoldenDpsConfig.STATE_GROUP, PANEL_TOGGLES_KEY);
		return toggles == null ? "" : toggles;
	}

	void savePanelToggles(String toggles)
	{
		configManager.setConfiguration(GoldenDpsConfig.STATE_GROUP, PANEL_TOGGLES_KEY, toggles);
	}

	void requestPanelUpdate()
	{
		clientThread.invoke(this::updatePanel);
	}

	/** Runs a change to the fights on the client thread and shows the result. */
	private void runAndRefresh(Runnable action)
	{
		clientThread.invoke(() ->
		{
			action.run();
			refreshOverlay();
			updatePanel();
		});
	}

	private String localName()
	{
		return Names.localName(client);
	}
}
