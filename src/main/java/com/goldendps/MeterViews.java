package com.goldendps;

import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.KillRates;
import java.util.List;

/** Turns the tracked fights into what the overlays and the panel show. Client thread only. */
class MeterViews
{
	private final GoldenDpsConfig config;
	private final FightTracker tracker;
	private final FightStorage storage;
	private final BarrowsTracker barrows;
	private final PartyManager party;
	private final FunEffects fun;

	MeterViews(GoldenDpsConfig config, FightTracker tracker, FightStorage storage, BarrowsTracker barrows,
		PartyManager party, FunEffects fun)
	{
		this.config = config;
		this.tracker = tracker;
		this.storage = storage;
		this.barrows = barrows;
		this.party = party;
		this.fun = fun;
	}

	/** The main overlay meter: the overall fights or the session; null when there is none. */
	FightReport overlayReport(long now)
	{
		Fight fight = isSessionOverlay() ? tracker.getSessionTotal(now) : tracker.getOverall(now);
		return fight == null ? null : FightReport.of(fight, config.bossOnly(), config.countRegen(), now);
	}

	/** The second overlay meter for the current boss, or null when it is turned off or has nothing to show. */
	BossMeter bossMeter(long now)
	{
		Fight shown = tracker.getBossDisplayFight();
		if (!config.bossMeter() || config.bossOnly() || shown == null || !shown.hasBossMeter())
		{
			return null;
		}
		FightReport report = FightReport.of(shown, true, config.countRegen(), now);
		String pb = BossRecords.meterTag(storage.record(report.getTitle(), BossRecords.isParty(report)),
			storage.compare(shown.id));
		boolean dead = shown.bossDiedMs() > 0;
		return new BossMeter(report, dead, !dead && shown.isPaused(now), pb);
	}

	/** Gives the crown and the snacks to the players of the shown fight and boss. */
	void updateFun(long now)
	{
		Fight fight = tracker.getDisplayFight();
		Fight boss = tracker.getBossDisplayFight();
		FightReport fightReport = fight == null ? null : FightReport.of(fight, false, config.countRegen(), now);
		FightReport bossReport = boss == null || !boss.hasBossTarget()
			? null : FightReport.of(boss, true, config.countRegen(), now);
		fun.update(fightReport, fight == null ? 0 : fight.endMs, bossReport, boss == null ? 0 : boss.endMs,
			boss == null ? "" : boss.id, now);
	}

	/** The fight shown in the overlay, for a report of this kind. */
	Fight overlayFight(ReportKind kind, long now)
	{
		if (isSessionOverlay())
		{
			return tracker.getSessionTotal(now);
		}
		return kind == ReportKind.BOSS ? tracker.getBossDisplayFight() : tracker.getOverall(now);
	}

	boolean isSessionOverlay()
	{
		return config.overlayScope() == GoldenDpsConfig.Scope.SESSION;
	}

	/** The fight for a choice of the panel's fight list, or null. */
	Fight panelFight(String choiceId, long now)
	{
		if (GoldenDpsPanel.SESSION_CHOICE.equals(choiceId))
		{
			return tracker.getSessionTotal(now);
		}
		if (choiceId == null || GoldenDpsPanel.LIVE_CHOICE.equals(choiceId))
		{
			return tracker.getDisplayFight();
		}
		return tracker.findFight(choiceId);
	}

	GoldenDpsPanel.Data panelData(String selectedId, String localName, long now)
	{
		GoldenDpsPanel.Data data = new GoldenDpsPanel.Data();
		data.choices.add(new GoldenDpsPanel.Choice(GoldenDpsPanel.LIVE_CHOICE,
			tracker.getCurrent() != null ? "Current fight (live)" : "Current / last fight"));
		data.choices.add(new GoldenDpsPanel.Choice(GoldenDpsPanel.SESSION_CHOICE, "Session total"));
		for (Fight f : tracker.getHistory())
		{
			data.choices.add(new GoldenDpsPanel.Choice(f.id, GoldenDpsPanel.label(f, now)));
		}
		data.bossOnly = config.bossOnly();

		Fight fight = panelFight(selectedId, now);
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
		data.killRates = KillRates.of(sessionFights(), config.countRegen(), now);
		data.barrowsRunTime = barrows.liveTime();
		data.barrowsRunPaused = barrows.isPaused();
		data.party = party.view(localName);
		return data;
	}

	/** The ended fights of this session and the running one. */
	private List<Fight> sessionFights()
	{
		List<Fight> fights = tracker.getSessionFights();
		if (tracker.getCurrent() != null)
		{
			fights.add(0, tracker.getCurrent());
		}
		return fights;
	}
}
