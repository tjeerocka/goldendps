package com.goldendps.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

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
		if (current != null)
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
		Fight f = ensureFight(now);
		TargetStats t = f.target(targetKey, npcId, npcName, boss);
		t.engaged = true;
		if (t.firstMs == 0)
		{
			t.firstMs = now;
		}
		t.lastMs = Math.max(t.lastMs, now);
		f.player(player, kind).addHit(targetKey, amount, miss, source);
		f.activity(now);

		Fight b = bossPartFor(now, boss);
		if (b != null)
		{
			TargetStats bt = b.target(targetKey, npcId, npcName, boss);
			bt.engaged = true;
			if (bt.firstMs == 0)
			{
				bt.firstMs = now;
			}
			bt.lastMs = Math.max(bt.lastMs, now);
			b.player(player, kind).addHit(targetKey, amount, miss, source);
			b.activity(now);
		}
	}

	/** Any damage hitsplat seen on an NPC. Starts a fight only on a boss. */
	public void seenHit(long now, String targetKey, int npcId, String npcName, boolean boss, int amount)
	{
		if (current == null)
		{
			if (!boss)
			{
				return;
			}
			ensureFight(now);
		}
		TargetStats t = current.target(targetKey, npcId, npcName, boss);
		t.seenDamage += Math.max(0, amount);
		if (t.engaged || t.boss)
		{
			current.activity(now);
		}
		Fight b = bossPartFor(now, boss);
		if (b != null)
		{
			TargetStats bt = b.target(targetKey, npcId, npcName, boss);
			bt.seenDamage += Math.max(0, amount);
			if (bt.engaged || bt.boss)
			{
				b.activity(now);
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
		for (TargetStats other : current.targets.values())
		{
			if (other.boss && other.engaged && !other.died)
			{
				return;
			}
		}
		end(now, "Boss died");
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
		f.finish(endMs, reason);
		if (f.knownDamage() == 0)
		{
			// only strangers' hits: not our fight
			return;
		}
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
		endNow(now, reason);
		raid = null;
	}

	public String getRaid()
	{
		return raid;
	}

	public Fight getBossPart()
	{
		return bossPart;
	}

	/** In a raid the current or last boss of this raid, otherwise the running or last fight. */
	public Fight getBossDisplayFight()
	{
		if (raid == null)
		{
			return getDisplayFight();
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
			bossPart.pauseAfterMs = pauseAfterMs;
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

	private void endBossPart(long endMs, String reason)
	{
		Fight b = bossPart;
		bossPart = null;
		b.finish(endMs, reason);
		if (b.knownDamage() == 0)
		{
			return;
		}
		history.add(0, b);
		trimHistory();
		if (listener != null)
		{
			listener.fightEnded(b);
		}
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
			current.pauseAfterMs = pauseAfterMs;
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

	/** All fights of this session added together, or null. */
	public Fight getSessionTotal(long now)
	{
		Fight total = new Fight("session", sessionId, 0);
		for (int i = history.size() - 1; i >= 0; i--)
		{
			Fight f = history.get(i);
			// raid boss fights are part of their raid fight
			if (sessionId.equals(f.sessionId) && f.partOf == null)
			{
				total.merge(f, now);
			}
		}
		if (current != null)
		{
			total.merge(current, now);
		}
		return total.mergedFights == 0 ? null : total;
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
