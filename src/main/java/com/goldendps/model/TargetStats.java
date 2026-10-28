package com.goldendps.model;

/**
 * One NPC in a fight. The key is "world:npcIndex", which is the same on every
 * client in the same world/instance, so party reports land on the same target.
 */
public class TargetStats
{
	public String key;
	public int npcId;
	public String name;
	public boolean boss;
	/** All damage hitsplats this client saw on the NPC (mine and others'). */
	public long seenDamage;
	/** Poison / venom / burn / bleed hitsplats this client saw. */
	public long effectDamage;
	/** Someone in the party (or you) hit it. Only these targets count for "Others (unknown)". */
	public boolean engaged;
	public boolean died;

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
	}

	public TargetStats copy()
	{
		TargetStats c = new TargetStats(key, npcId, name, boss);
		c.merge(this);
		return c;
	}
}
