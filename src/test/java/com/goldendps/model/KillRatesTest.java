package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class KillRatesTest
{
	private static final double D = 0.01;

	/** A Vorkath kill from start to start + 60 s, hitting every 5 s. */
	private static void vorkath(FightTracker t, long start, String key)
	{
		for (long ms = start; ms < start + 60_000; ms += 5000)
		{
			t.playerHit(ms, "Me", PlayerKind.LOCAL, key, 1, "Vorkath", true, 50, false);
		}
		t.npcDied(start + 60_000, key, true);
	}

	@Test
	public void killsPerHourFromTheFirstStartUntilNow()
	{
		FightTracker t = new FightTracker();
		vorkath(t, 0, "1:1");
		vorkath(t, 120_000, "1:2");
		vorkath(t, 240_000, "1:3");
		List<KillRates.Rate> rates = KillRates.of(t.getSessionFights(), false, 300_000);
		assertEquals(1, rates.size());
		KillRates.Rate r = rates.get(0);
		assertEquals(KillRates.Kind.BOSS, r.kind);
		assertEquals("Vorkath", r.name);
		assertEquals(3, r.kills);
		// 3 kills in 300 s = 36 per hour
		assertEquals(36.0, r.perHour, D);
		assertTrue(r.averageMs >= 55_000 && r.averageMs <= 60_000);
	}

	@Test
	public void theRateDropsWhileYouDoSomethingElse()
	{
		FightTracker t = new FightTracker();
		vorkath(t, 0, "1:1");
		assertEquals(60.0, KillRates.of(t.getSessionFights(), false, 60_000).get(0).perHour, D);
		assertEquals(1.0, KillRates.of(t.getSessionFights(), false, 3_600_000).get(0).perHour, D);
	}

	@Test
	public void raidsCountCompletionsAndTheirBossesCountAsKills()
	{
		FightTracker t = new FightTracker();
		for (int run = 0; run < 2; run++)
		{
			long start = run * 1_800_000L;
			t.startRaid(start, "Theatre of Blood");
			vorkath(t, start + 1000, "1:" + run);
			t.endRaid(start + 900_000, "Left the raid");
		}
		Fight last = t.getHistory().get(0);
		List<KillRates.Rate> rates = KillRates.of(t.getSessionFights(), false, last.endMs);
		KillRates.Rate raid = rates.stream().filter(r -> r.kind == KillRates.Kind.RAID).findFirst().get();
		assertEquals(2, raid.kills);
		assertEquals("Theatre of Blood", raid.name);
		// 2 completions from the first raid start to the last hit of the second raid
		assertTrue("a raid ends with its last hit", last.endMs < 2_700_000);
		assertEquals(2 * 3_600_000.0 / (last.endMs - 1000), raid.perHour, D);
		assertEquals(2, rates.stream().filter(r -> r.kind == KillRates.Kind.BOSS).findFirst().get().kills);
	}

	@Test
	public void aNewSessionStartsFromZero()
	{
		FightTracker t = new FightTracker();
		vorkath(t, 0, "1:1");
		t.resetSession(70_000);
		assertTrue(KillRates.of(t.getSessionFights(), false, 80_000).isEmpty());
	}

	@Test
	public void monstersCountPerName()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "1:1", 2, "Cow", false, 8, false);
		t.npcDied(4000, "1:1", false);
		t.playerHit(5000, "Me", PlayerKind.LOCAL, "1:2", 2, "Cow", false, 8, false);
		t.npcDied(9000, "1:2", false);
		t.playerHit(9000, "Me", PlayerKind.LOCAL, "1:3", 3, "Goblin", false, 5, false);
		t.endNow(12_000, "test");
		List<KillRates.Rate> rates = KillRates.of(t.getSessionFights(), false, 9000);
		KillRates.Rate cow = rates.get(0);
		assertEquals("Cow", cow.name);
		assertEquals(KillRates.Kind.MONSTER, cow.kind);
		assertEquals(2, cow.kills);
		// 2 cows in 8 s = 900 per hour
		assertEquals(900.0, cow.perHour, D);
		assertEquals("the goblin did not die", 1, rates.size());
	}
}
