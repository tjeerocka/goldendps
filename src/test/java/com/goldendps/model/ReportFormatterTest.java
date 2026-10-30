package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import java.util.List;
import org.junit.Test;

public class ReportFormatterTest
{
	private static final String BOSS = "301:5";

	/** Me 600, Friend 300, someone without the plugin 100, over 10 s (hits every 5 s). */
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
		return FightReport.of(t.getCurrent(), false, false, 11_000);
	}

	@Test
	public void damageReport()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DAMAGE, "Vorkath", 5);
		assertEquals("Damage - Vorkath 0:10 - total 1,000 (100.0/s)", lines.get(0));
		assertEquals("1. Kurt Yoghurt 600 60.0/s 60%", lines.get(1));
		assertEquals("2. Zezima 300 30.0/s 30%", lines.get(2));
		assertEquals("3. Others (unknown) 100 10.0/s 10%", lines.get(3));
		assertEquals(4, lines.size());
	}

	@Test
	public void dpsReportPutsDpsFirst()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DPS, "Vorkath", 5);
		assertEquals("1. Kurt Yoghurt 60.0/s 600 60%", lines.get(1));
	}

	@Test
	public void healingReportHasOverhealing()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.HEALING, "Vorkath", 5);
		// group: 80 healed + 20 wasted = 20% OH
		assertEquals("Healing - Vorkath 0:10 - total 80 OH 20%", lines.get(0));
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
		f.activity(10_000);
		f.finish(10_000, "test");
		List<String> lines = ReportFormatter.lines(FightReport.of(f, false, false, 10_000), MeterMode.DAMAGE,
			"Boss damage", "Vorkath", 5);
		assertEquals(1, lines.size());
		assertEquals("Boss damage - Vorkath 0:10 - total 100 (10.0/s)", lines.get(0));
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
		assertEquals("Damage taken - Session 0:10", lines.get(0));
	}

	@Test
	public void linesFitInOnePartyMessage()
	{
		FightTracker t = new FightTracker();
		String longName = "A very long name that goes on and on and on and on and on and on and on and on and on and on and on and on";
		t.playerHit(1000, longName, PlayerKind.LOCAL, BOSS, 1, "Vorkath", true, 300, false);
		FightReport r = FightReport.of(t.getCurrent(), false, false, 11_000);
		for (String line : ReportFormatter.lines(r, MeterMode.DAMAGE, longName, 5))
		{
			assertTrue(line.length() <= ReportFormatter.MAX_LINE);
		}
	}

	@Test
	public void headerUsesTheReportName()
	{
		List<String> lines = ReportFormatter.lines(fight(), MeterMode.DAMAGE, "Boss damage", "Vorkath", 5);
		assertEquals("Boss damage - Vorkath 0:10 - total 1,000 (100.0/s)", lines.get(0));
	}
}
