package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class ReportFormatterTest
{
	private static final String BOSS = "301:5";

	/** Me 600, Friend 300, someone without the plugin 100, over 12 s (hits every 5 s). */
	private FightReport fight()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Kurt Yoghurt", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 300, false);
		t.seenHit(1000, BOSS, 1, "Vorkath", true, 300);
		t.playerHit(6000, "Kurt Yoghurt", PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 300, false);
		t.seenHit(6000, BOSS, 1, "Vorkath", true, 300);
		t.playerHit(6000, "Zezima", PlayerKind.PARTY, BOSS, 1, "Vorkath", true, 300, false);
		t.seenHit(6000, BOSS, 1, "Vorkath", true, 300);
		t.seenHit(6000, BOSS, 1, "Vorkath", true, 100);
		t.heal(6000, "Kurt Yoghurt", PlayerKind.LOCAL, HealType.FOOD, 60, 20);
		t.heal(6000, "Zezima", PlayerKind.PARTY, HealType.LIFESTEAL, 20);
		return FightReport.of(t.getCurrent(), false, 13_000);
	}

	@Test
	public void damageReport()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DAMAGE, "Vorkath", 5);
		assertEquals("Damage - Vorkath 0:12.00 - total 1,000 (83.3/s)", lines.get(0));
		assertEquals("1. Kurt Yoghurt 600 50.0/s 60%", lines.get(1));
		assertEquals("2. Zezima 300 25.0/s 30%", lines.get(2));
		assertEquals("3. Others (unknown) 100 8.3/s 10%", lines.get(3));
		assertEquals(4, lines.size());
	}

	@Test
	public void dpsReportPutsDpsFirst()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DPS, "Vorkath", 5);
		assertEquals("1. Kurt Yoghurt 50.0/s 600 60%", lines.get(1));
	}

	@Test
	public void healingReportHasOverhealing()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.HEALING, "Vorkath", 5);
		// group: 80 healed + 20 wasted = 20% OH
		assertEquals("Healing - Vorkath 0:12.00 - total 80 OH 20%", lines.get(0));
		assertEquals("1. Kurt Yoghurt 60 OH 25% 75%", lines.get(1));
		assertEquals("2. Zezima 20 25%", lines.get(2));
		assertEquals(3, lines.size());
	}

	@Test
	public void soloIsOneLine()
	{
		Fight f = new Fight("s", "s", 0);
		f.player("Me", PlayerKind.LOCAL).addHit("1:1", 100, false);
		f.target("1:1", 1, "Vorkath", true).engaged = true;
		f.activity(12_000);
		f.finish(12_000, "test");
		List<String> lines = ReportFormatter.lines(FightReport.of(f, false, 12_000), MeterMode.DAMAGE,
			"Boss damage", "Vorkath", 5);
		assertEquals(1, lines.size());
		assertEquals("Boss damage - Vorkath 0:12.00 - total 100 (8.3/s)", lines.get(0));
	}

	@Test
	public void burnDamageDoesNotMakeAGroup()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "1:1", 1, "Lesser demon", false, 40, false);
		t.effectHit(2000, "1:1", 1, "Lesser demon", false, 2);
		FightReport r = FightReport.of(t.getCurrent(), false, 3000);
		assertTrue(r.isSolo());
		assertEquals(1, ReportFormatter.lines(r, MeterMode.DAMAGE, "Damage", "Lesser demon", 5).size());
	}

	@Test
	public void theOverallReportGivesTheKills()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "1:1", 1, "Grizzly bear", false, 30, false);
		t.npcDied(2000, "1:1", false);
		t.playerHit(3000, "Me", PlayerKind.LOCAL, "1:2", 1, "Grizzly bear", false, 30, false);
		t.npcDied(4000, "1:2", false);
		FightReport r = FightReport.of(t.getCurrent(), false, 7000);
		assertEquals("Overall damage - Grizzly bear 0:06.00 - total 60 - 2 kills",
			ReportFormatter.lines(r, MeterMode.DAMAGE, "Overall damage", "Grizzly bear", 5, true).get(0));
	}

	@Test
	public void topLimitsThePlayers()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DAMAGE, "Vorkath", 1);
		assertEquals(2, lines.size());
	}

	@Test
	public void emptyFightIsOnlyTheHeader()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DAMAGE_TAKEN, "Session", 5);
		assertEquals(1, lines.size());
		assertEquals("Damage taken - Session 0:12.00", lines.get(0));
	}

	@Test
	public void linesFitInOnePartyMessage()
	{
		FightTracker t = new FightTracker();
		String longName = "A very long name that goes on and on and on and on and on and on and on and on and on and on and on and on";
		t.playerHit(1000, longName, PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 300, false);
		FightReport r = FightReport.of(t.getCurrent(), false, 11_000);
		for (String line : ReportFormatter.lines(r, MeterMode.DAMAGE, longName, 5))
		{
			assertTrue(line.length() <= ReportFormatter.MAX_LINE);
		}
	}

	@Test
	public void headerUsesTheReportName()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DAMAGE, "Boss damage", "Vorkath", 5);
		assertEquals("Boss damage - Vorkath 0:12.00 - total 1,000 (83.3/s)", lines.get(0));
	}
}
