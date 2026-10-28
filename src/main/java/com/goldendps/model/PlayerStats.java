package com.goldendps.model;

import java.util.LinkedHashMap;
import java.util.Map;

/** Everything one player did in one fight. */
public class PlayerStats
{
	public String name;
	public PlayerKind kind;
	/** Damage per target key. */
	public Map<String, TargetDamage> byTarget = new LinkedHashMap<>();
	public long damageTaken;
	public long healFood;
	public long healLifesteal;
	public long healRegen;
	public long healOther;

	public PlayerStats()
	{
	}

	public PlayerStats(String name, PlayerKind kind)
	{
		this.name = name;
		this.kind = kind;
	}

	public void addHit(String targetKey, int amount, boolean miss)
	{
		byTarget.computeIfAbsent(targetKey, k -> new TargetDamage()).add(amount, miss);
	}

	public void addHeal(HealType type, int amount)
	{
		if (amount <= 0)
		{
			return;
		}
		switch (type)
		{
			case FOOD:
				healFood += amount;
				break;
			case LIFESTEAL:
				healLifesteal += amount;
				break;
			case REGEN:
				healRegen += amount;
				break;
			case HEAL_OTHER:
				healOther += amount;
				break;
			default:
				// RECEIVED: not this player's healing
				break;
		}
	}

	/** Healing this player did: on themselves (food, lifesteal, optional regen) and on others. */
	public long healing(boolean includeRegen)
	{
		return healFood + healLifesteal + healOther + (includeRegen ? healRegen : 0);
	}

	public void merge(PlayerStats o)
	{
		if (kind == PlayerKind.PARTY && o.kind == PlayerKind.LOCAL)
		{
			kind = PlayerKind.LOCAL;
		}
		for (Map.Entry<String, TargetDamage> e : o.byTarget.entrySet())
		{
			byTarget.computeIfAbsent(e.getKey(), k -> new TargetDamage()).merge(e.getValue());
		}
		damageTaken += o.damageTaken;
		healFood += o.healFood;
		healLifesteal += o.healLifesteal;
		healRegen += o.healRegen;
		healOther += o.healOther;
	}

	public PlayerStats copy()
	{
		PlayerStats c = new PlayerStats(name, kind);
		c.merge(this);
		c.kind = kind;
		return c;
	}
}
