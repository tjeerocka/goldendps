package com.goldendps.model;

import java.util.HashMap;
import java.util.Map;

/** The biggest hit of every player in this session, to announce new max hits. */
public class MaxHits
{
	/** A hit bigger than the player's earlier best. */
	public static final class NewMax
	{
		public final String player;
		public final int amount;
		public final int previous;

		NewMax(String player, int amount, int previous)
		{
			this.player = player;
			this.amount = amount;
			this.previous = previous;
		}
	}

	private final Map<String, Integer> best = new HashMap<>();

	/** Returns the new max hit, or null. A player's first hit only sets the bar. */
	public NewMax hit(String player, int amount)
	{
		int previous = best.getOrDefault(player, 0);
		if (amount <= previous)
		{
			return null;
		}
		best.put(player, amount);
		return previous > 0 ? new NewMax(player, amount, previous) : null;
	}

	public void reset()
	{
		best.clear();
	}
}
