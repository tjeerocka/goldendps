package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class HealTableTest
{
	@Test
	public void fixedFood()
	{
		assertEquals(20, HealTable.foodHeal("Shark", 99));
		assertEquals(18, HealTable.foodHeal("Cooked karambwan", 99));
		assertEquals(22, HealTable.foodHeal("Manta ray", 99));
		assertEquals(0, HealTable.foodBoost("Shark", 99));
		assertEquals(HealTable.UNKNOWN, HealTable.foodHeal("Bread", 99));
		assertEquals(HealTable.UNKNOWN, HealTable.foodHeal(null, 99));
	}

	@Test
	public void saradominBrew()
	{
		assertEquals(16, HealTable.foodHeal("Saradomin brew(4)", 99));
		assertEquals(16, HealTable.foodHeal("Saradomin brew(1)", 99));
		assertEquals(12, HealTable.foodHeal("Saradomin brew(3)", 70));
		assertEquals(16, HealTable.foodBoost("Saradomin brew(2)", 99));
	}

	@Test
	public void anglerfishByLevel()
	{
		assertEquals(3, HealTable.foodHeal("Anglerfish", 10));
		assertEquals(4, HealTable.foodHeal("Anglerfish", 20));
		assertEquals(22, HealTable.foodHeal("Anglerfish", 93));
		assertEquals(22, HealTable.foodHeal("Anglerfish", 99));
		assertEquals(22, HealTable.foodBoost("Blighted anglerfish", 99));
	}

	@Test
	public void lifesteal()
	{
		assertEquals(23, HealTable.sanguinesti(47));
		assertEquals(25, HealTable.bloodSpell(100, 0, "Kodai wand"));
		assertEquals(35, HealTable.bloodSpell(100, 5, "Kodai wand"));
		assertEquals(38, HealTable.bloodSpell(100, 5, "Ancient sceptre"));
		assertEquals(52, HealTable.bloodSpell(100, 5, "Zuriel's staff"));
		assertEquals(9, HealTable.bloodBoost("Blood ancient sceptre", 99));
		assertEquals(0, HealTable.bloodBoost("Kodai wand", 99));
		assertTrue(HealTable.isMagicWeapon("Kodai wand"));
		assertTrue(HealTable.isMagicWeapon("Blood ancient sceptre"));
		assertFalse(HealTable.isMagicWeapon("Scythe of vitur"));
	}
}
