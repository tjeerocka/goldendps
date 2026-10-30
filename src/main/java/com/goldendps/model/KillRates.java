package com.goldendps.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kills per hour in one session: every monster, every boss, and completions of every raid.
 * A rate runs from the start of the first kill until now, so it drops while you do something else.
 */
public final class KillRates
{
	public enum Kind
	{
		RAID,
		/** Barrows chests. */
		RUN,
		BOSS,
		MONSTER
	}

	/** One raid, boss or monster. */
	public static final class Rate
	{
		public final String name;
		public final Kind kind;
		public int kills;
		/** Kills per hour. */
		public double perHour;
		/** Average kill (or raid) time. */
		public long averageMs;
		long firstStartMs = Long.MAX_VALUE;
		long totalMs;

		Rate(String name, Kind kind)
		{
			this.name = name;
			this.kind = kind;
		}
	}

	private KillRates()
	{
	}

	/** Rates for the fights of one session: raids first, then bosses, then monsters, most kills first. */
	public static List<Rate> of(List<Fight> fights, boolean includeRegen, long nowMs)
	{
		Map<String, Rate> rates = new LinkedHashMap<>();
		for (Fight f : fights)
		{
			if (f.mergedFights > 0)
			{
				continue;
			}
			if (f.isCompleteRaid())
			{
				add(rates, f.raid, f.run ? Kind.RUN : Kind.RAID, f.startMs, f.durationMs(f.endMs));
			}
			BossRecords.Kill kill = BossRecords.kill(f, includeRegen);
			// Barrows brothers die once per chest: chests per hour says it
			if (kill != null && !BarrowsRun.isBrother(kill.boss))
			{
				add(rates, kill.boss, Kind.BOSS, f.startMs, kill.timeMs);
			}
			if (f.partOf == null && !f.run)
			{
				addMonsters(rates, f);
			}
		}
		List<Rate> out = new ArrayList<>(rates.values());
		for (Rate r : out)
		{
			r.averageMs = r.totalMs / r.kills;
			r.perHour = r.kills * 3_600_000.0 / Math.max(1, nowMs - r.firstStartMs);
		}
		out.sort((a, b) -> a.kind != b.kind ? a.kind.compareTo(b.kind)
			: b.kills != a.kills ? Integer.compare(b.kills, a.kills) : a.name.compareTo(b.name));
		return out;
	}

	/** Non-boss NPCs you or your party fought that died. Raid boss fights are skipped: their raid has them. */
	private static void addMonsters(Map<String, Rate> rates, Fight f)
	{
		for (TargetStats t : f.targets.values())
		{
			if (!t.boss && t.engaged && t.died && t.name != null && t.firstMs > 0 && t.diedMs >= t.firstMs)
			{
				add(rates, t.name, Kind.MONSTER, t.firstMs, t.diedMs - t.firstMs);
			}
		}
	}

	private static void add(Map<String, Rate> rates, String name, Kind kind, long startMs, long timeMs)
	{
		Rate r = rates.computeIfAbsent(kind + "|" + name, k -> new Rate(name, kind));
		r.kills++;
		r.firstStartMs = Math.min(r.firstStartMs, startMs);
		r.totalMs += timeMs;
	}
}
