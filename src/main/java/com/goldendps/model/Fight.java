package com.goldendps.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
	/** Only used for merged totals: sum of the boss times. */
	public long mergedBossMs;
	/**
	 * Active time, like Details!: when nobody hits for this long, the clock stops
	 * until the next hit. 0 = the clock never stops.
	 */
	public long pauseAfterMs;
	/** Time that is not counted (AFK gaps): [from, to] in ms. */
	public List<long[]> pauses = new ArrayList<>();
	/** Raid name when this fight is a whole raid (or a boss in a raid). Null outside raids. */
	public String raid;
	/** Id of the raid fight this boss fight is part of. Null for normal fights and the raid itself. */
	public String partOf;

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

	/** A hit happened: if nobody hit for longer than the pause time, that gap is not counted. */
	public void activity(long nowMs)
	{
		addPause(nowMs);
		lastActivityMs = Math.max(lastActivityMs, nowMs);
	}

	private void addPause(long nowMs)
	{
		if (pauseAfterMs <= 0)
		{
			return;
		}
		long from = lastActivityMs + pauseAfterMs;
		if (nowMs > from)
		{
			if (pauses == null)
			{
				pauses = new ArrayList<>();
			}
			pauses.add(new long[]{from, nowMs});
		}
	}

	/** Called when the fight ends at this time. */
	public void finish(long endAtMs, String reason)
	{
		endMs = Math.max(endAtMs, startMs);
		addPause(endMs);
		endReason = reason;
	}

	/** True while the fight runs but nobody hit for the pause time (clock stopped). */
	public boolean isPaused(long nowMs)
	{
		return isActive() && pauseAfterMs > 0 && nowMs > lastActivityMs + pauseAfterMs;
	}

	/** "Now" for the clock: it stops pauseAfterMs after the last hit. */
	private long clockNow(long nowMs)
	{
		return isPaused(nowMs) ? lastActivityMs + pauseAfterMs : nowMs;
	}

	/** Time from a to b without the pauses. */
	long activeBetween(long a, long b)
	{
		if (b <= a)
		{
			return 0;
		}
		long d = b - a;
		if (pauses != null)
		{
			for (long[] p : pauses)
			{
				long from = Math.max(a, p[0]);
				long to = Math.min(b, p[1]);
				if (to > from)
				{
					d -= to - from;
				}
			}
		}
		return d;
	}

	/** Active fight time (AFK gaps not counted). */
	public long durationMs(long nowMs)
	{
		long d;
		if (mergedFights > 0)
		{
			d = mergedDurationMs;
		}
		else if (endMs > 0)
		{
			d = activeBetween(startMs, endMs);
		}
		else
		{
			d = activeBetween(startMs, clockNow(nowMs));
		}
		return Math.max(MIN_DURATION_MS, d);
	}

	/**
	 * Time spent on bosses: from the first hit on a boss to the last boss death
	 * (or the last hit / now while a boss is alive). 0 when no boss was fought.
	 */
	public long bossDurationMs(long nowMs)
	{
		if (mergedFights > 0)
		{
			return mergedBossMs;
		}
		long start = 0;
		long end = 0;
		boolean alive = false;
		for (TargetStats t : targets.values())
		{
			if (!t.boss || !t.engaged || t.firstMs <= 0)
			{
				continue;
			}
			start = start == 0 ? t.firstMs : Math.min(start, t.firstMs);
			if (t.died && t.diedMs > 0)
			{
				end = Math.max(end, t.diedMs);
			}
			else
			{
				alive = true;
				end = Math.max(end, t.lastMs);
			}
		}
		if (start == 0)
		{
			return 0;
		}
		if (alive && isActive())
		{
			end = Math.max(end, clockNow(nowMs));
		}
		return Math.max(MIN_DURATION_MS, activeBetween(start, end));
	}

	/** When the last boss died, if every boss we fought is dead. Else 0. */
	public long bossDiedMs()
	{
		long died = 0;
		for (TargetStats t : targets.values())
		{
			if (t.boss && t.engaged)
			{
				if (!t.died || t.diedMs <= 0)
				{
					return 0;
				}
				died = Math.max(died, t.diedMs);
			}
		}
		return died;
	}

	/**
	 * Show the boss meter? Yes while a boss fight runs, and for hideAfterMs after
	 * the boss died (or after the fight ended without a kill).
	 */
	public boolean bossMeterVisible(long nowMs, long hideAfterMs)
	{
		if (mergedFights > 0 || !hasBossTarget())
		{
			return false;
		}
		long over = bossDiedMs();
		if (over == 0 && !isActive())
		{
			over = endMs;
		}
		return over == 0 || nowMs - over < hideAfterMs;
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
		return title(false);
	}

	/** Name for lists. bossOnly: only boss targets count. */
	public String title(boolean bossOnly)
	{
		if (raid != null && partOf == null && !bossOnly)
		{
			return raid;
		}
		Map<String, Long> byName = new HashMap<>();
		Map<String, Boolean> bossByName = new HashMap<>();
		for (PlayerStats p : players.values())
		{
			for (Map.Entry<String, TargetDamage> e : p.damageByTarget().entrySet())
			{
				TargetStats t = targets.get(e.getKey());
				if (bossOnly && (t == null || !t.boss))
				{
					continue;
				}
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
		mergedBossMs += o.bossDurationMs(nowMs);
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
			remapped.overFood = p.overFood;
			remapped.overLifesteal = p.overLifesteal;
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
			for (Map.Entry<String, Map<String, TargetDamage>> s : p.sources().entrySet())
			{
				Map<String, TargetDamage> to = remapped.sources().computeIfAbsent(s.getKey(), x -> new LinkedHashMap<>());
				for (Map.Entry<String, TargetDamage> e : s.getValue().entrySet())
				{
					String k = keyMap.getOrDefault(e.getKey(), e.getKey());
					to.computeIfAbsent(k, x -> new TargetDamage()).merge(e.getValue());
				}
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
		c.mergedBossMs = mergedBossMs;
		c.pauseAfterMs = pauseAfterMs;
		c.raid = raid;
		c.partOf = partOf;
		if (pauses != null)
		{
			for (long[] p : pauses)
			{
				c.pauses.add(new long[]{p[0], p[1]});
			}
		}
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
