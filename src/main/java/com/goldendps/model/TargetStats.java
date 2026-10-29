package com.goldendps.model;

/** One NPC in a fight, keyed by "world:npcIndex" (the same on every client in that world). */
public class TargetStats
{
	public String key;
	public int npcId;
	public String name;
	public boolean boss;
	/** All damage hitsplats seen on the NPC. */
	public long seenDamage;
	/** Poison, venom, burn and bleed damage. */
	public long effectDamage;
	/** Hit by you or a party member; only these count towards unknown damage. */
	public boolean engaged;
	public boolean died;
	public long firstMs;
	public long lastMs;
	public long diedMs;

	public TargetStats()
	{
	}

	public TargetStats(String key, int npcId, String name, boolean boss)
	{
		this.key = key;
		this.npcId = npcId;
		this.name = name;
		this.boss = boss;
	}

	public void merge(TargetStats o)
	{
		seenDamage += o.seenDamage;
		effectDamage += o.effectDamage;
		engaged |= o.engaged;
		died |= o.died;
		boss |= o.boss;
		if (o.firstMs > 0 && (firstMs == 0 || o.firstMs < firstMs))
		{
			firstMs = o.firstMs;
		}
		lastMs = Math.max(lastMs, o.lastMs);
		diedMs = Math.max(diedMs, o.diedMs);
	}

	public TargetStats copy()
	{
		TargetStats c = new TargetStats(key, npcId, name, boss);
		c.merge(this);
		return c;
	}
}
