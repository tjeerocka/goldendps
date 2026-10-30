package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class BarrowsRunTest
{
	private static final String DHAROK = "Dharok the Wretched";
	private static final String VERAC = "Verac the Defiled";
	private static final String RAT = "Giant crypt rat";
	private static final long MIN = 60_000;

	private static void hit(FightTracker t, BarrowsRun run, long ms, String key, String name, boolean boss)
	{
		t.playerHit(ms, "Me", PlayerKind.LOCAL, key, 1, name, boss, 30, false);
		run.hit(ms, name);
	}

	private static void kill(FightTracker t, BarrowsRun run, long ms, String key, String name, boolean boss)
	{
		t.npcDied(ms, key, boss);
		run.died(name);
	}

	private static Fight addRun(FightTracker t, BarrowsRun.Result r)
	{
		return t.addRun(BarrowsRun.NAME, r.startMs, r.endMs, r.activeMs, x -> BarrowsRun.isBrother(x.name),
			"Chest opened");
	}

	@Test
	public void aRunStartsAtTheFirstBrotherHitNotAtTunnelMonsters()
	{
		BarrowsRun run = new BarrowsRun();
		run.hit(1000, RAT);
		assertFalse(run.isRunning());
		run.hit(2000, DHAROK);
		assertTrue(run.isRunning());
		run.hit(5000, VERAC);
		BarrowsRun.Result r = run.chestOpened(9000);
		assertEquals(2000, r.startMs);
		assertEquals(7000, r.activeMs);
		assertFalse(run.isRunning());
		assertNull("no run, no chest", run.chestOpened(10_000));
	}

	@Test
	public void hittingABrotherYouAlreadyKilledStartsANewRun()
	{
		BarrowsRun run = new BarrowsRun();
		run.hit(1000, DHAROK);
		run.died(DHAROK);
		run.hit(600_000, DHAROK);
		assertEquals(600_000, run.chestOpened(700_000).startMs);
	}

	@Test
	public void bankingCountsButLoggedOutTimeDoesNot()
	{
		BarrowsRun run = new BarrowsRun();
		run.hit(0, DHAROK);
		run.loggedOut(3 * MIN);
		run.hit(61 * MIN, VERAC);      // an hour away: the clock starts again at the next brother hit
		assertEquals("3 minutes before logout, 2 from the next hit", 5 * MIN, run.chestOpened(63 * MIN).activeMs);
	}

	@Test
	public void theClockStopsTenMinutesAfterTheLastBrotherHit()
	{
		BarrowsRun run = new BarrowsRun();
		run.hit(0, DHAROK);
		run.hit(30 * MIN, VERAC);      // 30 minutes AFK: only 10 count
		assertEquals(10 * MIN + 2 * MIN, run.chestOpened(32 * MIN).activeMs);
	}

	@Test
	public void theLiveTimeStopsWhenIdle()
	{
		BarrowsRun run = new BarrowsRun();
		run.hit(0, DHAROK);
		assertEquals(5 * MIN, run.activeMs(5 * MIN));
		assertFalse(run.isPaused(5 * MIN));
		assertEquals(10 * MIN, run.activeMs(15 * MIN));
		assertTrue(run.isPaused(15 * MIN));
	}

	@Test
	public void aSavedRunGoesOnAfterARestart()
	{
		BarrowsRun run = new BarrowsRun();
		assertTrue("the first brother hit starts a run", run.hit(0, DHAROK));
		assertFalse(run.hit(MIN, DHAROK));
		run.died(DHAROK);
		BarrowsRun.Saved saved = run.save(4 * MIN);    // then the client crashes

		BarrowsRun after = new BarrowsRun();
		after.restore(saved);
		assertTrue(after.isRunning());
		assertTrue("stopped until the next brother hit", after.isPaused(30 * MIN));
		assertEquals(4 * MIN, after.activeMs(30 * MIN));
		after.hit(31 * MIN, VERAC);
		assertEquals(4 * MIN + MIN, after.chestOpened(32 * MIN).activeMs);

		BarrowsRun again = new BarrowsRun();
		again.restore(saved);
		assertTrue("a killed brother is remembered: hitting him starts a new run", again.hit(40 * MIN, DHAROK));
		assertNull("no saved run after the chest", after.save(33 * MIN));
	}

	@Test
	public void theRunCountsOnlyDamageOnTheBrothers()
	{
		FightTracker t = new FightTracker();
		BarrowsRun run = new BarrowsRun();
		hit(t, run, 10_000, "1:1", DHAROK, true);
		hit(t, run, 12_000, "1:9", RAT, false);   // a rat in the brother fight
		kill(t, run, 40_000, "1:1", DHAROK, true);
		// tunnel and bank: 5 minutes without brother hits
		hit(t, run, 200_000, "1:2", RAT, false);
		kill(t, run, 205_000, "1:2", RAT, false);
		hit(t, run, 340_000, "1:3", VERAC, true);
		kill(t, run, 370_000, "1:3", VERAC, true);

		Fight r = addRun(t, run.chestOpened(380_000));
		assertEquals(BarrowsRun.NAME, r.title());
		assertEquals("first brother hit to chest, banking included", 370_000, r.durationMs(380_000));
		assertEquals("brothers only", 60, FightReport.of(r, false, false, 380_000).total(MeterMode.DAMAGE), 0.001);
		assertTrue(r.isCompleteRaid());
		assertFalse("no boss meter for the run", r.hasBossMeter());
		assertEquals("the session total counts every fight once", 120,
			FightReport.of(t.getSessionTotal(380_000), false, false, 380_000).total(MeterMode.DAMAGE), 0.001);

		assertNull("two brothers is no full run: no personal best", BossRecords.record(r, false));
		assertNull("no boss report for the run", BossRecords.kill(r, false));

		List<KillRates.Rate> rates = KillRates.of(t.getSessionFights(), false, 380_000);
		assertEquals(1, rates.stream().filter(x -> x.kind == KillRates.Kind.RUN && x.kills == 1).count());
		assertEquals("brothers are in chests per hour, not in bosses", 0,
			rates.stream().filter(x -> x.kind == KillRates.Kind.BOSS).count());
		assertEquals("the dead rat is counted once", 1, rates.stream()
			.filter(x -> x.kind == KillRates.Kind.MONSTER).mapToInt(x -> x.kills).sum());
	}

	@Test
	public void aBrotherInAFightThatStartedWithARatCounts()
	{
		FightTracker t = new FightTracker();
		BarrowsRun run = new BarrowsRun();
		hit(t, run, 1000, "1:9", RAT, false);
		kill(t, run, 3000, "1:9", RAT, false);
		hit(t, run, 10_000, "1:1", DHAROK, true);
		kill(t, run, 20_000, "1:1", DHAROK, true);
		Fight r = addRun(t, run.chestOpened(30_000));
		assertEquals(30, FightReport.of(r, false, false, 30_000).total(MeterMode.DAMAGE), 0.001);
	}

	@Test
	public void brothersWithTheSameNpcIndexStayApart()
	{
		FightTracker t = new FightTracker();
		BarrowsRun run = new BarrowsRun();
		hit(t, run, 1000, "1:5", DHAROK, true);
		kill(t, run, 2000, "1:5", DHAROK, true);
		hit(t, run, 60_000, "1:5", VERAC, true);
		kill(t, run, 61_000, "1:5", VERAC, true);
		Fight r = addRun(t, run.chestOpened(70_000));
		assertEquals(2, r.targets.size());
		assertEquals(2, FightReport.of(r, false, false, 70_000).getTargets().size());
	}

	@Test
	public void onlyAFullRunSetsAPersonalBest()
	{
		String[] brothers = {"Ahrim the Blighted", DHAROK, "Guthan the Infested", "Karil the Tainted",
			"Torag the Corrupted", VERAC};
		FightTracker t = new FightTracker();
		BarrowsRun run = new BarrowsRun();
		for (int i = 0; i < brothers.length; i++)
		{
			hit(t, run, MIN * i, "1:" + i, brothers[i], true);
			kill(t, run, MIN * i + 20_000, "1:" + i, brothers[i], true);
		}
		Fight r = addRun(t, run.chestOpened(6 * MIN));
		assertTrue(BarrowsRun.isFullRun(r));
		BossRecords.Kill record = BossRecords.record(r, false);
		assertEquals(BarrowsRun.NAME, record.boss);
		assertEquals(6 * MIN, record.timeMs);
	}

	@Test
	public void idleTimeIsLeftOutOfTheRunTime()
	{
		FightTracker t = new FightTracker();
		BarrowsRun run = new BarrowsRun();
		hit(t, run, 0, "1:1", DHAROK, true);
		kill(t, run, MIN, "1:1", DHAROK, true);
		hit(t, run, 40 * MIN, "1:3", VERAC, true);
		kill(t, run, 41 * MIN, "1:3", VERAC, true);
		Fight r = addRun(t, run.chestOpened(42 * MIN));
		assertEquals("10 idle minutes count, then from the next hit", 12 * MIN, r.durationMs(42 * MIN));
	}
}
