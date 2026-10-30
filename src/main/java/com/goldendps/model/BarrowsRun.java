package com.goldendps.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A Barrows run: from your first hit on a brother until you open the chest. Banking, restoring
 * and teleporting back count. The clock stops when you log out and when no brother was hit for
 * {@link #IDLE_MS}; the next brother hit starts it again.
 * A brother you already killed cannot be fought again before the chest, so hitting one starts a
 * new run (the old one was left).
 */
public class BarrowsRun
{
	public static final String NAME = "Barrows";
	/** No brother hit for this long stops the clock. */
	public static final long IDLE_MS = 600_000;

	private static final Set<String> BROTHERS = new HashSet<>(Arrays.asList(
		"Ahrim the Blighted", "Dharok the Wretched", "Guthan the Infested",
		"Karil the Tainted", "Torag the Corrupted", "Verac the Defiled"));

	/** A finished run. */
	public static final class Result
	{
		public final long startMs;
		public final long endMs;
		/** Counted time: without logged-out and idle time. */
		public final long activeMs;

		Result(long startMs, long endMs, long activeMs)
		{
			this.startMs = startMs;
			this.endMs = endMs;
			this.activeMs = activeMs;
		}
	}

	/** What is saved, so a run survives a client restart. Plain fields for Gson. */
	public static final class Saved
	{
		public long startMs;
		public long countedMs;
		public List<String> killed = new ArrayList<>();
	}

	private boolean running;
	private long startMs;
	/** Time counted before {@link #countFrom}. */
	private long countedMs;
	/** The clock runs from {@link #countFrom}; stopped after a logout until the next brother hit. */
	private boolean clockRunning;
	private long countFrom;
	/** The last brother hit (or login); the clock stops {@link #IDLE_MS} after it. */
	private long lastMs;
	private final Set<String> killed = new HashSet<>();

	public static boolean isBrother(String npcName)
	{
		return npcName != null && BROTHERS.contains(npcName);
	}

	/**
	 * All six brothers died in this run fight. Only such runs set a personal best: a run that
	 * lost its start (or skipped brothers) would otherwise look like a very fast one.
	 */
	public static boolean isFullRun(Fight run)
	{
		Set<String> dead = new HashSet<>();
		for (TargetStats t : run.targets.values())
		{
			if (t.died && isBrother(t.name))
			{
				dead.add(t.name);
			}
		}
		return dead.size() == BROTHERS.size();
	}

	/** You hit an NPC. Returns true when this hit started a new run. */
	public boolean hit(long now, String npcName)
	{
		if (!isBrother(npcName))
		{
			return false;
		}
		boolean started = !running || killed.contains(npcName);
		if (started)
		{
			running = true;
			startMs = now;
			countedMs = 0;
			killed.clear();
		}
		else
		{
			stopClock(now);
		}
		clockRunning = true;
		countFrom = now;
		lastMs = now;
		return started;
	}

	/** An NPC died. */
	public void died(String npcName)
	{
		if (running && isBrother(npcName))
		{
			killed.add(npcName);
		}
	}

	public void loggedOut(long now)
	{
		stopClock(now);
	}

	/** You opened the chest: the run, or null when no run was going on. */
	public Result chestOpened(long now)
	{
		if (!running)
		{
			return null;
		}
		stopClock(now);
		running = false;
		Result r = new Result(startMs, now, countedMs);
		killed.clear();
		return r;
	}

	public boolean isRunning()
	{
		return running;
	}

	/** The run time so far. */
	public long activeMs(long now)
	{
		return countedMs + (clockRunning ? Math.max(0, Math.min(now, lastMs + IDLE_MS) - countFrom) : 0);
	}

	/** The clock is stopped: logged out, or no brother hit for {@link #IDLE_MS}. */
	public boolean isPaused(long now)
	{
		return running && (!clockRunning || now > lastMs + IDLE_MS);
	}

	/** The run to save, or null when there is none. Saved with the clock stopped. */
	public Saved save(long now)
	{
		if (!running)
		{
			return null;
		}
		Saved s = new Saved();
		s.startMs = startMs;
		s.countedMs = activeMs(now);
		s.killed.addAll(killed);
		return s;
	}

	/** Takes over a saved run with the clock stopped; the next brother hit starts it again. */
	public void restore(Saved s)
	{
		clear();
		if (s == null)
		{
			return;
		}
		running = true;
		startMs = s.startMs;
		countedMs = s.countedMs;
		if (s.killed != null)
		{
			killed.addAll(s.killed);
		}
	}

	public void clear()
	{
		running = false;
		clockRunning = false;
		countedMs = 0;
		killed.clear();
	}

	private void stopClock(long now)
	{
		if (clockRunning)
		{
			countedMs += Math.max(0, Math.min(now, lastMs + IDLE_MS) - countFrom);
			clockRunning = false;
		}
	}
}
