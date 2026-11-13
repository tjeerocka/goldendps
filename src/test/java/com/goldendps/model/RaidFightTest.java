package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class RaidFightTest
{
	private static final String MAIDEN = "301:1";
	private static final String BLOAT = "301:2";
	private static final String CRAB = "301:3";
	private static final String TOB = "Theatre of Blood";

	private final List<Fight> ended = new ArrayList<>();

	private FightTracker tracker()
	{
		FightTracker t = new FightTracker();
		t.setListener(ended::add);
		return t;
	}

	private static void hit(FightTracker t, long ms, String key, String name, boolean boss, int amount)
	{
		t.playerHit(ms, "Me", PlayerKind.LOCAL, key, 1, name, boss, amount, false);
	}

	@Test
	public void aRaidRunsOnTheGameClockAndEndsWithItsLastHit()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		hit(t, 1000, MAIDEN, "The Maiden of Sugadinti", true, 50);
		hit(t, 121_000, BLOAT, "Pestilent Bloat", true, 50);
		t.endRaid(300_000, "Left the raid");
		Fight raid = ended.get(ended.size() - 1);
		assertEquals(TOB, raid.title());
		assertEquals("2 minutes between rooms count, looting after does not", 120_000, raid.durationMs(300_000));
	}

	@Test
	public void aRaidListsItsBossFightsInOrder()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		hit(t, 1000, MAIDEN, "The Maiden of Sugadinti", true, 50);
		t.npcDied(60_000, MAIDEN, true);
		hit(t, 90_000, BLOAT, "Pestilent Bloat", true, 50);
		Fight raid = t.getCurrent();
		List<Fight> parts = t.partsOf(raid);
		assertEquals(2, parts.size());
		assertEquals("The Maiden of Sugadinti", parts.get(0).title());
		assertSame("the running boss fight comes last", t.getBossPart(), parts.get(1));
	}

	@Test
	public void theRaidFightStartsWhenTheGameStartsTheRaid()
	{
		FightTracker t = tracker();
		t.startRaid(0, "Chambers of Xeric");
		t.raidBegan(5000);
		hit(t, 25_000, "301:7", "Tekton", true, 50);
		assertEquals(5000, t.getCurrent().startMs);
		assertEquals("from the game's start to the last hit", Fight.wholeTicks(20_000), t.getCurrent().durationMs(25_000));
	}

	@Test
	public void knowsWhenARaidIsGoingOn()
	{
		FightTracker t = tracker();
		assertFalse(t.isInRaid());
		t.startRaid(0, TOB);
		assertTrue(t.isInRaid());
		t.endRaid(1000, "test");
		assertFalse(t.isInRaid());
	}

	@Test
	public void theRaidAndItsRoomsTakeTheGameTimes()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		hit(t, 1000, MAIDEN, "The Maiden of Sugadinti", true, 50);
		t.gameTime(60_000, GameTimes.parse("Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: 1:02.40"));
		t.npcDied(61_000, MAIDEN, true);
		t.gameTime(62_000, GameTimes.parse("Theatre of Blood total completion time: 20:00.00"));
		Fight raid = t.getCurrent();
		assertEquals(62_400, t.partsOf(raid).get(0).bossDurationMs(0));
		assertEquals(1_200_000, raid.durationMs(0));
	}

	@Test
	public void olmIsOneKillOverAllItsPhases()
	{
		FightTracker t = tracker();
		t.startRaid(0, "Chambers of Xeric");
		hit(t, 1000, "301:7", "Great Olm (Left claw)", true, 50);
		t.npcDied(5000, "301:7", true);
		assertNull("a phase ends, Olm goes on", lastEnded(t));
		hit(t, 60_000, "301:8", "Great Olm (Right claw)", true, 50);
		t.npcDied(65_000, "301:8", true);
		hit(t, 90_000, "301:9", "Great Olm", true, 80);
		t.npcDied(95_000, "301:9", true);
		Fight olm = ended.get(ended.size() - 1);
		assertEquals("Great Olm", olm.title());
		assertNotNull(BossRecords.kill(olm));
		assertEquals(1, ended.stream().filter(f -> f.partOf != null).count());
	}

	@Test
	public void theVespulaPortalIsNamedAfterItsRoom()
	{
		assertEquals("Vespula portal", Fight.bossName("Abyssal portal"));
	}

	private Fight lastEnded(FightTracker t)
	{
		return ended.stream().filter(f -> f.partOf != null).findFirst().orElse(null);
	}

	@Test
	public void raidStartEndsTheOldFight()
	{
		FightTracker t = tracker();
		hit(t, 1000, CRAB, "Goblin", false, 5);
		t.startRaid(2000, TOB);
		assertNull(t.getCurrent());
		assertEquals("Raid started", t.getHistory().get(0).endReason);
	}

	@Test
	public void oneFightForTheWholeRaidAndOneForEachBoss()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		hit(t, 1000, MAIDEN, "The Maiden of Sugadinti", true, 50);
		hit(t, 2000, CRAB, "Nylocas Matomenos", false, 10);
		assertNotNull(t.getBossPart());
		t.npcDied(60_000, MAIDEN, true);

		// the raid goes on after the boss died, the boss has its own fight
		Fight raid = t.getCurrent();
		assertNotNull(raid);
		assertEquals(TOB, raid.title());
		assertNull(t.getBossPart());
		Fight maiden = t.getHistory().get(0);
		assertEquals(raid.id, maiden.partOf);
		assertEquals("a boss names the fight without +1", "The Maiden of Sugadinti", maiden.title());
		assertEquals(60, maiden.knownDamage());
		assertSame(maiden, ended.get(0));

		// 6 minutes later (walking, AFK): the raid is still running
		t.tick(400_000);
		assertNotNull(t.getCurrent());
		assertSame("boss meter: last boss of this raid", maiden, t.getBossDisplayFight());

		// next boss: a new boss fight
		hit(t, 420_000, BLOAT, "Pestilent Bloat", true, 40);
		assertEquals("Pestilent Bloat", t.getBossDisplayFight().title());
		t.npcDied(450_000, BLOAT, true);

		t.endRaid(500_000, "Left the raid");
		assertNull(t.getCurrent());
		assertNull(t.getRaid());
		Fight done = t.getHistory().get(0);
		assertEquals(TOB, done.title());
		assertEquals(100, done.knownDamage());
		assertEquals("Left the raid", done.endReason);
		assertSame("main meter shows the raid, not the last boss", done, t.getDisplayFight());
	}

	@Test
	public void sessionCountsRaidBossesOnce()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		hit(t, 1000, MAIDEN, "The Maiden of Sugadinti", true, 50);
		t.npcDied(5000, MAIDEN, true);
		t.endRaid(10_000, "done");
		Fight s = t.getSessionTotal(20_000);
		assertEquals(1, s.mergedFights);
		assertEquals(50, s.knownDamage());
	}

	@Test
	public void raidPbsComeFromTheBossFightsOnly()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		for (int ms = 1000; ms <= 31_000; ms += 5000)
		{
			hit(t, ms, MAIDEN, "The Maiden of Sugadinti", true, 50);
		}
		t.npcDied(31_000, MAIDEN, true);
		t.endRaid(40_000, "done");
		BossRecords.Kill boss = BossRecords.kill(ended.get(0));
		assertNotNull(boss);
		assertEquals(30_000, boss.timeMs);
		assertNull("the raid itself is no boss kill", BossRecords.kill(ended.get(1)));
	}

	@Test
	public void wipeEndsTheBossFightAfterTheIdleTime()
	{
		FightTracker t = tracker();
		t.setIdleTimeoutMs(300_000);
		t.startRaid(0, TOB);
		hit(t, 1000, MAIDEN, "The Maiden of Sugadinti", true, 50);
		t.tick(302_000);
		assertNull(t.getBossPart());
		assertNotNull("raid still runs", t.getCurrent());
		assertTrue(t.getHistory().get(0).endReason.startsWith("No hits"));
	}

	@Test
	public void bossFightWithOnlyStrangersHitsIsNotKept()
	{
		FightTracker t = tracker();
		t.startRaid(0, TOB);
		hit(t, 1000, CRAB, "Nylocas Matomenos", false, 10);
		t.seenHit(2000, MAIDEN, 1, "The Maiden of Sugadinti", true, 30);
		Fight bossPart = t.getBossPart();
		assertNotNull("a hit seen on a boss starts the boss fight", bossPart);
		assertEquals(30, bossPart.targets.get(MAIDEN).seenDamage);
		assertEquals("the raid fight sees it too", 30, t.getCurrent().targets.get(MAIDEN).seenDamage);

		t.endRaid(5000, "Left the raid");
		assertEquals("only the raid fight is kept", 1, ended.size());
		assertEquals(TOB, ended.get(0).title());
	}

	@Test
	public void outsideRaidsNothingChanges()
	{
		FightTracker t = tracker();
		hit(t, 1000, MAIDEN, "Vorkath", true, 50);
		assertNull(t.getBossPart());
		t.npcDied(2000, MAIDEN, true);
		assertNull(t.getCurrent());
		assertEquals(1, t.getHistory().size());
	}

	@Test
	public void monsterFightsCountTheOtherTargets()
	{
		FightTracker t = tracker();
		hit(t, 1000, CRAB, "Goblin", false, 5);
		hit(t, 2000, BLOAT, "Cow", false, 9);
		assertEquals("Cow +1", t.getCurrent().title());
	}
}
