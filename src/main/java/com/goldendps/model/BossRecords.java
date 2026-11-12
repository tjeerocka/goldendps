package com.goldendps.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Your personal bests per boss: fastest kill (boss time) and best own DPS on the boss.
 * Saved apart from the fight history, so old PBs stay when old fights are deleted.
 * Plain fields so Gson can save it.
 */
public class BossRecords
{
	/** One boss kill, worked out from a fight. */
	public static final class Kill
	{
		public String boss;
		/** Boss time (active time from the first boss hit to the boss death). */
		public long timeMs;
		/** Your DPS on the boss (0 = you did no damage). */
		public double myDps;
		/** Your damage on the boss: the record of a boss on a fixed timer. */
		public long myDamage;
		public long whenMs;
		/** The fight of this kill, to find its comparison later. */
		public String fightId;
		/** Two or more players did damage. Solo and party kills have their own records. */
		public boolean party;
	}

	/** The records for one boss. */
	public static final class Record
	{
		public String boss;
		public int kills;
		public long fastestMs;
		public double bestDps;
		public double lastDps;
		public long lastMs;
		/** Your most damage in one kill: the PB of a boss on a fixed timer. */
		public long mostDamage;
		/** Records of party kills; false = solo (records saved before the split were solo kills). */
		public boolean party;

		/** "Vorkath (solo)" or "Vorkath (party)". */
		public String label()
		{
			return boss + (party ? " (party)" : " (solo)");
		}

		Record copy()
		{
			Record c = new Record();
			c.boss = boss;
			c.party = party;
			c.kills = kills;
			c.fastestMs = fastestMs;
			c.bestDps = bestDps;
			c.lastDps = lastDps;
			c.lastMs = lastMs;
			c.mostDamage = mostDamage;
			return c;
		}
	}

	/** A kill compared with the records from before it. */
	public static final class Compare
	{
		public Kill kill;
		/** The records before this kill, null for the first kill. */
		public Record before;
		public boolean newFastest;
		public boolean newBestDps;
		public boolean newMostDamage;

		/** Your DPS compared with your last kill, in % (+5 = 5% more). NaN when there is no last kill. */
		public double vsLastDpsPercent()
		{
			if (before == null || before.lastDps <= 0 || kill.myDps <= 0)
			{
				return Double.NaN;
			}
			return (kill.myDps / before.lastDps - 1) * 100;
		}
	}

	public Map<String, Record> byBoss = new LinkedHashMap<>();

	/** The boss kill in this fight, or null (no boss killed). */
	/** A boss on a fixed timer: the Gemstone Crab moves on after 10 minutes whatever you do. */
	private static final String TIMED_BOSS = "Gemstone Crab";

	/** A boss on a fixed timer: its PB is your most damage, not the fastest time; no PB pace. */
	public static boolean isTimed(String boss)
	{
		return TIMED_BOSS.equals(boss);
	}

	public static Kill kill(Fight f)
	{
		// a whole raid is no boss kill (its bosses have their own fights)
		if (f == null || f.mergedFights > 0 || f.bossDiedMs() <= 0 || f.isWholeRaid())
		{
			return null;
		}
		long at = f.endMs > 0 ? f.endMs : f.bossDiedMs();
		FightReport r = FightReport.of(f, true, at);
		Kill k = new Kill();
		k.boss = r.getTitle();
		k.timeMs = f.bossDurationMs(at);
		k.whenMs = f.bossDiedMs();
		k.party = isParty(r);
		for (FightReport.PlayerRow row : r.getRows())
		{
			if (row.kind == PlayerKind.LOCAL)
			{
				k.myDps = row.dps;
				k.myDamage = row.damage;
			}
		}
		return k.boss == null || k.timeMs <= 0 ? null : k;
	}

	/** The boss kill or the finished raid in this fight (its record is the raid time), or null. */
	public static Kill record(Fight f)
	{
		Kill k = kill(f);
		if (k != null || f == null || !f.isCompleteRaid() || (f.run && !BarrowsRun.isFullRun(f)))
		{
			return k;
		}
		Kill run = new Kill();
		run.boss = f.raid;
		run.timeMs = f.durationMs(f.endMs);
		run.whenMs = f.endMs;
		FightReport report = FightReport.of(f, false, f.endMs);
		run.party = isParty(report);
		for (FightReport.PlayerRow row : report.getRows())
		{
			if (row.kind == PlayerKind.LOCAL)
			{
				run.myDps = row.dps;
			}
		}
		return run;
	}

	/** Two or more players (also guessed ones) did damage. */
	public static boolean isParty(FightReport r)
	{
		int players = 0;
		for (FightReport.PlayerRow row : r.getRows())
		{
			if (row.isPlayer() && row.damage > 0)
			{
				players++;
			}
		}
		return players >= 2;
	}

	/** Map key: the boss name for solo records (as saved before the split), with "|party" for party ones. */
	private static String key(String boss, boolean party)
	{
		return party ? boss + "|party" : boss;
	}

	/** Add a kill; returns how it compares with the records from before. */
	public Compare add(Kill k)
	{
		Compare c = new Compare();
		c.kill = k;
		Record r = byBoss.get(key(k.boss, k.party));
		c.before = r == null ? null : r.copy();
		if (r == null)
		{
			r = new Record();
			r.boss = k.boss;
			r.party = k.party;
			byBoss.put(key(k.boss, k.party), r);
		}
		r.kills++;
		if (!isTimed(k.boss) && (r.fastestMs <= 0 || k.timeMs < r.fastestMs))
		{
			r.fastestMs = k.timeMs;
			c.newFastest = true;
		}
		if (k.myDamage > r.mostDamage)
		{
			r.mostDamage = k.myDamage;
			c.newMostDamage = true;
		}
		if (k.myDps > 0)
		{
			if (k.myDps > r.bestDps)
			{
				r.bestDps = k.myDps;
				c.newBestDps = true;
			}
			r.lastDps = k.myDps;
		}
		r.lastMs = k.whenMs;
		return c;
	}

	/** The game told the real time of a kill that is already added: its fastest time is worked out again. */
	public void retime(Compare c, long timeMs)
	{
		Record r = byBoss.get(key(c.kill.boss, c.kill.party));
		c.kill.timeMs = timeMs;
		if (r == null || isTimed(c.kill.boss))
		{
			return;
		}
		long before = c.before == null ? 0 : c.before.fastestMs;
		c.newFastest = before <= 0 || timeMs < before;
		r.fastestMs = c.newFastest ? timeMs : before;
	}

	public void remove(String boss, boolean party)
	{
		byBoss.remove(key(boss, party));
	}

	public Record get(String boss, boolean party)
	{
		return boss == null ? null : byBoss.get(key(boss, party));
	}

	/** First start after the update: build the records from the saved fights (newest first). */
	public void seed(List<Fight> newestFirst)
	{
		for (int i = newestFirst.size() - 1; i >= 0; i--)
		{
			Kill k = record(newestFirst.get(i));
			if (k != null)
			{
				add(k);
			}
		}
	}

	/** All bosses, most kills first. */
	public List<Record> list()
	{
		List<Record> out = new ArrayList<>();
		for (Record r : byBoss.values())
		{
			out.add(r.copy());
		}
		out.sort(Comparator.comparingInt((Record r) -> -r.kills).thenComparing(r -> r.boss));
		return out;
	}

	/** Below this part of the boss's health done, the pace jumps around too much to show. */
	static final double MIN_PACE_DONE = 0.1;

	/**
	 * Live PB pace: the kill time this fight is heading for (boss time so far, spread over the
	 * health that is gone) minus the fastest kill. Negative = faster than the PB. Null when unknown.
	 */
	public static Long paceMs(long bossTimeMs, double healthDone, Record pb)
	{
		if (pb == null || pb.fastestMs <= 0 || healthDone < MIN_PACE_DONE || healthDone > 1)
		{
			return null;
		}
		return Math.round(bossTimeMs / healthDone) - pb.fastestMs;
	}

	/** "+0:05" or "-0:03". */
	public static String paceText(long paceMs)
	{
		return (paceMs < 0 ? "-" : "+") + Format.duration(Math.abs(paceMs));
	}

	/** Short tag for the boss meter title: "NEW PB!", "PB 1:10" or null. */
	public static String meterTag(Record rec, Compare kill)
	{
		String boss = kill != null ? kill.kill.boss : rec != null ? rec.boss : null;
		if (isTimed(boss))
		{
			return damageTag(rec, kill);
		}
		if (kill != null && kill.newFastest && kill.before != null)
		{
			return "NEW PB!";
		}
		if (kill != null && kill.before != null)
		{
			return "PB " + Format.preciseDuration(kill.before.fastestMs);
		}
		if (kill == null && rec != null && rec.fastestMs > 0)
		{
			return "PB " + Format.preciseDuration(rec.fastestMs);
		}
		return null;
	}

	/** {@link #meterTag} for a boss on a fixed timer: "NEW PB!" or "PB 12.3k" (most damage). */
	private static String damageTag(Record rec, Compare kill)
	{
		if (kill != null && kill.before != null)
		{
			return kill.newMostDamage ? "NEW PB!" : "PB " + Format.amount(kill.before.mostDamage);
		}
		return kill == null && rec != null && rec.mostDamage > 0 ? "PB " + Format.amount(rec.mostDamage) : null;
	}

	/** Panel lines for a boss kill: {label, value}. Empty when this fight was no boss kill. */
	public static List<String[]> killLines(Compare c)
	{
		List<String[]> out = new ArrayList<>();
		if (c == null)
		{
			return out;
		}
		Record b = c.before;
		if (isTimed(c.kill.boss))
		{
			String pb = b == null ? "" : c.newMostDamage ? " NEW PB! (was " + Format.amount(b.mostDamage) + ")"
				: " (PB " + Format.amount(b.mostDamage) + ")";
			out.add(new String[]{"Your damage", Format.amount(c.kill.myDamage) + pb});
		}
		else if (b == null)
		{
			out.add(new String[]{"Kill time", Format.preciseDuration(c.kill.timeMs) + " (first kill!)"});
		}
		else
		{
			out.add(new String[]{"Kill time", Format.preciseDuration(c.kill.timeMs)
				+ (c.newFastest ? " NEW PB! (was " : " (PB ") + Format.preciseDuration(b.fastestMs) + ")"});
		}
		if (c.kill.myDps > 0)
		{
			String best = b == null || b.bestDps <= 0 ? ""
				: c.newBestDps ? " NEW BEST (" + Format.dps(b.bestDps) + ")"
				: " (best " + Format.dps(b.bestDps) + ")";
			out.add(new String[]{"Your DPS", Format.dps(c.kill.myDps) + best});
		}
		double vs = c.vsLastDpsPercent();
		if (!Double.isNaN(vs))
		{
			out.add(new String[]{"vs last kill", String.format(java.util.Locale.US, "%+.0f%% DPS", vs)});
		}
		out.add(new String[]{"Kill count", String.valueOf(b == null ? 1 : b.kills + 1)});
		return out;
	}

	public BossRecords copy()
	{
		BossRecords c = new BossRecords();
		for (Map.Entry<String, Record> e : byBoss.entrySet())
		{
			c.byBoss.put(e.getKey(), e.getValue().copy());
		}
		return c;
	}
}
