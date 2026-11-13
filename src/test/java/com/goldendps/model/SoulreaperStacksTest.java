package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SoulreaperStacksTest
{
	private final SoulreaperStacks souls = new SoulreaperStacks();

	@Test
	public void oneStackPerAttackUpToFive()
	{
		for (int i = 0; i < 7; i++)
		{
			souls.attack(10 + i * 4, "Soulreaper axe", false);
		}
		assertEquals(5, souls.stacks(40));
	}

	@Test
	public void theSpecUsesAllStacks()
	{
		souls.attack(10, "Soulreaper axe", false);
		souls.attack(14, "Soulreaper axe", false);
		souls.attack(18, "Soulreaper axe", true);
		assertEquals(0, souls.stacks(19));
	}

	@Test
	public void oneStackIsLostEvery50TicksWithoutAttacking()
	{
		souls.attack(10, "Soulreaper axe", false);
		souls.attack(14, "Soulreaper axe", false);
		assertEquals(2, souls.stacks(63));
		assertEquals(1, souls.stacks(64));
		assertEquals(0, souls.stacks(114));
	}

	@Test
	public void otherWeaponsDoNotGiveStacks()
	{
		souls.attack(10, "Abyssal whip", false);
		assertEquals(0, souls.stacks(11));
	}
}
