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
		assertHeal(heals, type, amount, 0);
	}

	private void assertHeal(List<HealthWatcher.Heal> heals, HealType type, int amount, int overheal)
	{
		assertEquals(heals.toString(), 1, heals.size());
		assertEquals(type, heals.get(0).type);
		assertEquals(amount, heals.get(0).amount);
		assertEquals("overheal", overheal, heals.get(0).overheal);
	}

	/** New watcher at this HP, max HP 99. */
	private HealthWatcher at(int hp)
	{
		HealthWatcher h = new HealthWatcher();
		h.setMaxHp(99);
		h.hpChanged(hp);
		h.endTick(0);
		return h;
	}

	// ---- overhealing: food ----

	@Test
	public void sharkAtFullHpIsAllOverheal()
	{
		HealthWatcher h = at(99);
		h.foodEaten(10, 20, 0);
		assertTrue("wait for the HP update", h.endTick(10).isEmpty());
		assertTrue(h.endTick(11).isEmpty());
		assertHeal(h.endTick(12), HealType.FOOD, 0, 20);
	}

	@Test
	public void sharkPartlyWasted()
	{
		HealthWatcher h = at(90);
		h.foodEaten(10, 20, 0);
		h.hpChanged(99);
		assertHeal(h.endTick(10), HealType.FOOD, 9, 11);
	}

	@Test
	public void sharkFullyUsedHasNoOverheal()
	{
		HealthWatcher h = at(50);
		h.foodEaten(10, 20, 0);
		h.hpChanged(70);
		assertHeal(h.endTick(10), HealType.FOOD, 20, 0);
	}

	@Test
	public void hpUpdateOneTickLater()
	{
		HealthWatcher h = at(90);
		h.foodEaten(10, 20, 0);
		assertTrue(h.endTick(10).isEmpty());
		h.hpChanged(99);
		assertHeal(h.endTick(11), HealType.FOOD, 9, 11);
	}

	@Test
	public void brewAboveMaxWastesOnlyAboveItsCap()
	{
		HealthWatcher h = at(110);
		h.foodEaten(10, 16, 16); // cap = 99 + 16 = 115
		h.hpChanged(115);
		assertHeal(h.endTick(10), HealType.FOOD, 5, 11);
	}

	@Test
	public void brewBelowItsCapHasNoOverheal()
	{
		HealthWatcher h = at(98);
		h.foodEaten(10, 16, 16);
		h.hpChanged(114);
		assertHeal(h.endTick(10), HealType.FOOD, 16, 0);
	}

	@Test
	public void karambwanWhileBrewBoostedIsWasted()
	{
		HealthWatcher h = at(105);
		h.foodEaten(10, 18, 0);
		h.endTick(10);
		h.endTick(11);
		assertHeal(h.endTick(12), HealType.FOOD, 0, 18);
	}

	@Test
	public void comboEatSharkAndKarambwan()
	{
		HealthWatcher h = at(70);
		h.foodEaten(10, 20, 0);
		h.foodEaten(10, 18, 0);
		h.hpChanged(99);
		assertHeal(h.endTick(10), HealType.FOOD, 29, 9);
	}

	@Test
	public void unknownFoodHasNoOverheal()
	{
		HealthWatcher h = at(50);
		h.foodEaten(10, HealTable.UNKNOWN, 0);
		h.hpChanged(57);
		assertHeal(h.endTick(10), HealType.FOOD, 7, 0);

		HealthWatcher full = at(99);
		full.foodEaten(10, HealTable.UNKNOWN, 0);
		full.endTick(10);
		full.endTick(11);
		assertTrue(full.endTick(12).isEmpty());
	}

	@Test
	public void eatAndGetHitSameTickWithOverheal()
	{
		HealthWatcher h = at(90);
		h.foodEaten(10, 20, 0);
		h.damageOnMe(10);
		h.hpChanged(89); // healed to 99 (9 used), then hit for 10
		// effective 9 is right; HP did not end at the cap, so no overheal is counted (we can't be sure)
		assertHeal(h.endTick(10), HealType.FOOD, 9, 0);
	}

	// ---- overhealing: lifesteal ----

	@Test
	public void sanguinestiProcAtFullHpIsAllOverheal()
	{
		HealthWatcher h = at(99);
		h.myHit(20);
		h.lifesteal(20, 15, 0);
		assertTrue(h.endTick(20).isEmpty());
		assertHeal(h.endTick(21), HealType.LIFESTEAL, 0, 15);
	}

	@Test
	public void sanguinestiPartlyWasted()
	{
		HealthWatcher h = at(95);
		h.myHit(20);
		h.lifesteal(20, 15, 0);
		h.hpChanged(99);
		assertHeal(h.endTick(20), HealType.LIFESTEAL, 4, 11);
	}

	@Test
	public void bloodSpellNotCappedHasNoOverheal()
	{
		HealthWatcher h = at(50);
		h.myHit(20);
		h.lifesteal(20, 12, 0);
		h.hpChanged(60); // table said 12, game gave 10: not capped, so no waste
		assertHeal(h.endTick(20), HealType.LIFESTEAL, 10, 0);
	}

	@Test
	public void bloodSceptreCanHealAboveMax()
	{
		HealthWatcher h = at(99);
		h.myHit(20);
		h.lifesteal(20, 30, 9); // cap 108
		h.hpChanged(108);
		assertHeal(h.endTick(20), HealType.LIFESTEAL, 9, 21);
	}

	@Test
	public void foodAndLifestealSameTick()
	{
		HealthWatcher h = at(60);
		h.myHit(20);
		h.foodEaten(20, 20, 0);
		h.lifesteal(20, 10, 0);
		h.hpChanged(90);
		List<HealthWatcher.Heal> heals = h.endTick(20);
		assertEquals(heals.toString(), 2, heals.size());
		assertEquals(HealType.FOOD, heals.get(0).type);
		assertEquals(20, heals.get(0).amount);
		assertEquals(HealType.LIFESTEAL, heals.get(1).type);
		assertEquals(10, heals.get(1).amount);
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
