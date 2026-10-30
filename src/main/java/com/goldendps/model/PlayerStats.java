package com.goldendps.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Everything one player did in one fight. Plain fields for Gson. */
public class PlayerStats
{
	public String name;
	public PlayerKind kind;
	/** Damage per target key. */
	public Map<String, TargetDamage> byTarget = new LinkedHashMap<>();
	/** Guessed damage per target, used for {@link PlayerKind#ESTIMATE} players. */
	public Map<String, TargetDamage> estByTarget = new LinkedHashMap<>();
	/** Damage per source (weapon, special attack or spell), then per target key. */
	public Map<String, Map<String, TargetDamage>> bySource = new LinkedHashMap<>();
	public long damageTaken;
	/** Hitsplats seen on this player, used for {@link PlayerKind#ESTIMATE} players. */
	public long seenTaken;
	public long healFood;
	public long healLifesteal;
	public long healRegen;
	public long healOther;
	public long overFood;
	public long overLifesteal;

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
		addHit(targetKey, amount, miss, null);
	}

	public void addHit(String targetKey, int amount, boolean miss, String source)
	{
		byTarget.computeIfAbsent(targetKey, k -> new TargetDamage()).add(amount, miss);
		if (source != null && !source.isEmpty())
		{
			sources().computeIfAbsent(source, k -> new LinkedHashMap<>())
				.computeIfAbsent(targetKey, k -> new TargetDamage()).add(amount, miss);
		}
	}

	/** Never null; files saved by older versions have no sources. */
	public Map<String, Map<String, TargetDamage>> sources()
	{
		if (bySource == null)
		{
			bySource = new LinkedHashMap<>();
		}
		return bySource;
	}

	public void addEstimate(String targetKey, int amount, boolean miss)
	{
		estByTarget.computeIfAbsent(targetKey, k -> new TargetDamage()).add(amount, miss);
	}

	public Map<String, TargetDamage> damageByTarget()
	{
		return kind == PlayerKind.ESTIMATE ? estByTarget : byTarget;
	}

	public long takenTotal()
	{
		return kind == PlayerKind.ESTIMATE ? seenTaken : damageTaken;
	}

	public void addHeal(HealType type, int amount)
	{
		addHeal(type, amount, 0);
	}

	public void addHeal(HealType type, int amount, int overheal)
	{
		if (overheal > 0)
		{
			if (type == HealType.FOOD)
			{
				overFood += overheal;
			}
			else if (type == HealType.LIFESTEAL)
			{
				overLifesteal += overheal;
			}
		}
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
				break;
		}
	}

	public long overheal()
	{
		return overFood + overLifesteal;
	}

	public long healing(boolean includeRegen)
	{
		return healFood + healLifesteal + healOther + (includeRegen ? healRegen : 0);
	}

	public void merge(PlayerStats o)
	{
		merge(o, UnaryOperator.identity());
	}

	/** Adds {@code o} to this player, renaming target keys with {@code keyMap}. */
	public void merge(PlayerStats o, UnaryOperator<String> keyMap)
	{
		if (o.kind == PlayerKind.LOCAL || (o.kind == PlayerKind.PARTY && kind == PlayerKind.ESTIMATE))
		{
			kind = o.kind;
		}
		mergeTargets(byTarget, o.byTarget, keyMap);
		mergeTargets(estByTarget, o.estByTarget, keyMap);
		for (Map.Entry<String, Map<String, TargetDamage>> s : o.sources().entrySet())
		{
			mergeTargets(sources().computeIfAbsent(s.getKey(), k -> new LinkedHashMap<>()), s.getValue(), keyMap);
		}
		damageTaken += o.damageTaken;
		seenTaken += o.seenTaken;
		healFood += o.healFood;
		healLifesteal += o.healLifesteal;
		healRegen += o.healRegen;
		healOther += o.healOther;
		overFood += o.overFood;
		overLifesteal += o.overLifesteal;
	}

	private static void mergeTargets(Map<String, TargetDamage> into, Map<String, TargetDamage> from,
		UnaryOperator<String> keyMap)
	{
		for (Map.Entry<String, TargetDamage> e : from.entrySet())
		{
			into.computeIfAbsent(keyMap.apply(e.getKey()), k -> new TargetDamage()).merge(e.getValue());
		}
	}

	/** Drops the damage on every target that is not in {@code keys}. */
	public void keepTargets(Set<String> keys)
	{
		byTarget.keySet().retainAll(keys);
		estByTarget.keySet().retainAll(keys);
		for (Map<String, TargetDamage> s : sources().values())
		{
			s.keySet().retainAll(keys);
		}
		sources().values().removeIf(Map::isEmpty);
	}

	public PlayerStats copy()
	{
		PlayerStats c = new PlayerStats(name, kind);
		c.merge(this);
		c.kind = kind;
		return c;
	}
}
