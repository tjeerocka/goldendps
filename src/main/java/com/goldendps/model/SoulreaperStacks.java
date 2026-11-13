package com.goldendps.model;

import java.util.Locale;

/**
 * The Soulreaper axe's soul stacks: one per attack (hit or miss), at most five; one is lost every 50 ticks
 * without an attack; its special attack (Behead) uses them all, for 12% more accuracy each. Client thread only.
 */
public class SoulreaperStacks
{
	static final int MAX_STACKS = 5;
	static final int DECAY_TICKS = 50;

	private int stacks;
	private int lastChangeTick;

	/**
	 * You attacked.
	 * @param spec it was the special attack, which uses all stacks
	 */
	public void attack(int tick, String weapon, boolean spec)
	{
		decay(tick);
		if (weapon == null || !weapon.toLowerCase(Locale.ROOT).startsWith("soulreaper axe"))
		{
			return;
		}
		stacks = spec ? 0 : Math.min(MAX_STACKS, stacks + 1);
		lastChangeTick = tick;
	}

	public int stacks(int tick)
	{
		decay(tick);
		return stacks;
	}

	private void decay(int tick)
	{
		while (stacks > 0 && tick - lastChangeTick >= DECAY_TICKS)
		{
			stacks--;
			lastChangeTick += DECAY_TICKS;
		}
	}
}
