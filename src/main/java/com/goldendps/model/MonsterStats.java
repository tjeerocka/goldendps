package com.goldendps.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Your kills of every monster type, added up over all time (no limit). Plain fields for Gson. */
public class MonsterStats
{
	public static final class Monster
	{
		public String name;
		public int kills;
		/** Time from the first hit on the monster to its death. */
		public long fastestMs;
		public long totalMs;
		public long lastKillMs;
		/** Your hits on it. */
		public TargetDamage attacks = new TargetDamage();
		/** Your hits per weapon, special attack or spell. */
		public Map<String, TargetDamage> weapons = new LinkedHashMap<>();

		public long averageMs()
		{
			return kills == 0 ? 0 : totalMs / kills;
		}

		Monster copy()
		{
			Monster c = new Monster();
			c.name = name;
			c.kills = kills;
			c.fastestMs = fastestMs;
			c.totalMs = totalMs;
			c.lastKillMs = lastKillMs;
			c.attacks = attacks.copy();
			for (Map.Entry<String, TargetDamage> e : weapons.entrySet())
			{
				c.weapons.put(e.getKey(), e.getValue().copy());
			}
			return c;
		}
	}

	/** A line of the panel list: a monster, a raid or a Barrows run, with its stats and personal bests. */
	public static final class Entry
	{
		public String name;
		/** Null for a raid or Barrows run (they only have personal bests). */
		public Monster stats;
		public BossRecords.Record solo;
		public BossRecords.Record party;
		public long lastMs;

		/** Personal bests also count kills from before the monster stats existed, so the biggest count wins. */
		public int kills()
		{
			int recordKills = (solo == null ? 0 : solo.kills) + (party == null ? 0 : party.kills);
			return Math.max(recordKills, stats == null ? 0 : stats.kills);
		}
	}

	public Map<String, Monster> byName = new LinkedHashMap<>();

	/** Adds every monster you hit that died in this fight. */
	public void add(Fight f)
	{
		// a raid's bosses, a Barrows run's brothers and a session total are also in fights of their own
		if (f.partOf != null || f.run || f.mergedFights > 0)
		{
			return;
		}
		PlayerStats me = localPlayer(f);
		if (me == null)
		{
			return;
		}
		for (TargetStats t : f.targets.values())
		{
			TargetDamage mine = me.byTarget.get(t.key);
			if (t.died && t.name != null && mine != null && mine.hits + mine.misses > 0)
			{
				addKill(t, mine, me);
			}
		}
	}

	private void addKill(TargetStats t, TargetDamage mine, PlayerStats me)
	{
		Monster m = byName.computeIfAbsent(t.name, n -> new Monster());
		m.name = t.name;
		m.kills++;
		long timeMs = t.diedMs - t.firstMs;
		if (timeMs > 0)
		{
			m.totalMs += timeMs;
			m.fastestMs = m.fastestMs <= 0 ? timeMs : Math.min(m.fastestMs, timeMs);
		}
		m.lastKillMs = Math.max(m.lastKillMs, t.diedMs);
		m.attacks.merge(mine);
		for (Map.Entry<String, Map<String, TargetDamage>> source : me.sources().entrySet())
		{
			TargetDamage onTarget = source.getValue().get(t.key);
			if (onTarget != null)
			{
				m.weapons.computeIfAbsent(source.getKey(), k -> new TargetDamage()).merge(onTarget);
			}
		}
	}

	private static PlayerStats localPlayer(Fight f)
	{
		for (PlayerStats p : f.players.values())
		{
			if (p.kind == PlayerKind.LOCAL)
			{
				return p;
			}
		}
		return null;
	}

	public void remove(String name)
	{
		byName.remove(name);
	}

	/** Monsters and personal bests together, one entry per name, last kill first. */
	public List<Entry> entries(List<BossRecords.Record> records)
	{
		Map<String, Entry> byEntry = new LinkedHashMap<>();
		for (Monster m : byName.values())
		{
			Entry e = entry(byEntry, m.name, m.lastKillMs);
			e.stats = m.copy();
		}
		for (BossRecords.Record r : records)
		{
			Entry e = entry(byEntry, r.boss, r.lastMs);
			if (r.party)
			{
				e.party = r;
			}
			else
			{
				e.solo = r;
			}
		}
		List<Entry> out = new ArrayList<>(byEntry.values());
		out.sort(Comparator.comparingLong((Entry e) -> -e.lastMs).thenComparing(e -> e.name));
		return out;
	}

	private static Entry entry(Map<String, Entry> byEntry, String name, long whenMs)
	{
		Entry e = byEntry.computeIfAbsent(name, n -> new Entry());
		e.name = name;
		e.lastMs = Math.max(e.lastMs, whenMs);
		return e;
	}

	public MonsterStats copy()
	{
		MonsterStats c = new MonsterStats();
		for (Map.Entry<String, Monster> e : byName.entrySet())
		{
			c.byName.put(e.getKey(), e.getValue().copy());
		}
		return c;
	}
}
