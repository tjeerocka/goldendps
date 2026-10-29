package com.goldendps.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Starts and ends fights and puts every number in the right place.
 * No RuneLite code in here: the plugin calls these methods on the client thread,
 * and the unit tests call them with a fake clock.
 *
 * <p>Order rule for the plugin: for one of your own hitsplats call
 * {@link #playerHit} first and then {@link #seenHit}, so the first hit of a
 * fight is also in the "seen" total.</p>
 */
public class FightTracker
{
	/** Party reports that arrive this long after the fight ended still go into it (network delay). */
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

	/** Active time: the clock stops when nobody hits for this long. 0 = never stop. */
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

	/** A hit by you (LOCAL) or a party member (PARTY). Starts a fight if none is running. */
	public void playerHit(long now, String player, PlayerKind kind, String targetKey, int npcId,
		String npcName, boolean boss, int amount, boolean miss)
	{
		if (current == null && kind == PlayerKind.PARTY)
		{
			Fight late = recentFightWithTarget(now, targetKey);
			if (late != null)
			{
				late.player(player, kind).addHit(targetKey, amount, miss);
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
		f.player(player, kind).addHit(targetKey, amount, miss);
		f.activity(now);
	}

	/**
	 * Any damage hitsplat this client saw on an NPC (yours and other people's).
	 * Only starts a fight on a boss; otherwise it only counts while a fight runs.
	 */
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
	}

	/** Poison / venom / burn / bleed on an NPC. Only counts while a fight runs. */
	public void effectHit(long now, String targetKey, int npcId, String npcName, boolean boss, int amount)
	{
		if (current == null || amount <= 0)
		{
			return;
		}
		current.target(targetKey, npcId, npcName, boss).effectDamage += amount;
	}

	/**
	 * A guess: this player (without GoldenDPS) probably did this hit.
	 * Call after {@link #seenHit}. Only counts while a fight runs.
	 */
	public void estimateHit(long now, String player, String targetKey, int amount, boolean miss)
	{
		if (current == null || player == null)
		{
			return;
		}
		PlayerStats p = current.player(player, PlayerKind.ESTIMATE);
		if (p.kind == PlayerKind.ESTIMATE)
		{
			p.addEstimate(targetKey, amount, miss);
		}
	}

	/** A damage hitsplat you saw on another player. Only counts while a fight runs. */
	public void seenDamageTaken(long now, String player, int amount)
	{
		if (current == null || player == null || amount <= 0)
		{
			return;
		}
		current.player(player, PlayerKind.ESTIMATE).seenTaken += amount;
	}

	// ---- damage taken and healing ----

	public void damageTaken(long now, String player, PlayerKind kind, int amount)
	{
		Fight f = fightForReport(now, kind);
		if (f != null && amount > 0)
		{
			f.player(player, kind).damageTaken += amount;
		}
	}

	public void heal(long now, String player, PlayerKind kind, HealType type, int amount)
	{
		heal(now, player, kind, type, amount, 0);
	}

	/** A heal with its overhealing (the part that was wasted). */
	public void heal(long now, String player, PlayerKind kind, HealType type, int amount, int overheal)
	{
		Fight f = fightForReport(now, kind);
		if (f != null && (amount > 0 || overheal > 0))
		{
			f.player(player, kind).addHeal(type, amount, overheal);
		}
	}

	// ---- fight end ----

	public void npcDied(long now, String targetKey, boolean boss)
	{
		if (current == null)
		{
			return;
		}
		TargetStats t = current.targets.get(targetKey);
		if (t == null)
		{
			return;
		}
		t.died = true;
		t.diedMs = now;
		if (!(boss || t.boss) || !t.engaged)
		{
			return;
		}
		// End when every boss we fought in this fight is dead (for example Dagannoth Kings)
		for (TargetStats other : current.targets.values())
		{
			if (other.boss && other.engaged && !other.died)
			{
				return;
			}
		}
		end(now, "Boss died");
	}

	/** Call every game tick: ends the fight when nobody hit anything for the idle time. */
	public void tick(long now)
	{
		if (current != null && now - current.lastActivityMs >= idleTimeoutMs)
		{
			// the clock ran on for the pause time after the last hit (same as the live clock)
			long endAt = current.lastActivityMs + Math.min(pauseAfterMs, idleTimeoutMs);
			end(Math.min(now, endAt), "No hits for " + Format.duration(idleTimeoutMs));
		}
	}

	public void endNow(long now, String reason)
	{
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
			// Only other people's hits (for example strangers at a boss): not our fight
			return;
		}
		history.add(0, f);
		trimHistory();
		if (listener != null)
		{
			listener.fightEnded(f);
		}
	}

	// ---- helpers ----

	private Fight ensureFight(long now)
	{
		if (current == null)
		{
			current = new Fight(newId(), sessionId, now);
			current.pauseAfterMs = pauseAfterMs;
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

	/** The running fight, or null. */
	public Fight getCurrent()
	{
		return current;
	}

	/** The running fight, or else the last fight. Can be null. */
	public Fight getDisplayFight()
	{
		if (current != null)
		{
			return current;
		}
		return history.isEmpty() ? null : history.get(0);
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

	/** All fights of this session (incl. the running one) added together. Null if none. */
	public Fight getSessionTotal(long now)
	{
		Fight total = new Fight("session", sessionId, 0);
		for (int i = history.size() - 1; i >= 0; i--)
		{
			Fight f = history.get(i);
			if (sessionId.equals(f.sessionId))
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

	public String getSessionId()
	{
		return sessionId;
	}

	/** Start a new session total. The running fight is ended first; history is kept. */
	public void resetSession(long now)
	{
		endNow(now, "Reset");
		sessionId = newId();
	}

	public void clearHistory()
	{
		current = null;
		history.clear();
	}

	/** Load saved fights (newest first). Keeps the running fight. */
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
