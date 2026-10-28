package com.goldendps.model;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Decides if a party member's target is the same NPC as yours.
 *
 * <p>NPC numbers (index) are only unique inside one world or instance. Two party
 * members in different instances on the same world (for example each doing their
 * own Vorkath) can have NPCs with the same number. So we also compare where the NPC
 * is: every instance has its own map coordinates, far away from the others.</p>
 */
public class TargetMatcher
{
	/** Same NPC if it is this close (tiles) to where we last saw it. NPCs move between ticks. */
	static final int MAX_DISTANCE = 15;
	private static final int MAX_REMEMBERED = 512;

	private final Map<String, int[]> lastSeen = new LinkedHashMap<String, int[]>(64, 0.75f, true)
	{
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, int[]> eldest)
		{
			return size() > MAX_REMEMBERED;
		}
	};

	public static String key(int world, int npcIndex)
	{
		return world + ":" + npcIndex;
	}

	/** Remember where an NPC near you is (call on hitsplats and when checking party hits). */
	public void seen(String key, int x, int y, int plane)
	{
		lastSeen.put(key, new int[]{x, y, plane});
	}

	/**
	 * Target key for a party member's hit. Your own key when it is the same NPC as yours,
	 * else a separate key for that member (so it never mixes with your targets).
	 */
	public String partyKey(int myWorld, int world, int npcIndex, int x, int y, int plane, String sender)
	{
		String key = key(world, npcIndex);
		if (world == myWorld)
		{
			int[] p = lastSeen.get(key);
			if (p != null && p[2] == plane && Math.abs(p[0] - x) <= MAX_DISTANCE && Math.abs(p[1] - y) <= MAX_DISTANCE)
			{
				return key;
			}
		}
		return "p:" + sender + ":" + key;
	}

	public void clear()
	{
		lastSeen.clear();
	}
}
