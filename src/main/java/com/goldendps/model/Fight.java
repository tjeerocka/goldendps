package com.goldendps.model;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** One fight (or a merged total of several fights). Plain fields so Gson can save it. */
public class Fight
{
	/** A fight shorter than one game tick still counts as one tick for DPS. */
	public static final long MIN_DURATION_MS = 600;

	public String id;
	public String sessionId;
	public long startMs;
	public long lastActivityMs;
	/** 0 while the fight is still going. */
	public long endMs;
	public String endReason;
	/** Only used for merged totals: sum of the fight times. */
	public long mergedDurationMs;
	public int mergedFights;

	public Map<String, PlayerStats> players = new LinkedHashMap<>();
	public Map<String, TargetStats> targets = new LinkedHashMap<>();

	public Fight()
	{
	}

	public Fight(String id, String sessionId, long startMs)
	{
		this.id = id;
		this.sessionId = sessionId;
		this.startMs = startMs;
		this.lastActivityMs = startMs;
	}

	public boolean isActive()
	{
		return endMs == 0 && mergedFights == 0;
	}

	public long durationMs(long nowMs)
	{
		long d;
		if (mergedFights > 0)
		{
			d = mergedDurationMs;
		}
		else if (endMs > 0)
		{
			d = endMs - startMs;
		}
		else
		{
			d = nowMs - startMs;
		}
		return Math.max(MIN_DURATION_MS, d);
	}

	public PlayerStats player(String name, PlayerKind kind)
	{
		PlayerStats p = players.computeIfAbsent(name, n -> new PlayerStats(n, kind));
		if (kind == PlayerKind.LOCAL)
		{
			p.kind = PlayerKind.LOCAL;
		}
		else if (kind == PlayerKind.PARTY && p.kind == PlayerKind.ESTIMATE)
		{
			// they run GoldenDPS after all: their own reports win
			p.kind = PlayerKind.PARTY;
		}
		return p;
	}

	public TargetStats target(String key, int npcId, String name, boolean boss)
	{
		TargetStats t = targets.computeIfAbsent(key, k -> new TargetStats(k, npcId, name, boss));
		// NPCs change id/name between phases (Verzik, Nightmare...): keep the latest
		if (name != null && !name.isEmpty())
		{
			t.name = name;
		}
		t.npcId = npcId;
		t.boss |= boss;
		return t;
	}

	public boolean hasBossTarget()
	{
		for (TargetStats t : targets.values())
		{
			if (t.boss && t.engaged)
			{
				return true;
			}
		}
		return false;
	}

	/** Damage done by players (you + party), not unknown or effects. */
	public long knownDamage()
	{
		long sum = 0;
		for (PlayerStats p : players.values())
		{
			for (TargetDamage d : p.byTarget.values())
			{
				sum += d.damage;
			}
		}
		return sum;
	}

	/** Name for lists: the boss (or target) that took the most damage. */
	public String title()
	{
		Map<String, Long> byName = new HashMap<>();
		Map<String, Boolean> bossByName = new HashMap<>();
		for (PlayerStats p : players.values())
		{
			for (Map.Entry<String, TargetDamage> e : p.damageByTarget().entrySet())
			{
				TargetStats t = targets.get(e.getKey());
				String n = t == null || t.name == null ? "Unknown" : t.name;
				byName.merge(n, e.getValue().damage, Long::sum);
				if (t != null && t.boss)
				{
					bossByName.put(n, true);
				}
			}
		}
		String best = null;
		long bestDmg = -1;
		boolean bestBoss = false;
		for (Map.Entry<String, Long> e : byName.entrySet())
		{
			boolean boss = bossByName.containsKey(e.getKey());
			if ((boss && !bestBoss) || (boss == bestBoss && e.getValue() > bestDmg))
			{
				best = e.getKey();
				bestDmg = e.getValue();
				bestBoss = boss;
			}
		}
		if (best == null)
		{
			return "Fight";
		}
		return byName.size() > 1 ? best + " +" + (byName.size() - 1) : best;
	}

	public void merge(Fight o, long nowMs)
	{
		mergedDurationMs += o.durationMs(nowMs);
		mergedFights += o.mergedFights > 0 ? o.mergedFights : 1;
		if (startMs == 0 || (o.startMs > 0 && o.startMs < startMs))
		{
			startMs = o.startMs;
		}
		lastActivityMs = Math.max(lastActivityMs, o.lastActivityMs);
		// Target keys (world:index) get reused by other NPCs in other fights,
		// so in a merged total the key also holds the NPC name.
		Map<String, String> keyMap = new HashMap<>();
		for (TargetStats t : o.targets.values())
		{
			String newKey = o.mergedFights > 0 ? t.key : t.key + "|" + t.name;
			keyMap.put(t.key, newKey);
			TargetStats mine = targets.get(newKey);
			if (mine == null)
			{
				TargetStats c = t.copy();
				c.key = newKey;
				targets.put(newKey, c);
			}
			else
			{
				mine.merge(t);
			}
		}
		for (PlayerStats p : o.players.values())
		{
			PlayerStats mine = players.computeIfAbsent(p.name, n -> new PlayerStats(n, p.kind));
			if (p.kind == PlayerKind.LOCAL)
			{
				mine.kind = PlayerKind.LOCAL;
			}
			PlayerStats remapped = new PlayerStats(p.name, p.kind);
			remapped.damageTaken = p.damageTaken;
			remapped.seenTaken = p.seenTaken;
			remapped.healFood = p.healFood;
			remapped.healLifesteal = p.healLifesteal;
			remapped.healRegen = p.healRegen;
			remapped.healOther = p.healOther;
			for (Map.Entry<String, TargetDamage> e : p.byTarget.entrySet())
			{
				String k = keyMap.getOrDefault(e.getKey(), e.getKey());
				remapped.byTarget.computeIfAbsent(k, x -> new TargetDamage()).merge(e.getValue());
			}
			for (Map.Entry<String, TargetDamage> e : p.estByTarget.entrySet())
			{
				String k = keyMap.getOrDefault(e.getKey(), e.getKey());
				remapped.estByTarget.computeIfAbsent(k, x -> new TargetDamage()).merge(e.getValue());
			}
			mine.merge(remapped);
		}
	}

	public Fight copy()
	{
		Fight c = new Fight(id, sessionId, startMs);
		c.lastActivityMs = lastActivityMs;
		c.endMs = endMs;
		c.endReason = endReason;
		c.mergedDurationMs = mergedDurationMs;
		c.mergedFights = mergedFights;
		for (PlayerStats p : players.values())
		{
			c.players.put(p.name, p.copy());
		}
		for (TargetStats t : targets.values())
		{
			c.targets.put(t.key, t.copy());
		}
		return c;
	}
}
