package com.goldendps.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Knows when you are inside a raid (by the map region of the raid instance), so the meter can
 * make one fight for the whole raid. Regions are the same as RuneLite's Discord plugin uses.
 */
public class RaidZone
{
	/** Not in the raid for this many ticks = you left (loading between rooms is shorter). */
	public static final int LEAVE_TICKS = 5;

	public static final String COX = "Chambers of Xeric";
	public static final String TOB = "Theatre of Blood";
	public static final String TOA = "Tombs of Amascut";
	public static final String TFA = "The Fractured Archive";

	private static final Map<Integer, String> REGIONS = new HashMap<>();

	static
	{
		put(COX, 12889, 13136, 13137, 13138, 13139, 13140, 13141, 13145, 13393, 13394, 13395, 13396, 13397, 13401);
		put(TOB, 12611, 12612, 12613, 12867, 12869, 13122, 13123, 13125, 13379);
		put(TOA, 14160, 14162, 14164, 14674, 14676, 15184, 15186, 15188, 15696, 15698, 15700);
		put(TFA, 16214, 16726, 16215, 16727, 16216, 16728, 16472, 16474);
	}

	private static void put(String raid, int... regions)
	{
		for (int r : regions)
		{
			REGIONS.put(r, raid);
		}
	}

	/** The raid this instance region belongs to, or null. */
	public static String raidFor(int region, boolean instance)
	{
		return instance ? REGIONS.get(region) : null;
	}

	/** What the tracker should do this tick. */
	public static final class Change
	{
		public static final Change NONE = new Change(null, false);

		/** Raid that started now, or null. */
		public final String start;
		public final boolean end;

		Change(String start, boolean end)
		{
			this.start = start;
			this.end = end;
		}
	}

	private String inside;
	private int outTicks;

	/** Call every game tick with {@link #raidFor} of where you are now. */
	public Change tick(String raidNow)
	{
		if (raidNow != null)
		{
			outTicks = 0;
			if (raidNow.equals(inside))
			{
				return Change.NONE;
			}
			inside = raidNow;
			return new Change(raidNow, false);
		}
		if (inside != null && ++outTicks >= LEAVE_TICKS)
		{
			inside = null;
			outTicks = 0;
			return new Change(null, true);
		}
		return Change.NONE;
	}

	public String getInside()
	{
		return inside;
	}

	public void reset()
	{
		inside = null;
		outTicks = 0;
	}
}
