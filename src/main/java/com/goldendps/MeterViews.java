package com.goldendps;

import com.goldendps.model.BossRecords;
import com.goldendps.model.DefenceDrains;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.Format;
import com.goldendps.model.KillRates;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Turns the tracked fights into what the overlays and the panel show. Client thread only. */
class MeterViews
{
	private final GoldenDpsConfig config;
	private final FightTracker tracker;
	private final FightStorage storage;
	private final BossHealth bossHealth;
	private final BarrowsTracker barrows;
	private final PartyManager party;
	private final FunEffects fun;
	private final DefenceDrains defence;

	MeterViews(GoldenDpsConfig config, FightTracker tracker, FightStorage storage, BossHealth bossHealth,
		BarrowsTracker barrows, PartyManager party, FunEffects fun, DefenceDrains defence)
	{
		this.config = config;
		this.tracker = tracker;
		this.storage = storage;
		this.bossHealth = bossHealth;
		this.barrows = barrows;
		this.party = party;
		this.fun = fun;
		this.defence = defence;
	}

	/**
	 * Target key to the lines above that NPC: "Def 120 / 246  Hit chance 78%" and "DWH x2 -126, BGS -34".
	 * @param showDefence false shows only the hit chance
	 * @param hit your hit chance on the monster you attack, or null
	 */
	Map<String, String[]> defenceLabels(boolean showDefence, TargetHitChance.Result hit)
	{
		Map<String, String[]> labels = new HashMap<>();
		if (showDefence)
		{
			defence.all().forEach((key, t) -> labels.put(key, new String[]{levels(t), t.summary()}));
		}
		if (hit != null)
		{
			String[] lines = labels.get(hit.targetKey);
			String chance = (hit.spec ? "Spec hit chance " : "Hit chance ") + percent(hit.now);
			labels.put(hit.targetKey, lines == null ? new String[]{chance} : new String[]{lines[0] + "  " + chance, lines[1]});
		}
		return labels;
	}

	/** "Def 120 / 246", plus "  Magic 120 / 150" when the magic level is lowered too. */
	private static String levels(DefenceDrains.Target t)
	{
		String magic = t.getMagicCurrent() < t.magicBase ? "  Magic " + t.getMagicCurrent() + " / " + t.magicBase : "";
		return "Def " + t.getCurrent() + " / " + t.base + magic;
	}

	private static String percent(double chance)
	{
		return Math.round(chance * 100) + "%";
	}

	/** {label, value} lines for the panel: each monster with lowered defence, then each special attack. */
	private List<String[]> defenceLines(String localName, TargetHitChance.Result hit)
	{
		List<String[]> lines = new ArrayList<>();
		if (hit != null)
		{
			String now = percent(hit.now);
			String before = percent(hit.before);
			String value = now.equals(before) ? now : now + " (was " + before + ")";
			lines.add(new String[]{(hit.spec ? "Spec hit chance: " : "Hit chance: ") + hit.name, value});
		}
		for (DefenceDrains.Target t : defence.all().values())
		{
			lines.add(new String[]{t.name, levels(t)});
			for (DefenceDrains.Drain d : t.getDrains())
			{
				String who = Objects.equals(d.player, localName) ? "you" : d.player;
				lines.add(new String[]{"+ " + DefenceDrains.shortName(d.weapon) + " (" + who + ")",
					"-" + d.amount + (d.magic ? " magic" : "")});
			}
		}
		return lines;
	}

	/** The main overlay meter: the overall fights or the session; null when there is none. */
	FightReport overlayReport(long now)
	{
		Fight fight = isSessionOverlay() ? tracker.getSessionTotal(now) : tracker.getOverall(now);
		return fight == null ? null : FightReport.of(fight, config.bossOnly(), now);
	}

	/** The second overlay meter for the current boss, or null when it is turned off or has nothing to show. */
	BossMeter bossMeter(long now)
	{
		Fight shown = tracker.getBossDisplayFight();
		if (!config.bossMeter() || config.bossOnly() || shown == null || !shown.hasBossMeter())
		{
			return null;
		}
		FightReport report = FightReport.of(shown, true, now);
		BossRecords.Record record = storage.record(report.getTitle(), BossRecords.isParty(report));
		String pb = BossRecords.meterTag(record, storage.compare(shown.id));
		boolean dead = shown.bossDiedMs() > 0;
		// a timed boss's health bar is its clock, not your damage: no pace there
		Long pace = dead || BossRecords.isTimed(report.getTitle()) ? null
			: BossRecords.paceMs(report.getDurationMs(), bossHealth.done(shown), record);
		return new BossMeter(report, dead, !dead && shown.isPaused(now), pb, pace);
	}

	/** Gives the crown and the snacks to the players of the shown fight and boss. */
	void updateFun(long now)
	{
		Fight fight = tracker.getDisplayFight();
		Fight boss = tracker.getBossDisplayFight();
		FightReport fightReport = fight == null ? null : FightReport.of(fight, false, now);
		FightReport bossReport = boss == null || !boss.hasBossTarget()
			? null : FightReport.of(boss, true, now);
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

	/** @param hit your hit chance on the monster you attack, or null */
	GoldenDpsPanel.Data panelData(String selectedId, String localName, long now, TargetHitChance.Result hit)
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
			data.report = FightReport.of(fight, config.bossOnly(), now);
			data.running = fight.isActive();
			data.paused = fight.isPaused(now);
			data.endReason = fight.endReason;
			data.fights = fight.mergedFights;
			data.startMs = fight.startMs;
			if (!config.bossOnly() && fight.hasBossTarget())
			{
				data.bossReport = FightReport.of(fight, true, now);
			}
			data.killLines = BossRecords.killLines(storage.compare(fight.id));
			data.bossTimes = bossTimes(fight, now);
		}
		data.monsters = storage.monsterEntries();
		data.killRates = KillRates.of(sessionFights(), now);
		data.barrowsRunTime = barrows.liveTime();
		data.barrowsRunPaused = barrows.isPaused();
		data.party = party.view(localName);
		data.defence = defenceLines(localName, hit);
		return data;
	}

	/** Raid splits: every boss of a raid fight with its time and PB. */
	private List<String[]> bossTimes(Fight raidFight, long now)
	{
		List<String[]> out = new ArrayList<>();
		if (raidFight.raid == null || raidFight.partOf != null || raidFight.run)
		{
			return out;
		}
		for (Fight part : tracker.partsOf(raidFight))
		{
			FightReport report = FightReport.of(part, true, now);
			BossRecords.Compare kill = storage.compare(part.id);
			String tag;
			if (kill != null)
			{
				tag = kill.newFastest && kill.before != null ? "NEW PB!"
					: kill.before != null ? "PB " + Format.preciseDuration(kill.before.fastestMs) : "";
			}
			else
			{
				BossRecords.Record pb = storage.record(report.getTitle(), BossRecords.isParty(report));
				tag = pb != null ? "PB " + Format.preciseDuration(pb.fastestMs) : "";
			}
			out.add(new String[]{report.getTitle(), Format.preciseDuration(report.getDurationMs()), tag});
		}
		return out;
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
