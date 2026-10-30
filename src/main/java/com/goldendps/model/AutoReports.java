package com.goldendps.model;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Which ended fights are reported to the party by themselves, and when: every boss kill, every
 * finished raid and every Barrows run (not each brother), a few ticks later so the last party hits are in.
 */
public class AutoReports
{
	/** Wait this many ticks for the last party hits to arrive. */
	public static final int DELAY_TICKS = 5;

	private final Map<Fight, Integer> pending = new LinkedHashMap<>();

	/** A fight ended at this tick. */
	public void fightEnded(Fight fight, int tick, boolean includeRegen)
	{
		BossRecords.Kill kill = BossRecords.kill(fight, includeRegen);
		if (fight.isCompleteRaid() || (kill != null && !BarrowsRun.isBrother(kill.boss)))
		{
			pending.put(fight, tick + DELAY_TICKS);
		}
	}

	/** The fights to report now; each one only once. */
	public List<Fight> due(int tick)
	{
		List<Fight> out = new ArrayList<>();
		Iterator<Map.Entry<Fight, Integer>> it = pending.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<Fight, Integer> e = it.next();
			if (tick >= e.getValue())
			{
				out.add(e.getKey());
				it.remove();
			}
		}
		return out;
	}

	public void clear()
	{
		pending.clear();
	}
}
