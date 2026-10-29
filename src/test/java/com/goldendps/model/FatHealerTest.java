package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class FatHealerTest
{
	private static final double D = 0.0001;

	private FightReport report(FightTracker t)
	{
		return FightReport.of(t.getCurrent(), false, false, 10_000);
	}

	@Test
	public void mostHealingWins()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "301:5", 1, "Vorkath", true, 10, false);
		t.heal(2000, "Me", PlayerKind.LOCAL, HealType.FOOD, 40);
		t.heal(2000, "Healer", PlayerKind.PARTY, HealType.HEAL_OTHER, 90);
		t.heal(2000, "Healer", PlayerKind.PARTY, HealType.FOOD, 10, 50); // overheal does not count
		assertEquals("Healer", FatHealer.pick(report(t)));
	}

	@Test
	public void nobodyHealedMeansNobodyIsFat()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "301:5", 1, "Vorkath", true, 10, false);
		assertNull(FatHealer.pick(report(t)));
		assertNull(FatHealer.pick(null));
	}

	@Test
	public void guessedPlayersNeverGetFat()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "301:5", 1, "Vorkath", true, 10, false);
		t.seenDamageTaken(1000, "Stranger", 20);
		t.heal(2000, "Me", PlayerKind.LOCAL, HealType.FOOD, 5);
		assertEquals("Me", FatHealer.pick(report(t)));
	}

	@Test
	public void tieGoesToTheFirstName()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Zed", PlayerKind.LOCAL, "301:5", 1, "Vorkath", true, 10, false);
		t.heal(2000, "Zed", PlayerKind.LOCAL, HealType.FOOD, 20);
		t.heal(2000, "Amy", PlayerKind.PARTY, HealType.FOOD, 20);
		assertEquals("Amy", FatHealer.pick(report(t)));
	}

	@Test
	public void scaleAndGrow()
	{
		assertEquals(128, FatHealer.scale(0));
		assertEquals(173, FatHealer.scale(35));
		assertEquals(136, FatHealer.scale(6));
		assertEquals(128, FatHealer.scale(-5));
		assertEquals(1.5, FatHealer.approach(0, 35, 1.5), D);
		assertEquals(35, FatHealer.approach(34, 35, 1.5), D);
		assertEquals(33.5, FatHealer.approach(35, 0, 1.5), D);
	}
}
