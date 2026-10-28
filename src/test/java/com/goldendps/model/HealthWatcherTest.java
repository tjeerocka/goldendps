package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class HealthWatcherTest
{
	private HealthWatcher w;

	@Before
	public void setUp()
	{
		w = new HealthWatcher();
		w.hpChanged(50); // first value only sets the start
		assertTrue(w.endTick(0).isEmpty());
	}

	private void assertHeal(List<HealthWatcher.Heal> heals, HealType type, int amount)
	{
		assertEquals(heals.toString(), 1, heals.size());
		assertEquals(type, heals.get(0).type);
		assertEquals(amount, heals.get(0).amount);
	}

	@Test
	public void eatingIsFood()
	{
		w.consumed(10);
		w.hpChanged(70);
		assertHeal(w.endTick(11), HealType.FOOD, 20);
	}

	@Test
	public void foodWindowEndsAfterTwoTicks()
	{
		w.consumed(10);
		w.hpChanged(70);
		assertHeal(w.endTick(13), HealType.RECEIVED, 20);
	}

	@Test
	public void healWhenIHitIsLifesteal()
	{
		w.myHit(20);
		w.hpChanged(58);
		assertHeal(w.endTick(20), HealType.LIFESTEAL, 8);
	}

	@Test
	public void smallHealWithoutReasonIsRegen()
	{
		w.hpChanged(51);
		assertHeal(w.endTick(30), HealType.REGEN, 1);
	}

	@Test
	public void bigHealWithoutReasonIsReceived()
	{
		w.hpChanged(80);
		assertHeal(w.endTick(30), HealType.RECEIVED, 30);
	}

	@Test
	public void eatAndGetHitInTheSameTick()
	{
		w.consumed(40);
		w.damageOnMe(15);
		w.hpChanged(55); // +20 food, -15 hit
		assertHeal(w.endTick(40), HealType.FOOD, 20);
	}

	@Test
	public void plainHitIsNoHeal()
	{
		w.damageOnMe(15);
		w.hpChanged(35);
		assertTrue(w.endTick(40).isEmpty());
	}

	@Test
	public void healOtherIsTheHpIGaveAway()
	{
		w.healOtherCast(50);
		w.hpChanged(13); // gave 37 hp
		assertHeal(w.endTick(52), HealType.HEAL_OTHER, 37);
		// only once per cast
		w.hpChanged(10);
		assertTrue(w.endTick(53).isEmpty());
	}

	@Test
	public void hpDropLongAfterCastIsNotHealOther()
	{
		w.healOtherCast(50);
		w.hpChanged(13);
		assertTrue(w.endTick(50 + HealthWatcher.HEAL_OTHER_TICKS + 1).isEmpty());
	}

	@Test
	public void resetForgetsOldHp()
	{
		w.reset();
		w.hpChanged(99);
		assertTrue(w.endTick(60).isEmpty());
	}
}
