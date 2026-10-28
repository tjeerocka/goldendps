package com.goldendps.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns your hitpoints changes into heals. The game does not say why HP went up,
 * so we look at what you did around the same tick:
 * <ul>
 * <li>ate or drank in the last 2 ticks: {@link HealType#FOOD}</li>
 * <li>you hit an NPC this tick or last tick: {@link HealType#LIFESTEAL}
 *     (Sanguinesti staff, blood spells, Soulreaper axe, ...)</li>
 * <li>+1 or +2 with no reason: {@link HealType#REGEN}</li>
 * <li>anything else: {@link HealType#RECEIVED} (for example Heal Other from someone else)</li>
 * </ul>
 * HP that goes down without a hitsplat shortly after you cast Heal Other / Heal Group
 * is the HP you gave away: {@link HealType#HEAL_OTHER}.
 *
 * <p>HP is only read once per tick, so a heal and a hit in the same tick are
 * added back together: heal = HP change + damage hitsplats on you.</p>
 */
public class HealthWatcher
{
	static final int FOOD_TICKS = 2;
	static final int LIFESTEAL_TICKS = 1;
	static final int HEAL_OTHER_TICKS = 5;
	static final int MAX_REGEN = 2;

	public static class Heal
	{
		public final HealType type;
		public final int amount;

		Heal(HealType type, int amount)
		{
			this.type = type;
			this.amount = amount;
		}

		@Override
		public String toString()
		{
			return type + " " + amount;
		}
	}

	private int prevHp = -1;
	private int netChange;
	private int hitsplatDamage;
	private int lastConsumeTick = Integer.MIN_VALUE / 2;
	private int lastMyHitTick = Integer.MIN_VALUE / 2;
	private int healOtherCastTick = Integer.MIN_VALUE / 2;

	/** Forget the last HP value (login, world hop). */
	public void reset()
	{
		prevHp = -1;
		netChange = 0;
		hitsplatDamage = 0;
	}

	/** New current (boosted) hitpoints value. */
	public void hpChanged(int hp)
	{
		if (prevHp < 0)
		{
			prevHp = hp;
			return;
		}
		netChange += hp - prevHp;
		prevHp = hp;
	}

	public void consumed(int tick)
	{
		lastConsumeTick = tick;
	}

	/** You hit an NPC for more than 0. */
	public void myHit(int tick)
	{
		lastMyHitTick = tick;
	}

	public void healOtherCast(int tick)
	{
		healOtherCastTick = tick;
	}

	/** A damage hitsplat on you. */
	public void damageOnMe(int amount)
	{
		hitsplatDamage += Math.max(0, amount);
	}

	/** Call once at the end of every tick. Returns the heals found this tick. */
	public List<Heal> endTick(int tick)
	{
		List<Heal> out = new ArrayList<>(1);
		int change = netChange + hitsplatDamage;
		if (change > 0)
		{
			HealType type = classify(change, tick);
			// No HP really went up: only trust it when there is a clear reason
			boolean trusted = netChange > 0 || type == HealType.FOOD || type == HealType.LIFESTEAL;
			if (trusted)
			{
				out.add(new Heal(type, change));
			}
		}
		else if (change < 0 && tick - healOtherCastTick <= HEAL_OTHER_TICKS)
		{
			out.add(new Heal(HealType.HEAL_OTHER, -change));
			healOtherCastTick = Integer.MIN_VALUE / 2;
		}
		netChange = 0;
		hitsplatDamage = 0;
		return out;
	}

	HealType classify(int amount, int tick)
	{
		if (tick - lastConsumeTick <= FOOD_TICKS)
		{
			return HealType.FOOD;
		}
		if (tick - lastMyHitTick <= LIFESTEAL_TICKS)
		{
			return HealType.LIFESTEAL;
		}
		if (amount <= MAX_REGEN)
		{
			return HealType.REGEN;
		}
		return HealType.RECEIVED;
	}
}
