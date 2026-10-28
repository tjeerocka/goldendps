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
	/** Guessed damage per target (only used for ESTIMATE players). */
	public Map<String, TargetDamage> estByTarget = new LinkedHashMap<>();
	/** Hitsplats you saw on this player (only used for ESTIMATE players). */
	public long seenTaken;
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

	public void addEstimate(String targetKey, int amount, boolean miss)
	{
		estByTarget.computeIfAbsent(targetKey, k -> new TargetDamage()).add(amount, miss);
	}

	/** Damage per target that counts for this player: own reports, or the guess. */
	public Map<String, TargetDamage> damageByTarget()
	{
		return kind == PlayerKind.ESTIMATE ? estByTarget : byTarget;
	}

	/** Damage taken that counts: own reports, or what we saw. */
	public long takenTotal()
	{
		return kind == PlayerKind.ESTIMATE ? seenTaken : damageTaken;
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
		if (o.kind == PlayerKind.LOCAL || (o.kind == PlayerKind.PARTY && kind == PlayerKind.ESTIMATE))
		{
			kind = o.kind;
		}
		for (Map.Entry<String, TargetDamage> e : o.byTarget.entrySet())
		{
			byTarget.computeIfAbsent(e.getKey(), k -> new TargetDamage()).merge(e.getValue());
		}
		for (Map.Entry<String, TargetDamage> e : o.estByTarget.entrySet())
		{
			estByTarget.computeIfAbsent(e.getKey(), k -> new TargetDamage()).merge(e.getValue());
		}
		damageTaken += o.damageTaken;
		seenTaken += o.seenTaken;
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
