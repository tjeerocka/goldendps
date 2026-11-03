package com.goldendps;

import com.goldendps.model.Fight;
import com.goldendps.model.TargetStats;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;

/** How far a boss is from dead, from its health bar. Client thread only. */
class BossHealth
{
	private final Client client;

	BossHealth(Client client)
	{
		this.client = client;
	}

	/**
	 * The part of the boss's health that is gone (0 to 1), or -1 when it is not known: no health bar,
	 * not in view, or several living bosses (Dagannoth Kings), where one bar says little.
	 */
	double done(Fight boss)
	{
		TargetStats alive = null;
		for (TargetStats t : boss.targets.values())
		{
			if (t.boss && t.engaged && !t.died)
			{
				if (alive != null)
				{
					return -1;
				}
				alive = t;
			}
		}
		NPC npc = alive == null ? null : npc(alive.key);
		if (npc == null || npc.getHealthScale() <= 0 || npc.getHealthRatio() < 0)
		{
			return -1;
		}
		return 1 - (double) npc.getHealthRatio() / npc.getHealthScale();
	}

	/** The NPC behind a target key ("world:index") in your scene, or null. */
	private NPC npc(String targetKey)
	{
		int split = targetKey.indexOf(':');
		WorldView view = client.getTopLevelWorldView();
		if (split < 0 || view == null || !targetKey.startsWith(client.getWorld() + ":"))
		{
			return null;
		}
		return view.npcs().byIndex(Integer.parseInt(targetKey.substring(split + 1)));
	}
}
