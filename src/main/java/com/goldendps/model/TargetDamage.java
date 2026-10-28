package com.goldendps.model;

/** One player's damage on one target. */
public class TargetDamage
{
	public long damage;
	/** Hitsplats with damage above 0. */
	public int hits;
	/** Blue 0 hitsplats (and 0 damage hits). */
	public int misses;
	public int biggestHit;

	public void add(int amount, boolean miss)
	{
		if (miss || amount <= 0)
		{
			misses++;
			return;
		}
		hits++;
		damage += amount;
		biggestHit = Math.max(biggestHit, amount);
	}

	public void merge(TargetDamage o)
	{
		damage += o.damage;
		hits += o.hits;
		misses += o.misses;
		biggestHit = Math.max(biggestHit, o.biggestHit);
	}

	public TargetDamage copy()
	{
		TargetDamage c = new TargetDamage();
		c.merge(this);
		return c;
	}
}
