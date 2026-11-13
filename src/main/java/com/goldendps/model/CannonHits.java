package com.goldendps.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Your cannon's hits look like your own hits, so this remembers when each of your cannonballs lands on a
 * monster: a hit of yours on that monster at that moment is the cannon's. Client thread only.
 */
public class CannonHits
{
	public static final String CANNON = "Dwarf multicannon";
	/** A cannonball and its hitsplat can be a tick apart. */
	static final int TICKS_EARLY_OR_LATE = 1;

	/** Target key to the ticks your cannonballs land on it. */
	private final Map<String, List<Integer>> landing = new HashMap<>();

	/** Your cannon fired at this monster; the ball lands at this tick. */
	public void fired(String targetKey, int landTick)
	{
		landing.computeIfAbsent(targetKey, k -> new ArrayList<>()).add(landTick);
	}

	/** A hit of yours on this monster now: true when it is a cannonball (each ball counts once). */
	public boolean isCannonHit(String targetKey, int tick)
	{
		List<Integer> ticks = landing.get(targetKey);
		if (ticks == null)
		{
			return false;
		}
		boolean found = false;
		for (Iterator<Integer> it = ticks.iterator(); it.hasNext(); )
		{
			int land = it.next();
			if (land < tick - TICKS_EARLY_OR_LATE)
			{
				// missed its hitsplat, for example when the monster died first
				it.remove();
			}
			else if (!found && land <= tick + TICKS_EARLY_OR_LATE)
			{
				it.remove();
				found = true;
			}
		}
		if (ticks.isEmpty())
		{
			landing.remove(targetKey);
		}
		return found;
	}

	public void clear()
	{
		landing.clear();
	}
}
