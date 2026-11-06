package com.goldendps.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
		/** Kills with a known time (a respawn on the same NPC index has none). */
		public int timedKills;
		public long lastKillMs;
		/** Your hits on it. */
		public TargetDamage attacks = new TargetDamage();
		/** Your hits per weapon, special attack or spell. */
		public Map<String, TargetDamage> weapons = new LinkedHashMap<>();

		public long averageMs()
		{
			return timedKills == 0 ? 0 : totalMs / timedKills;
		}

		Monster copy()
		{
			Monster c = new Monster();
			c.name = name;
			c.kills = kills;
			c.fastestMs = fastestMs;
			c.totalMs = totalMs;
			c.timedKills = timedKills;
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

	/** Your hits on one target that are already counted, with the death they were counted at. */
	private static final class Counted
	{
		final long diedMs;
		final TargetDamage attacks;
		final Map<String, TargetDamage> weapons;

		Counted(long diedMs, TargetDamage attacks, Map<String, TargetDamage> weapons)
		{
			this.diedMs = diedMs;
			this.attacks = attacks;
			this.weapons = weapons;
		}
	}

	public Map<String, Monster> byName = new LinkedHashMap<>();

	/**
	 * What is counted per target of the running fight. NPC indexes are reused, so a respawn joins the
	 * target of the monster before it; only the hits since that kill are new. Not saved (transient).
	 */
	private transient String countedFightId;
	private transient Map<String, Counted> counted;

	/** Adds a kill the moment the monster dies, if you hit it. */
	public void addKill(Fight f, String targetKey)
	{
		TargetStats t = f.targets.get(targetKey);
		PlayerStats me = localPlayer(f);
		TargetDamage mine = me == null ? null : me.byTarget.get(targetKey);
		if (t == null || !t.died || t.name == null || mine == null)
		{
			return;
		}
		if (counted == null || !f.id.equals(countedFightId))
		{
			counted = new HashMap<>();
			countedFightId = f.id;
		}
		Counted before = counted.get(targetKey);
		TargetDamage attacks = minus(mine, before == null ? null : before.attacks);
		if (attacks.hits + attacks.misses == 0 || (before != null && before.diedMs == t.diedMs))
		{
			// you did not hit this one, or its death was already counted
			return;
		}
		Map<String, TargetDamage> weapons = weaponsOn(me, targetKey);
		counted.put(targetKey, new Counted(t.diedMs, mine.copy(), weapons));

		Monster m = byName.computeIfAbsent(t.name, n -> new Monster());
		m.name = t.name;
		m.kills++;
		long timeMs = t.diedMs - t.firstMs;
		if (before == null && timeMs > 0)
		{
			m.timedKills++;
			m.totalMs += timeMs;
			m.fastestMs = m.fastestMs <= 0 ? timeMs : Math.min(m.fastestMs, timeMs);
		}
		m.lastKillMs = Math.max(m.lastKillMs, t.diedMs);
		m.attacks.merge(attacks);
		for (Map.Entry<String, TargetDamage> w : weapons.entrySet())
		{
			TargetDamage newHits = minus(w.getValue(), before == null ? null : before.weapons.get(w.getKey()));
			if (newHits.hits + newHits.misses > 0)
			{
				m.weapons.computeIfAbsent(w.getKey(), k -> new TargetDamage()).merge(newHits);
			}
		}
	}

	/** Copies of your hits per weapon on one target. */
	private static Map<String, TargetDamage> weaponsOn(PlayerStats me, String targetKey)
	{
		Map<String, TargetDamage> out = new HashMap<>();
		for (Map.Entry<String, Map<String, TargetDamage>> source : me.sources().entrySet())
		{
			TargetDamage onTarget = source.getValue().get(targetKey);
			if (onTarget != null)
			{
				out.put(source.getKey(), onTarget.copy());
			}
		}
		return out;
	}

	/** The hits in {@code all} that are not in {@code counted}. The biggest hit stays: it is a maximum anyway. */
	private static TargetDamage minus(TargetDamage all, TargetDamage counted)
	{
		TargetDamage out = all.copy();
		if (counted != null)
		{
			out.damage -= counted.damage;
			out.hits -= counted.hits;
			out.misses -= counted.misses;
		}
		return out;
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
