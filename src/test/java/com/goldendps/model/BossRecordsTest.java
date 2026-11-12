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
		BossRecords.Kill k = BossRecords.kill(vorkath(60, 50));
		assertNotNull(k);
		assertEquals("Vorkath", k.boss);
		assertEquals(60_000, k.timeMs);
		assertEquals(12 * 50 / 60.0, k.myDps, D);
	}

	@Test
	public void soloAndPartyKillsHaveTheirOwnRecords()
	{
		FightTracker t = new FightTracker();
		for (int s = 0; s < 30; s += 5)
		{
			t.playerHit(1000 + s * 1000L, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 50, false);
			t.playerHit(1000 + s * 1000L, "Friend", PlayerKind.PARTY, BOSS, 1, "Vorkath", true, 50, false);
		}
		t.npcDied(31_000, BOSS, true);
		BossRecords.Kill duo = BossRecords.kill(t.getHistory().get(0));
		assertTrue(duo.party);
		assertFalse(BossRecords.kill(vorkath(60, 50)).party);

		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50)));
		BossRecords.Compare c = r.add(duo);
		assertNull("the first party kill is compared with nothing", c.before);
		assertEquals(60_000, r.get("Vorkath", false).fastestMs);
		assertEquals(30_000, r.get("Vorkath", true).fastestMs);
		assertEquals("Vorkath (party)", r.get("Vorkath", true).label());
		assertEquals("Vorkath (solo)", r.get("Vorkath", false).label());
	}

	@Test
	public void paceSpreadsTheTimeOverTheHealthThatIsGone()
	{
		BossRecords.Record pb = new BossRecords.Record();
		pb.fastestMs = 60_000;
		assertEquals("half dead after 25 s: heading for 50 s", -10_000L, (long) BossRecords.paceMs(25_000, 0.5, pb));
		assertEquals(15_000L, (long) BossRecords.paceMs(30_000, 0.4, pb));
		assertNull("too early to tell", BossRecords.paceMs(2_000, 0.05, pb));
		assertNull("no PB yet", BossRecords.paceMs(25_000, 0.5, null));
		assertEquals("-0:10", BossRecords.paceText(-10_000));
		assertEquals("+0:15", BossRecords.paceText(15_000));
	}

	private static BossRecords.Kill crab(int damage)
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, BOSS, 1, "Gemstone Crab", true, damage, false);
		t.npcDied(600_000, BOSS, true);
		return BossRecords.kill(t.getHistory().get(0));
	}

	@Test
	public void theGemstoneCrabPbIsYourMostDamage()
	{
		BossRecords r = new BossRecords();
		r.add(crab(500));
		BossRecords.Compare less = r.add(crab(300));
		assertEquals("PB 500", BossRecords.meterTag(null, less));
		BossRecords.Compare more = r.add(crab(800));
		assertEquals("NEW PB!", BossRecords.meterTag(null, more));
		assertEquals(800, r.get("Gemstone Crab", false).mostDamage);
		assertEquals("no fastest time on a timer", 0, r.get("Gemstone Crab", false).fastestMs);
	}

	@Test
	public void aRecordCanBeRemoved()
	{
		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50)));
		r.remove("Vorkath", false);
		assertNull(r.get("Vorkath", false));
		assertTrue(r.list().isEmpty());
	}

	@Test
	public void noKillWithoutABossDeath()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 50, false);
		t.endNow(5000, "test");
		assertNull(BossRecords.kill(t.getHistory().get(0)));
		assertNull(BossRecords.kill(null));
	}

	@Test
	public void firstKillIsAPbAndLaterKillsCompare()
	{
		BossRecords r = new BossRecords();
		BossRecords.Compare first = r.add(BossRecords.kill(vorkath(60, 50)));
		assertNull(first.before);
		assertTrue(first.newFastest);
		assertTrue(first.newBestDps);

		// slower kill, less DPS
		BossRecords.Compare slower = r.add(BossRecords.kill(vorkath(72, 40)));
		assertFalse(slower.newFastest);
		assertFalse(slower.newBestDps);
		assertEquals(60_000, slower.before.fastestMs);
		assertTrue(slower.vsLastDpsPercent() < 0);

		// faster kill with more DPS
		BossRecords.Compare faster = r.add(BossRecords.kill(vorkath(48, 80)));
		assertTrue(faster.newFastest);
		assertTrue(faster.newBestDps);
		BossRecords.Record rec = r.get("Vorkath", false);
		assertEquals(3, rec.kills);
		assertEquals(48_000, rec.fastestMs);
	}

	@Test
	public void vsLastKillPercent()
	{
		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50)));   // 10 DPS
		BossRecords.Compare c = r.add(BossRecords.kill(vorkath(60, 55))); // 11 DPS
		assertEquals(10.0, c.vsLastDpsPercent(), D);
	}

	@Test
	public void seedFromSavedFights()
	{
		List<Fight> newestFirst = Arrays.asList(vorkath(48, 80), vorkath(60, 50));
		BossRecords r = new BossRecords();
		r.seed(newestFirst);
		BossRecords.Record rec = r.get("Vorkath", false);
		assertEquals(2, rec.kills);
		assertEquals(48_000, rec.fastestMs);
		// the newest kill is the last one
		assertEquals(80 * 10 / 48.0, rec.lastDps, D);
		assertEquals(Collections.singletonList("Vorkath"), Collections.singletonList(r.list().get(0).boss));
	}

	@Test
	public void survivesJsonAndCopyIsDeep()
	{
		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50)));
		BossRecords back = new Gson().fromJson(new Gson().toJson(r), BossRecords.class);
		assertEquals(60_000, back.get("Vorkath", false).fastestMs);

		BossRecords c = r.copy();
		r.add(BossRecords.kill(vorkath(42, 50)));
		assertEquals(60_000, c.get("Vorkath", false).fastestMs);
	}

	@Test
	public void textForMeterAndPanel()
	{
		BossRecords r = new BossRecords();
		BossRecords.Compare first = r.add(BossRecords.kill(vorkath(60, 50)));
		assertNull("no PB tag on the first kill", BossRecords.meterTag(r.get("Vorkath", false), first));
		assertEquals("first kill!", BossRecords.killLines(first).get(0)[1].replace("1:00.00 (", "").replace(")", ""));

		assertEquals("PB 1:00.00", BossRecords.meterTag(r.get("Vorkath", false), null));

		BossRecords.Compare slow = r.add(BossRecords.kill(vorkath(72, 40)));
		assertEquals("PB 1:00.00", BossRecords.meterTag(r.get("Vorkath", false), slow));
		assertEquals("1:12.00 (PB 1:00.00)", BossRecords.killLines(slow).get(0)[1]);

		BossRecords.Compare fast = r.add(BossRecords.kill(vorkath(48, 80)));
		assertEquals("NEW PB!", BossRecords.meterTag(r.get("Vorkath", false), fast));
		List<String[]> lines = BossRecords.killLines(fast);
		assertEquals("0:48.00 NEW PB! (was 1:00.00)", lines.get(0)[1]);
		assertTrue(lines.get(1)[1].contains("NEW BEST ("));
		assertEquals("vs last kill", lines.get(2)[0]);
		assertTrue(lines.get(2)[1].startsWith("+"));
		assertEquals("3", lines.get(3)[1]);
		assertTrue(BossRecords.killLines(null).isEmpty());
	}

	@Test
	public void aGameTimeAfterTheKillFixesThePb()
	{
		BossRecords r = new BossRecords();
		r.add(BossRecords.kill(vorkath(60, 50)));
		BossRecords.Compare c = r.add(BossRecords.kill(vorkath(72, 50)));
		assertFalse(c.newFastest);
		r.retime(c, 57_000);
		assertTrue(c.newFastest);
		assertEquals(57_000, c.kill.timeMs);
		assertEquals(57_000, r.get("Vorkath", false).fastestMs);
		r.retime(c, 61_200);
		assertFalse(c.newFastest);
		assertEquals("back to the old PB", 60_000, r.get("Vorkath", false).fastestMs);
	}
}
