package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MonsterStatsTest
{
	private final FightTracker tracker = new FightTracker();
	private final MonsterStats stats = new MonsterStats();

	/** Kills one monster as the plugin does: 3 hits and 1 miss with the bow, 1 spec hit, 2.4 s apart. */
	private void kill(long start, String key, String name, int hit)
	{
		long now = start;
		for (int i = 0; i < 3; i++, now += 2400)
		{
			tracker.playerHit(now, "Me", PlayerKind.LOCAL, key, 1, name, false, hit, false, "Bow");
		}
		tracker.playerHit(now, "Me", PlayerKind.LOCAL, key, 1, name, false, 0, true, "Bow");
		tracker.playerHit(now += 2400, "Me", PlayerKind.LOCAL, key, 1, name, false, 40, false, "Claws (spec)");
		tracker.playerHit(now, "Friend", PlayerKind.PARTY, key, 1, name, false, 99, false, "Sword");
		Fight fight = tracker.getCurrent();
		tracker.npcDied(now, key, false);
		stats.addKill(fight, key);
	}

	@Test
	public void countsEachKillTheMomentItDies()
	{
		kill(1000, "1:1", "Gargoyle", 10);
		assertEquals(1, stats.byName.get("Gargoyle").kills);
		kill(20_000, "1:2", "Gargoyle", 20);
		MonsterStats.Monster m = stats.byName.get("Gargoyle");

		assertEquals(2, m.kills);
		// first hit to death: four attacks 2.4 s apart
		assertEquals(9600, m.fastestMs);
		assertEquals(9600, m.averageMs());
		// only your own hits: the friend's 99 is left out
		assertEquals(40, m.attacks.biggestHit);
		assertEquals(8, m.attacks.hits);
		assertEquals(2, m.attacks.misses);
		assertEquals(30 + 60 + 80, m.attacks.damage);
		assertEquals(6, m.weapons.get("Bow").hits);
		assertEquals(2, m.weapons.get("Bow").misses);
		assertEquals(20, m.weapons.get("Bow").biggestHit);
		assertEquals(2, m.weapons.get("Claws (spec)").hits);
	}

	@Test
	public void aRespawnOnTheSameIndexCountsOnlyItsOwnHits()
	{
		kill(1000, "1:1", "Gargoyle", 10);
		// the same death reported again is no new kill
		stats.addKill(tracker.getCurrent(), "1:1");
		kill(20_000, "1:1", "Gargoyle", 20);
		MonsterStats.Monster m = stats.byName.get("Gargoyle");
		assertEquals(2, m.kills);
		assertEquals(8, m.attacks.hits);
		assertEquals(30 + 60 + 80, m.attacks.damage);
		assertEquals(6, m.weapons.get("Bow").hits);
		// the respawn's time starts at its own first hit
		assertEquals(2, m.timedKills);
		assertEquals(9600, m.averageMs());
	}

	@Test
	public void aMonsterYouDidNotHitIsNoKill()
	{
		tracker.playerHit(1000, "Friend", PlayerKind.PARTY, "1:1", 1, "Gargoyle", false, 50, false, "Sword");
		Fight fight = tracker.getCurrent();
		tracker.npcDied(2000, "1:1", false);
		stats.addKill(fight, "1:1");
		assertNull(stats.byName.get("Gargoyle"));
	}

	@Test
	public void entriesJoinMonstersAndPersonalBestsLastKillFirst()
	{
		kill(1000, "1:1", "Vorkath", 10);
		kill(100_000, "1:2", "Gargoyle", 10);
		BossRecords.Record solo = new BossRecords.Record();
		solo.boss = "Vorkath";
		solo.lastMs = 500;
		BossRecords.Record raid = new BossRecords.Record();
		raid.boss = "Chambers of Xeric";
		raid.party = true;
		raid.lastMs = 200_000;
		List<BossRecords.Record> records = new ArrayList<>();
		records.add(solo);
		records.add(raid);

		List<MonsterStats.Entry> entries = stats.entries(records);
		assertEquals(3, entries.size());
		assertEquals("Chambers of Xeric", entries.get(0).name);
		assertNull(entries.get(0).stats);
		assertEquals(raid, entries.get(0).party);
		assertEquals("Gargoyle", entries.get(1).name);
		assertEquals("Vorkath", entries.get(2).name);
		assertEquals(solo, entries.get(2).solo);
		assertEquals(1, entries.get(2).stats.kills);
		solo.kills = 143;
		assertEquals(143, entries.get(2).kills());
		assertEquals(1, entries.get(1).kills());
	}
}
