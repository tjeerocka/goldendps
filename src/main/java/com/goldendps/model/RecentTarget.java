package com.goldendps.model;

/**
 * The monster you attack, kept for a while after you stop (to walk, eat or dodge), so its hit chance stays
 * shown: 15 seconds, a minute for bosses (Vorkath's acid, Zulrah's and Hydra's phases). Client thread only.
 * @param <T> the monster
 */
public class RecentTarget<T>
{
	static final int KEEP_TICKS = 25;
	static final int KEEP_BOSS_TICKS = 100;

	private T target;
	private int keepUntilTick;

	/**
	 * @param now the monster you attack now, or null
	 * @param boss the monster you attack now is a boss
	 * @param stillThere false when the kept monster died or is gone
	 * @return the monster to show, or null
	 */
	public T get(int tick, T now, boolean boss, boolean stillThere)
	{
		if (now != null)
		{
			target = now;
			keepUntilTick = tick + (boss ? KEEP_BOSS_TICKS : KEEP_TICKS);
		}
		else if (target != null && (!stillThere || tick > keepUntilTick))
		{
			target = null;
		}
		return target;
	}

	/** The monster kept now, or null. */
	public T kept()
	{
		return target;
	}
}
