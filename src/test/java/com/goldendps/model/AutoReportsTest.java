package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.Collections;
import org.junit.Test;

public class AutoReportsTest
{
	private static Fight kill(String boss)
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "1:1", 1, boss, true, 50, false);
		t.npcDied(5000, "1:1", true);
		return t.getHistory().get(0);
	}

	@Test
	public void aBossKillIsReportedOnceAfterTheDelay()
	{
		AutoReports r = new AutoReports();
		Fight f = kill("Vorkath");
		r.fightEnded(f, 100, false);
		assertTrue(r.due(100 + AutoReports.DELAY_TICKS - 1).isEmpty());
		assertEquals(Collections.singletonList(f), r.due(100 + AutoReports.DELAY_TICKS));
		assertTrue("only once", r.due(200).isEmpty());
	}

	@Test
	public void barrowsReportsTheRunNotTheBrothers()
	{
		AutoReports r = new AutoReports();
		Fight brother = kill("Verac the Defiled");
		r.fightEnded(brother, 100, false);
		FightTracker t = new FightTracker();
		Fight run = t.addRun(BarrowsRun.NAME, 0, 10_000, 10_000, x -> true, "Chest opened");
		r.fightEnded(run, 101, false);
		assertEquals("the run, not the brother", Collections.singletonList(run), r.due(200));
	}

	@Test
	public void aFightWithoutABossKillIsNotReported()
	{
		AutoReports r = new AutoReports();
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "1:1", 1, "Goblin", false, 5, false);
		t.npcDied(2000, "1:1", false);
		t.endNow(3000, "test");
		r.fightEnded(t.getHistory().get(0), 100, false);
		assertTrue(r.due(200).isEmpty());
	}
}
