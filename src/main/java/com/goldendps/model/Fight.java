package com.goldendps.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One fight, or a merged total of several fights. Plain fields for Gson. */
public class Fight
{
	/** Shortest duration used for DPS: one game tick. */
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
	/** Gaps without hits longer than this are not counted (active time). 0 disables it. */
	public long pauseAfterMs;
	/** Uncounted gaps as [from, to]. */
	public List<long[]> pauses = new ArrayList<>();
	/** Raid name for a raid fight and its boss fights. */
	public String raid;
	/** For a boss fight inside a raid: the id of the raid fight. */
	public String partOf;
	/**
	 * A finished run made of other fights (a Barrows run, from the first brother to the chest).
	 * Its kills are already counted in those fights.
	 */
	public boolean run;

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

	/** A whole raid in which every boss died, or a finished run. */
	public boolean isCompleteRaid()
	{
		return raid != null && partOf == null && endMs > 0 && (run || bossDiedMs() > 0);
	}

	public boolean isActive()
	{
		return endMs == 0 && mergedFights == 0;
	}

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

	public void finish(long endAtMs, String reason)
	{
		endMs = Math.max(endAtMs, startMs);
		addPause(endMs);
		endReason = reason;
	}

	/** The fight is running but the clock is stopped (no hits for the pause time). */
	public boolean isPaused(long nowMs)
	{
		return isActive() && pauseAfterMs > 0 && nowMs > lastActivityMs + pauseAfterMs;
	}

	private long clockNow(long nowMs)
	{
		return isPaused(nowMs) ? lastActivityMs + pauseAfterMs : nowMs;
	}

	long activeBetween(long startMs, long endMs)
	{
		if (endMs <= startMs)
		{
			return 0;
		}
		long active = endMs - startMs;
		if (pauses != null)
		{
			for (long[] pause : pauses)
			{
				long from = Math.max(startMs, pause[0]);
				long to = Math.min(endMs, pause[1]);
				if (to > from)
				{
					active -= to - from;
				}
			}
		}
		return active;
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
			d = activeBetween(startMs, endMs);
		}
		else
		{
			d = activeBetween(startMs, clockNow(nowMs));
		}
		return Math.max(MIN_DURATION_MS, d);
	}

	/** Active time from the first boss hit to the last boss death (or now). 0 without bosses. */
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

	/** Time of the last boss death once every engaged boss is dead, else 0. */
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

	/** A single fight with a boss; its boss meter stays until the next boss fight replaces it. */
	public boolean hasBossMeter()
	{
		return mergedFights == 0 && !run && hasBossTarget();
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
			p.kind = PlayerKind.PARTY;
		}
		return p;
	}

	public TargetStats target(String key, int npcId, String name, boolean boss)
	{
		TargetStats t = targets.computeIfAbsent(key, k -> new TargetStats(k, npcId, name, boss));
		// phase changes alter the id and name; keep the latest
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

	/** Damage reported by players (excludes unknown and effect damage). */
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

	public String title()
	{
		return title(false);
	}

	/** The raid, or the boss or target that took the most damage. */
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
		// a boss names the fight on its own; "+2" only helps for fights against normal monsters
		return byName.size() > 1 && !bestBoss ? best + " +" + (byName.size() - 1) : best;
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
		// NPC indexes are reused across fights, so merged keys include the NPC name.
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
			players.computeIfAbsent(p.name, n -> new PlayerStats(n, p.kind))
				.merge(p, k -> keyMap.getOrDefault(k, k));
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
		c.run = run;
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
