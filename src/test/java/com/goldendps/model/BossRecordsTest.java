package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class BossRecordsTest
{
	private static final double D = 0.001;
	private static final String BOSS = "301:5";

	/** A Vorkath kill: I hit every 5 s for 'hit' damage, boss dies at 1000 + seconds*1000. */
	private static Fight vorkath(int seconds, int hit)
	{
		FightTracker t = new FightTracker();
		long t0 = 1000;
		for (int s = 0; s < seconds; s += 5)
		{
			t.playerHit(t0 + s * 1000L, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, hit, false);
		}
		t.npcDied(t0 + seconds * 1000L, BOSS, true);
		return t.getHistory().get(0);
	}

	@Test
	public void killIsWorkedOutFromTheFight()
	{
		BossRecords.Kill k = BossRecords.kill(vorkath(60, 50), false);
		assertNotNull(k);
		assertEquals("Vorkath", k.boss);
		assertEquals(60_000, k.timeMs);
		assertEquals(12 * 50 / 60.0, k.myDps, D);
	}

	@Test
	public void noKillWithoutABossDeath()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 50, false);
		t.endNow(5000, "test");
		assertNull(BossRecords.kill(t.getHistory().get(0), false));
		assertNull(BossRecords.kill(null, false));
	}

	@Test
	public void firstKillIsAPbAndLaterKillsCompare()
	{
		BossRecords r = new BossRecords();
		BossRecords.Compare first = r.add(BossRecords.kill(vorkath(60, 50), false));
		assertNull(first.before);
		assertTrue(first.newFastest);
		assertTrue(first.newBestDps);

		// slower kill, less DPS
		BossRecords.Compare slower = r.add(BossRecords.kill(vorkath(70, 40), false));
		assertFalse(slower.newFastest);
		assertFalse(slower.newBestDps);
		assertEquals(60_000, slower.before.fastestMs);
		assertTrue(slower.vsLastDpsPercent() < 0);

		// faster kill with more DPS
		BossRecords.Compare faster = r.add(BossRecords.kill(vorkath(50, 80), false));
		assertTrue(faster.newFastest);
		assertTrue(faster.newBestDps);
		BossRecords.Record rec = r.get("Vorkath");
		assertEquals(3, rec.kills);
		assertEquals(50_000, rec.fastestMs);
	}

	@Test
	public void vsLastKillPercent()
	{
		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50), false));   // 10 DPS
		BossRecords.Compare c = r.add(BossRecords.kill(vorkath(60, 55), false)); // 11 DPS
		assertEquals(10.0, c.vsLastDpsPercent(), D);
	}

	@Test
	public void seedFromSavedFights()
	{
		List<Fight> newestFirst = Arrays.asList(vorkath(50, 80), vorkath(60, 50));
		BossRecords r = new BossRecords();
		r.seed(newestFirst, false);
		BossRecords.Record rec = r.get("Vorkath");
		assertEquals(2, rec.kills);
		assertEquals(50_000, rec.fastestMs);
		// the newest kill is the last one
		assertEquals(80 * 10 / 50.0, rec.lastDps, D);
		assertEquals(Collections.singletonList("Vorkath"), Collections.singletonList(r.list().get(0).boss));
	}

	@Test
	public void survivesJsonAndCopyIsDeep()
	{
		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50), false));
		BossRecords back = new Gson().fromJson(new Gson().toJson(r), BossRecords.class);
		assertEquals(60_000, back.get("Vorkath").fastestMs);

		BossRecords c = r.copy();
		r.add(BossRecords.kill(vorkath(40, 50), false));
		assertEquals(60_000, c.get("Vorkath").fastestMs);
	}

	@Test
	public void textForMeterAndPanel()
	{
		BossRecords r = new BossRecords();
		BossRecords.Compare first = r.add(BossRecords.kill(vorkath(60, 50), false));
		assertNull("no PB tag on the first kill", BossRecords.meterTag(r.get("Vorkath"), first));
		assertEquals("first kill!", BossRecords.killLines(first).get(0)[1].replace("1:00 (", "").replace(")", ""));

		assertEquals("PB 1:00", BossRecords.meterTag(r.get("Vorkath"), null));

		BossRecords.Compare slow = r.add(BossRecords.kill(vorkath(70, 40), false));
		assertEquals("PB 1:00", BossRecords.meterTag(r.get("Vorkath"), slow));
		assertEquals("1:10 (PB 1:00)", BossRecords.killLines(slow).get(0)[1]);

		BossRecords.Compare fast = r.add(BossRecords.kill(vorkath(50, 80), false));
		assertEquals("NEW PB!", BossRecords.meterTag(r.get("Vorkath"), fast));
		List<String[]> lines = BossRecords.killLines(fast);
		assertEquals("0:50 NEW PB! (was 1:00)", lines.get(0)[1]);
		assertTrue(lines.get(1)[1].contains("NEW BEST!"));
		assertEquals("vs last kill", lines.get(2)[0]);
		assertTrue(lines.get(2)[1].startsWith("+"));
		assertEquals("3", lines.get(3)[1]);
		assertTrue(BossRecords.killLines(null).isEmpty());
	}
}
