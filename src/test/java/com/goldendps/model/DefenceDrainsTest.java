package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;

public class DefenceDrainsTest
{
	private static final int VORKATH = 8059;
	private static final int SIRE = 5886;
	private static final String KEY = "1:5";

	private DefenceDrains drains;
	private int tick;

	@Before
	public void setUp()
	{
		NpcStats stats = new NpcStats(Arrays.asList(
			"# id\tname\tdefence",
			"8058\tVorkath\t164\t150\t150\t66\t126\t126\t204\t80\t80\t80\tdragon,undead\t\t\t0",
			VORKATH + "\tVorkath\t214\t150\t150\t26\t108\t108\t240\t26\t26\t26\tdragon,undead\t\t\t1",
			SIRE + "\tAbyssal Sire\t250\t200\t0\t40\t60\t50\t20\t60\t60\t60\tdemon\t\t\t1"));
		drains = new DefenceDrains(stats);
	}

	private void spec(int npcId, String player, String weapon, int damage)
	{
		drains.hit(tick, KEY, npcId, npcId == VORKATH ? "Vorkath" : "Abyssal Sire", player, weapon + " (spec)", damage,
			damage <= 0);
	}

	private int defence()
	{
		return drains.get(KEY).getCurrent();
	}

	@Test
	public void warhammerTakes30PercentOfTheCurrentDefenceEachTime()
	{
		spec(VORKATH, "Me", "Dragon warhammer", 40);
		assertEquals("214 - 64", 150, defence());
		spec(VORKATH, "Friend", "Dragon warhammer", 35);
		assertEquals("150 - 45", 105, defence());
		assertEquals("DWH x2 -109", drains.get(KEY).summary());
		assertEquals("Friend", drains.get(KEY).getDrains().get(1).player);
	}

	@Test
	public void aMissedWarhammerDoesNothing()
	{
		spec(VORKATH, "Me", "Dragon warhammer", 0);
		assertNull(drains.get(KEY));
	}

	@Test
	public void elderMaulTakes35Percent()
	{
		spec(VORKATH, "Me", "Elder maul", 50);
		assertEquals("214 - 74", 140, defence());
		assertEquals("Elder maul -74", drains.get(KEY).summary());
	}

	@Test
	public void bandosGodswordTakesTheDamageButNotBelowZero()
	{
		spec(VORKATH, "Me", "Bandos godsword", 60);
		assertEquals(154, defence());
		spec(VORKATH, "Me", "Bandos godsword (or)", 200);
		assertEquals(0, defence());
		assertEquals("BGS x2 -214", drains.get(KEY).summary());
	}

	@Test
	public void summaryPerWeaponInTheOrderTheyHit()
	{
		spec(VORKATH, "Me", "Dragon warhammer", 40);
		spec(VORKATH, "Me", "Bandos godsword", 30);
		spec(VORKATH, "Friend", "Dragon warhammer", 40);
		assertEquals("64 + 36 (30% of 120)", "DWH x2 -100, BGS -30", drains.get(KEY).summary());
	}

	@Test
	public void arclightAndEmberlightTakeMoreFromDemons()
	{
		spec(VORKATH, "Me", "Arclight", 20);
		assertEquals("5% of 214 + 1", 214 - 11, defence());
		drains.clear();
		spec(SIRE, "Me", "Arclight", 20);
		assertEquals("10% of 250 + 1", 250 - 26, defence());
		drains.clear();
		spec(SIRE, "Me", "Emberlight", 20);
		assertEquals("15% of 250 + 1", 250 - 38, defence());
	}

	@Test
	public void accursedSceptreDoesNotStack()
	{
		spec(SIRE, "Me", "Accursed sceptre", 30);
		assertEquals(213, defence());
		spec(SIRE, "Me", "Accursed sceptre", 30);
		assertEquals(213, defence());
		assertEquals("magic 200 - 15%", 170, drains.get(KEY).getMagicCurrent());
		assertEquals("one defence and one magic drain", 2, drains.get(KEY).getDrains().size());
		assertEquals("Accursed sceptre -37, Accursed sceptre -30 magic", drains.get(KEY).summary());
	}

	@Test
	public void boneDaggerOnlyWhenTheDefenceIsNotLowered()
	{
		spec(VORKATH, "Me", "Bone dagger (p++)", 20);
		assertEquals(194, defence());
		spec(VORKATH, "Me", "Bone dagger (p++)", 20);
		assertEquals(194, defence());
	}

	@Test
	public void normalHitsAndUnknownMonstersAreIgnored()
	{
		drains.hit(0, KEY, VORKATH, "Vorkath", "Me", "Dragon warhammer", 40, false);
		drains.hit(0, KEY, VORKATH, "Vorkath", "Me", null, 40, false);
		drains.hit(0, KEY, 99999, "Unknown thing", "Me", "Dragon warhammer (spec)", 40, false);
		assertNull(drains.get(KEY));
	}

	@Test
	public void goneNpcsAreForgotten()
	{
		spec(VORKATH, "Me", "Dragon warhammer", 40);
		drains.remove(KEY);
		assertNull(drains.get(KEY));
	}

	@Test
	public void theStatsFileHasBossesAndMonsters() throws Exception
	{
		NpcStats stats = NpcStats.load();
		assertEquals("Abyssal demon", 135, stats.get(415, "Abyssal demon").defence);
		assertEquals(214, stats.get(VORKATH, "Vorkath").defence);
		assertEquals("an id the wiki does not list", 214, stats.get(8061, "Vorkath").defence);
		assertEquals("the quest version", 164, stats.get(8058, "Vorkath").defence);
		assertNull(stats.get(99999, "Unknown thing"));
	}

	@Test
	public void seercullLowersTheMagicLevelOnce()
	{
		spec(SIRE, "Me", "Seercull", 40);
		assertEquals(160, drains.get(KEY).getMagicCurrent());
		assertEquals("defence stays", 250, defence());
		spec(SIRE, "Me", "Seercull", 40);
		assertEquals(160, drains.get(KEY).getMagicCurrent());
		assertEquals("Seercull -40 magic", drains.get(KEY).summary());
	}

	@Test
	public void dorgeshuunCrossbowAndBarrelchestAnchor()
	{
		spec(VORKATH, "Me", "Dorgeshuun crossbow", 20);
		assertEquals(194, defence());
		spec(VORKATH, "Me", "Dorgeshuun crossbow", 20);
		assertEquals("only when not lowered yet", 194, defence());
		spec(VORKATH, "Me", "Barrelchest anchor", 47);
		assertEquals("10% of 47", 190, defence());
	}

	@Test
	public void drainedStatsComeBackOneLevelAMinute()
	{
		tick = 1000;
		spec(VORKATH, "Me", "Dragon warhammer", 40);
		assertEquals(150, defence());
		drains.restore(1099);
		assertEquals(150, defence());
		drains.restore(1100);
		assertEquals(151, defence());
		tick = 1150;
		spec(VORKATH, "Me", "Bandos godsword", 10);
		assertEquals(141, defence());
		drains.restore(1350);
		assertEquals("the timer keeps going from the first drain: +1 at 1200 and 1300", 143, defence());
	}

	@Test
	public void aFullyRestoredMonsterIsForgotten()
	{
		spec(SIRE, "Me", "Bandos godsword", 2);
		drains.restore(DefenceDrains.RESTORE_TICKS);
		assertEquals(249, defence());
		drains.restore(2 * DefenceDrains.RESTORE_TICKS);
		assertNull(drains.get(KEY));
	}
}
