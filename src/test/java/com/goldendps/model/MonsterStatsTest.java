package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class MonsterStatsTest
{
	private final FightTracker tracker = new FightTracker();
	private final List<Fight> ended = new ArrayList<>();

	{
		tracker.setListener(ended::add);
	}

	/** Kills one monster: 3 hits of {@code hit} and 1 miss with the bow, 1 hit with the spec, 2.4 s apart. */
	private Fight kill(long start, String key, String name, int hit)
	{
		long now = start;
		for (int i = 0; i < 3; i++, now += 2400)
		{
			tracker.playerHit(now, "Me", PlayerKind.LOCAL, key, 1, name, false, hit, false, "Bow");
		}
		tracker.playerHit(now, "Me", PlayerKind.LOCAL, key, 1, name, false, 0, true, "Bow");
		tracker.playerHit(now += 2400, "Me", PlayerKind.LOCAL, key, 1, name, false, 40, false, "Claws (spec)");
		tracker.playerHit(now, "Friend", PlayerKind.PARTY, key, 1, name, false, 99, false, "Sword");
		tracker.npcDied(now, key, false);
		tracker.endNow(now + 1000, "test");
		return ended.get(ended.size() - 1);
	}

	@Test
	public void addsUpYourKillsPerMonster()
	{
		MonsterStats stats = new MonsterStats();
		stats.add(kill(1000, "1:1", "Gargoyle", 10));
		stats.add(kill(100_000, "1:2", "Gargoyle", 20));
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
	public void raidBossesAndSessionTotalsAreNotCountedTwice()
	{
		MonsterStats stats = new MonsterStats();
		Fight f = kill(1000, "1:1", "Tekton", 10);
		Fight part = f.copy();
		part.partOf = "raid";
		stats.add(part);
		Fight total = f.copy();
		total.mergedFights = 2;
		stats.add(total);
		assertNull(stats.byName.get("Tekton"));
	}

	@Test
	public void entriesJoinMonstersAndPersonalBestsLastKillFirst()
	{
		MonsterStats stats = new MonsterStats();
		stats.add(kill(1000, "1:1", "Vorkath", 10));
		stats.add(kill(100_000, "1:2", "Gargoyle", 10));
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
