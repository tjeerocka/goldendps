package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RecentTargetTest
{
	private final RecentTarget<String> target = new RecentTarget<>();

	@Test
	public void keptFor15SecondsAfterYouStopAttacking()
	{
		assertEquals("Blue dragon", target.get(100, "Blue dragon", false, true));
		assertEquals("walking away", "Blue dragon", target.get(105, null, false, true));
		assertEquals("Blue dragon", target.get(100 + RecentTarget.KEEP_TICKS, null, false, true));
		assertNull(target.get(101 + RecentTarget.KEEP_TICKS, null, false, true));
	}

	@Test
	public void bossesAreKeptAMinute()
	{
		target.get(100, "Vorkath", true, true);
		assertEquals("acid phase", "Vorkath", target.get(100 + RecentTarget.KEEP_BOSS_TICKS, null, false, true));
		assertNull(target.get(101 + RecentTarget.KEEP_BOSS_TICKS, null, false, true));
	}

	@Test
	public void attackingAgainStartsTheTimeAgain()
	{
		target.get(100, "Blue dragon", false, true);
		target.get(110, "Blue dragon", false, true);
		assertEquals("Blue dragon", target.get(110 + RecentTarget.KEEP_TICKS, null, false, true));
	}

	@Test
	public void aNewTargetReplacesTheOld()
	{
		target.get(100, "Vorkath", true, true);
		assertEquals("Zulrah", target.get(101, "Zulrah", true, true));
	}

	@Test
	public void aDeadOrGoneMonsterIsDroppedAtOnce()
	{
		target.get(100, "Vorkath", true, true);
		assertNull(target.get(101, null, false, false));
	}
}
