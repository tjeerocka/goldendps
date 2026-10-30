package com.goldendps.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Starts and ends fights and records every hit and heal. Free of RuneLite types, so it can be
 * tested with a fake clock. For your own hitsplats call {@link #playerHit} before
 * {@link #seenHit}, so the first hit of a fight is also seen.
 */
public class FightTracker
{
	/** Party reports arriving this long after a fight ended still count for it. */
	public static final long LATE_REPORT_MS = 5000;

	public interface Listener
	{
		void fightEnded(Fight fight);
	}

	private long idleTimeoutMs = 300_000;
	private long pauseAfterMs = 10_000;
	private int maxHistory = 50;
	private String sessionId = newId();
	private Fight current;
	/** The raid you are in; the running fight then covers the whole raid. */
	private String raid;
	/** Inside a raid: the fight for the boss being fought now. */
	private Fight bossPart;
	/** Ended fights, newest first. */
	private final List<Fight> history = new ArrayList<>();
	private Listener listener;

	private static String newId()
	{
		return UUID.randomUUID().toString();
	}

	public void setListener(Listener listener)
	{
		this.listener = listener;
	}

	public void setIdleTimeoutMs(long idleTimeoutMs)
	{
		this.idleTimeoutMs = Math.max(1000, idleTimeoutMs);
	}

	public void setPauseAfterMs(long pauseAfterMs)
	{
		this.pauseAfterMs = Math.max(0, pauseAfterMs);
		if (current != null && current.raid == null)
		{
			current.pauseAfterMs = this.pauseAfterMs;
		}
	}

	public void setMaxHistory(int maxHistory)
	{
		this.maxHistory = Math.max(1, maxHistory);
		trimHistory();
	}

	// ---- damage ----

	/** A hit by you or a party member. Starts a fight if none is running. */
	public void playerHit(long now, String player, PlayerKind kind, String targetKey, int npcId,
		String npcName, boolean boss, int amount, boolean miss)
	{
		playerHit(now, player, kind, targetKey, npcId, npcName, boss, amount, miss, null);
	}

	/** @param source weapon or spell name, or null */
	public void playerHit(long now, String player, PlayerKind kind, String targetKey, int npcId,
		String npcName, boolean boss, int amount, boolean miss, String source)
	{
		if (current == null && kind == PlayerKind.PARTY)
		{
			Fight late = recentFightWithTarget(now, targetKey);
			if (late != null)
			{
				late.player(player, kind).addHit(targetKey, amount, miss, source);
				return;
			}
		}
		for (Fight f : fightAndBossPart(now, boss))
		{
			TargetStats t = f.target(targetKey, npcId, npcName, boss);
			t.engaged = true;
			if (t.firstMs == 0)
			{
				t.firstMs = now;
			}
			t.lastMs = Math.max(t.lastMs, now);
			f.player(player, kind).addHit(targetKey, amount, miss, source);
			f.activity(now);
		}
	}

	/** Any damage hitsplat seen on an NPC. Starts a fight only on a boss. */
	public void seenHit(long now, String targetKey, int npcId, String npcName, boolean boss, int amount)
	{
		if (current == null && !boss)
		{
			return;
		}
		for (Fight f : fightAndBossPart(now, boss))
		{
			TargetStats t = f.target(targetKey, npcId, npcName, boss);
			t.seenDamage += Math.max(0, amount);
			if (t.engaged || t.boss)
			{
				f.activity(now);
			}
		}
	}

	/** Poison, venom, burn or bleed damage on an NPC. */
	public void effectHit(long now, String targetKey, int npcId, String npcName, boolean boss, int amount)
	{
		if (current == null || amount <= 0)
		{
			return;
		}
		current.target(targetKey, npcId, npcName, boss).effectDamage += amount;
		if (bossPart != null)
		{
			bossPart.target(targetKey, npcId, npcName, boss).effectDamage += amount;
		}
	}

	/** An estimated hit by a player without GoldenDPS. Call after {@link #seenHit}. */
	public void estimateHit(long now, String player, String targetKey, int amount, boolean miss)
	{
		if (current == null || player == null)
		{
			return;
		}
		for (Fight f : live())
		{
			PlayerStats p = f.player(player, PlayerKind.ESTIMATE);
			if (p.kind == PlayerKind.ESTIMATE)
			{
				p.addEstimate(targetKey, amount, miss);
			}
		}
	}

	/** A damage hitsplat seen on a player without GoldenDPS. */
	public void seenDamageTaken(long now, String player, int amount)
	{
		if (current == null || player == null || amount <= 0)
		{
			return;
		}
		for (Fight f : live())
		{
			f.player(player, PlayerKind.ESTIMATE).seenTaken += amount;
		}
	}

	// ---- damage taken and healing ----

	public void damageTaken(long now, String player, PlayerKind kind, int amount)
	{
		Fight f = fightForReport(now, kind);
		if (f != null && amount > 0)
		{
			f.player(player, kind).damageTaken += amount;
			if (f == current && bossPart != null)
			{
				bossPart.player(player, kind).damageTaken += amount;
			}
		}
	}

	public void heal(long now, String player, PlayerKind kind, HealType type, int amount)
	{
		heal(now, player, kind, type, amount, 0);
	}

	public void heal(long now, String player, PlayerKind kind, HealType type, int amount, int overheal)
	{
		Fight f = fightForReport(now, kind);
		if (f != null && (amount > 0 || overheal > 0))
		{
			f.player(player, kind).addHeal(type, amount, overheal);
			if (f == current && bossPart != null)
			{
				bossPart.player(player, kind).addHeal(type, amount, overheal);
			}
		}
	}

	// ---- fight end ----

	public void npcDied(long now, String targetKey, boolean boss)
	{
		if (current == null)
		{
			return;
		}
		if (bossPart != null)
		{
			TargetStats bt = bossPart.targets.get(targetKey);
			if (bt != null)
			{
				bt.died = true;
				bt.diedMs = now;
				if ((boss || bt.boss) && bt.engaged && allBossesDead(bossPart))
				{
					endBossPart(now, "Boss died");
				}
			}
		}
		TargetStats t = current.targets.get(targetKey);
		if (t == null)
		{
			return;
		}
		t.died = true;
		t.diedMs = now;
		if (!(boss || t.boss) || !t.engaged || raid != null)
		{
			// a raid fight lasts until the raid is left
			return;
		}
		// end once every engaged boss is dead (e.g. all three Dagannoth Kings)
		if (allBossesDead(current))
		{
			end(now, "Boss died");
		}
	}

	/** Ends the fight after the idle timeout. Call every game tick. */
	public void tick(long now)
	{
		if (bossPart != null && now - bossPart.lastActivityMs >= idleTimeoutMs)
		{
			endBossPart(Math.min(now, bossPart.lastActivityMs + Math.min(pauseAfterMs, idleTimeoutMs)),
				"No hits for " + Format.duration(idleTimeoutMs));
		}
		if (raid != null)
		{
			return;
		}
		if (current != null && now - current.lastActivityMs >= idleTimeoutMs)
		{
			// the live clock ran on for the pause time after the last hit
			long endAt = current.lastActivityMs + Math.min(pauseAfterMs, idleTimeoutMs);
			end(Math.min(now, endAt), "No hits for " + Format.duration(idleTimeoutMs));
		}
	}

	public void endNow(long now, String reason)
	{
		if (bossPart != null)
		{
			endBossPart(now, reason);
		}
		if (current != null)
		{
			end(now, reason);
		}
	}

	private void end(long endMs, String reason)
	{
		Fight f = current;
		current = null;
		finishAndKeep(f, endMs, reason);
	}

	private void endBossPart(long endMs, String reason)
	{
		Fight b = bossPart;
		bossPart = null;
		finishAndKeep(b, endMs, reason);
	}

	private void finishAndKeep(Fight f, long endMs, String reason)
	{
		f.finish(endMs, reason);
		if (f.knownDamage() == 0)
		{
			// only strangers' hits: not our fight
			return;
		}
		addToHistory(f);
	}

	private void addToHistory(Fight f)
	{
		history.add(0, f);
		trimHistory();
		if (listener != null)
		{
			listener.fightEnded(f);
		}
	}

	// ---- raids ----

	/**
	 * Ends the running fight; the next fight covers the whole raid and only ends with
	 * {@link #endRaid}. Each boss in the raid also gets a fight of its own.
	 */
	public void startRaid(long now, String name)
	{
		if (name == null || name.equals(raid))
		{
			return;
		}
		endNow(now, "Raid started");
		raid = name;
	}

	public void endRaid(long now, String reason)
	{
		if (raid == null)
		{
			return;
		}
		// the raid ends with its last hit, not when you leave after looting
		endNow(current != null ? Math.min(now, current.lastActivityMs) : now, reason);
		raid = null;
	}

	/**
	 * Adds a finished run (a Barrows run): the fights from {@code startMs} to {@code endMs} with
	 * targets that belong to the run, counting only the damage on those targets.
	 *
	 * @param activeMs the run time; the rest of the time from start to end is not counted
	 * @return the run fight
	 */
	public Fight addRun(String name, long startMs, long endMs, long activeMs, Predicate<TargetStats> belongs,
		String reason)
	{
		Fight run = new Fight(newId(), sessionId, startMs);
		run.raid = name;
		run.run = true;
		List<Fight> parts = new ArrayList<>(history);
		if (current != null)
		{
			parts.add(current);
		}
		for (Fight f : parts)
		{
			if (f.partOf != null || f.run || f.mergedFights > 0)
			{
				continue;
			}
			// by target: a brother can be fought in a fight that started before the run (a rat first)
			Set<String> keys = new HashSet<>();
			for (TargetStats t : f.targets.values())
			{
				if (belongs.test(t) && t.firstMs >= startMs && t.firstMs <= endMs)
				{
					keys.add(t.key);
					TargetStats runTarget = t.copy();
					runTarget.key = runKey(f, t.key);
					run.targets.put(runTarget.key, runTarget);
				}
			}
			if (keys.isEmpty())
			{
				continue;
			}
			for (PlayerStats p : f.players.values())
			{
				PlayerStats only = p.copy();
				only.keepTargets(keys);
				// NPC indexes are reused: two brothers may have had the same key in different fights
				run.player(p.name, p.kind).merge(only, key -> runKey(f, key));
			}
		}
		run.lastActivityMs = endMs;
		run.finish(endMs, reason);
		if (endMs - startMs > activeMs)
		{
			// the uncounted time (logged out, idle) as one gap at the end
			run.pauses.add(new long[]{startMs + activeMs, endMs});
		}
		addToHistory(run);
		return run;
	}

	private static String runKey(Fight f, String targetKey)
	{
		return f.id + "/" + targetKey;
	}

	public String getRaid()
	{
		return raid;
	}

	public Fight getBossPart()
	{
		return bossPart;
	}

	/**
	 * The fight for the boss meter: in a raid the current or last boss of this raid, otherwise the
	 * running or last fight that had a boss. It stays until the next boss fight.
	 */
	public Fight getBossDisplayFight()
	{
		if (raid == null)
		{
			if (current != null && current.hasBossTarget())
			{
				return current;
			}
			for (Fight f : history)
			{
				if (f.hasBossMeter())
				{
					return f;
				}
			}
			return null;
		}
		if (bossPart != null)
		{
			return bossPart;
		}
		if (current != null)
		{
			for (Fight f : history)
			{
				if (current.id.equals(f.partOf))
				{
					return f;
				}
			}
		}
		return null;
	}

	/** Inside a raid: the boss fight to record into; a boss hit starts one. */
	private Fight bossPartFor(long now, boolean boss)
	{
		if (raid == null || current == null)
		{
			return null;
		}
		if (bossPart == null && boss)
		{
			bossPart = new Fight(newId(), sessionId, now);
			bossPart.raid = raid;
			bossPart.partOf = current.id;
		}
		return bossPart;
	}

	private static boolean allBossesDead(Fight f)
	{
		for (TargetStats other : f.targets.values())
		{
			if (other.boss && other.engaged && !other.died)
			{
				return false;
			}
		}
		return true;
	}

	/** The running fight (started if needed) and, inside a raid, the boss fight the same hit counts for. */
	private List<Fight> fightAndBossPart(long now, boolean boss)
	{
		List<Fight> out = new ArrayList<>(2);
		out.add(ensureFight(now));
		Fight b = bossPartFor(now, boss);
		if (b != null)
		{
			out.add(b);
		}
		return out;
	}

	private List<Fight> live()
	{
		List<Fight> out = new ArrayList<>(2);
		if (current != null)
		{
			out.add(current);
		}
		if (bossPart != null)
		{
			out.add(bossPart);
		}
		return out;
	}

	// ---- helpers ----

	private Fight ensureFight(long now)
	{
		if (current == null)
		{
			current = new Fight(newId(), sessionId, now);
			// a raid runs on the game clock: waiting between rooms counts too
			current.pauseAfterMs = raid == null ? pauseAfterMs : 0;
			current.raid = raid;
		}
		return current;
	}

	private Fight fightForReport(long now, PlayerKind kind)
	{
		if (current != null)
		{
			return current;
		}
		if (kind == PlayerKind.PARTY && !history.isEmpty())
		{
			Fight last = history.get(0);
			if (now - last.endMs <= LATE_REPORT_MS)
			{
				return last;
			}
		}
		return null;
	}

	private Fight recentFightWithTarget(long now, String targetKey)
	{
		if (history.isEmpty())
		{
			return null;
		}
		Fight last = history.get(0);
		if (now - last.endMs <= LATE_REPORT_MS && last.targets.containsKey(targetKey))
		{
			return last;
		}
		return null;
	}

	private void trimHistory()
	{
		while (history.size() > maxHistory)
		{
			history.remove(history.size() - 1);
		}
	}

	// ---- reading ----

	public Fight getCurrent()
	{
		return current;
	}

	/** The running fight, or else the last one (raid boss fights excluded). */
	public Fight getDisplayFight()
	{
		if (current != null)
		{
			return current;
		}
		for (Fight f : history)
		{
			if (f.partOf == null)
			{
				return f;
			}
		}
		return null;
	}

	public List<Fight> getHistory()
	{
		return Collections.unmodifiableList(history);
	}

	public Fight findFight(String id)
	{
		if (current != null && current.id.equals(id))
		{
			return current;
		}
		for (Fight f : history)
		{
			if (f.id.equals(id))
			{
				return f;
			}
		}
		return null;
	}

	/** Ended fights of this session, newest first. */
	public List<Fight> getSessionFights()
	{
		List<Fight> out = new ArrayList<>();
		for (Fight f : history)
		{
			if (sessionId.equals(f.sessionId))
			{
				out.add(f);
			}
		}
		return out;
	}

	/** All fights of this session added together, or null. */
	public Fight getSessionTotal(long now)
	{
		List<Fight> fights = new ArrayList<>();
		for (Fight f : history)
		{
			if (isWholeFight(f))
			{
				fights.add(f);
			}
		}
		if (current != null)
		{
			fights.add(0, current);
		}
		return added("session", fights, now);
	}

	/**
	 * The overall meter: the running or last fight together with the fights before it, back to the
	 * last break of {@link #idleTimeoutMs} without fighting. So the meter keeps counting over boss
	 * kills (Barrows, a slayer trip) instead of starting at zero after every kill.
	 */
	public Fight getOverall(long now)
	{
		List<Fight> chain = new ArrayList<>();
		if (current != null)
		{
			chain.add(current);
		}
		for (Fight f : history)
		{
			if (!isWholeFight(f))
			{
				continue;
			}
			if (!chain.isEmpty() && chain.get(chain.size() - 1).startMs - f.endMs > idleTimeoutMs)
			{
				break;
			}
			chain.add(f);
		}
		return chain.size() == 1 ? chain.get(0) : added("overall", chain, now);
	}

	/** The overall meter goes on: a fight runs, or the last one ended less than the idle time ago. */
	public boolean isOverallGoingOn(long now)
	{
		if (current != null)
		{
			return true;
		}
		for (Fight f : history)
		{
			if (isWholeFight(f))
			{
				return now - f.endMs <= idleTimeoutMs;
			}
		}
		return false;
	}

	/** A fight of this session that is not part of another one (raid boss fights, runs). */
	private boolean isWholeFight(Fight f)
	{
		return sessionId.equals(f.sessionId) && f.partOf == null && !f.run && f.mergedFights == 0;
	}

	/** The fights (newest first) added together, or null without fights. */
	private static Fight added(String id, List<Fight> newestFirst, long now)
	{
		if (newestFirst.isEmpty())
		{
			return null;
		}
		Fight total = new Fight(id, newestFirst.get(0).sessionId, 0);
		for (int i = newestFirst.size() - 1; i >= 0; i--)
		{
			total.merge(newestFirst.get(i), now);
		}
		return total;
	}

	/** Ends the running fight and starts a new session; the history is kept. */
	public void resetSession(long now)
	{
		endNow(now, "Reset");
		sessionId = newId();
	}

	public void clearHistory()
	{
		current = null;
		bossPart = null;
		history.clear();
	}

	/** Replaces the history with saved fights (newest first); keeps the running fight. */
	public void setHistory(List<Fight> fights)
	{
		history.clear();
		if (fights != null)
		{
			for (Fight f : fights)
			{
				if (f != null && f.id != null && f.players != null && f.targets != null)
				{
					history.add(f);
				}
			}
		}
		trimHistory();
	}
}
