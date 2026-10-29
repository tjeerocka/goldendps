package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.util.List;
import org.junit.Test;

public class SourceBreakdownTest
{
	private static final double D = 0.001;
	private static final String BOSS = "301:5";
	private static final String RAT = "301:9";

	private static FightReport.PlayerRow me(FightReport r)
	{
		for (FightReport.PlayerRow row : r.getRows())
		{
			if ("Me".equals(row.name))
			{
				return row;
			}
		}
		throw new AssertionError("no row");
	}

	private static FightTracker fight()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, RAT, 2, "Rat", false, 10, false, "Abyssal whip");
		t.playerHit(2000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 30, false, "Abyssal whip");
		t.playerHit(3000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 0, true, "Abyssal whip");
		t.playerHit(4000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 40, false, "Dragon claws (spec)");
		t.playerHit(4000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 20, false, "Dragon claws (spec)");
		t.playerHit(4000, "Friend", PlayerKind.PARTY, BOSS, 1, "Vorkath", true, 25, false, null);
		return t;
	}

	@Test
	public void breakdownPerWeaponBiggestFirst()
	{
		FightReport r = FightReport.of(fight().getCurrent(), false, false, 5000);
		List<FightReport.SourceRow> s = me(r).sources;
		assertEquals(2, s.size());
		assertEquals("Dragon claws (spec)", s.get(0).name);
		assertEquals(60, s.get(0).damage);
		assertEquals(2, s.get(0).hits);
		assertEquals(40, s.get(0).biggestHit);
		assertEquals("Abyssal whip", s.get(1).name);
		assertEquals(40, s.get(1).damage);
		assertEquals(1, s.get(1).misses);
		assertEquals(40 / 3.0, s.get(1).average(), D);
		assertEquals(60.0, s.get(0).percent, D);
	}

	@Test
	public void bossOnlyLeavesOutTrash()
	{
		FightReport r = FightReport.of(fight().getCurrent(), true, false, 5000);
		List<FightReport.SourceRow> s = me(r).sources;
		assertEquals(30, s.get(1).damage);
		assertEquals(100.0, s.get(0).percent + s.get(1).percent, D);
	}

	@Test
	public void playersWithoutSourcesHaveNoBreakdown()
	{
		FightReport r = FightReport.of(fight().getCurrent(), false, false, 5000);
		for (FightReport.PlayerRow row : r.getRows())
		{
			if ("Friend".equals(row.name))
			{
				assertTrue(row.sources.isEmpty());
			}
		}
	}

	@Test
	public void sessionTotalAndSavingKeepTheBreakdown()
	{
		FightTracker t = fight();
		t.endNow(5000, "test");
		t.playerHit(9000, "Me", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 50, false, "Abyssal whip");
		t.endNow(10000, "test");
		FightReport session = FightReport.of(t.getSessionTotal(11000), false, false, 11000);
		assertEquals(90, me(session).sources.get(0).damage);

		Gson gson = new Gson();
		Fight back = gson.fromJson(gson.toJson(t.getHistory().get(1)), Fight.class);
		assertEquals(2, me(FightReport.of(back, false, false, 5000)).sources.size());

		// old save files have no sources at all
		PlayerStats old = gson.fromJson("{\"name\":\"Me\",\"kind\":\"LOCAL\",\"bySource\":null}", PlayerStats.class);
		assertTrue(old.copy().sources().isEmpty());
	}
}
