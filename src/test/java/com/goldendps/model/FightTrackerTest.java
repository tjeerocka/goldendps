package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class FightTrackerTest
{
	private static final double D = 0.001;
	private static final String BOSS = "301:10";
	private static final String RAT = "301:20";

	private FightTracker tracker;
	private final List<Fight> ended = new ArrayList<>();

	@Before
	public void setUp()
	{
		tracker = new FightTracker();
		tracker.setIdleTimeoutMs(10_000);
		tracker.setListener(ended::add);
	}

	/** A hit by me, called in the same order as the plugin does it. */
	private void myHit(long t, String key, boolean boss, int amount)
	{
		String name = boss ? "Vorkath" : "Rat";
		tracker.playerHit(t, "Me", PlayerKind.LOCAL, key, 1, name, boss, amount, amount == 0);
		tracker.seenHit(t, key, 1, name, boss, amount);
	}

	private void partyHit(long t, String who, String key, boolean boss, int amount, boolean alsoSeen)
	{
		String name = boss ? "Vorkath" : "Rat";
		tracker.playerHit(t, who, PlayerKind.PARTY, key, 1, name, boss, amount, amount == 0);
		if (alsoSeen)
		{
			tracker.seenHit(t, key, 1, name, boss, amount);
		}
	}

	private FightReport report(Fight f, long now)
	{
		return FightReport.of(f, false, false, now);
	}

	@Test
	public void soloDamageDpsAndAccuracy()
	{
		myHit(0, RAT, false, 10);
		myHit(2400, RAT, false, 0);
		myHit(4800, RAT, false, 20);
		myHit(10_000, RAT, false, 30);

		FightReport r = report(tracker.getCurrent(), 10_000);
		FightReport.PlayerRow me = r.row("Me");
		assertEquals(60, me.damage);
		assertEquals(3, me.hits);
		assertEquals(1, me.misses);
		assertEquals(30, me.biggestHit);
		assertEquals(75.0, me.accuracy(), D);
		assertEquals(6.0, me.dps, D);
		assertEquals(100.0, me.percent, D);
		assertNull("solo: no unknown row", r.row(FightReport.UNKNOWN_NAME));
		assertEquals(PlayerKind.LOCAL, me.kind);
	}

	@Test
	public void partyDamageAndOthersUnknown()
	{
		myHit(0, BOSS, true, 30);
		// party member hit: I see the hitsplat and get the report
		partyHit(600, "Friend", BOSS, true, 20, true);
		// someone without the plugin: I only see the hitsplat
		tracker.seenHit(1200, BOSS, 1, "Vorkath", true, 50);

		FightReport r = report(tracker.getCurrent(), 10_000);
		assertEquals(100, r.getTotalDamage());
		assertEquals(30, r.row("Me").damage);
		assertEquals(20, r.row("Friend").damage);
		assertEquals(50, r.row(FightReport.UNKNOWN_NAME).damage);
		assertEquals(30.0, r.row("Me").percent, D);
		assertEquals(50.0, r.row(FightReport.UNKNOWN_NAME).percent, D);

		List<FightReport.PlayerRow> bars = r.sorted(MeterMode.DAMAGE);
		assertEquals(FightReport.UNKNOWN_NAME, bars.get(0).name);
		assertEquals("Me", bars.get(1).name);
		assertEquals("Friend", bars.get(2).name);
	}

	@Test
	public void partyHitOnTargetIDontSeeGivesNoNegativeUnknown()
	{
		myHit(0, RAT, false, 5);
		partyHit(600, "Friend", "301:99", false, 40, false);

		FightReport r = report(tracker.getCurrent(), 6000);
		assertEquals(45, r.getTotalDamage());
		assertNull(r.row(FightReport.UNKNOWN_NAME));
	}

	@Test
	public void poisonGetsItsOwnRow()
	{
		myHit(0, RAT, false, 10);
		tracker.effectHit(600, RAT, 1, "Rat", false, 6);

		FightReport r = report(tracker.getCurrent(), 6000);
		assertEquals(6, r.row(FightReport.EFFECTS_NAME).damage);
		assertNull(r.row(FightReport.UNKNOWN_NAME));
		assertEquals(16, r.getTotalDamage());
	}

	@Test
	public void effectsBeforeAFightAreIgnored()
	{
		tracker.effectHit(0, RAT, 1, "Rat", false, 6);
		assertNull(tracker.getCurrent());
	}

	@Test
	public void othersHittingNormalNpcDoNotStartAFight()
	{
		tracker.seenHit(0, RAT, 1, "Rat", false, 10);
		assertNull(tracker.getCurrent());
	}

	@Test
	public void strangersOnABossStartAFightButItIsThrownAwayWithoutOurDamage()
	{
		tracker.seenHit(0, BOSS, 1, "Vorkath", true, 10);
		assertNotNull(tracker.getCurrent());
		tracker.tick(20_000);
		assertNull(tracker.getCurrent());
		assertTrue(tracker.getHistory().isEmpty());
		assertTrue(ended.isEmpty());
	}

	@Test
	public void bossDeathEndsTheFight()
	{
		myHit(0, BOSS, true, 50);
		myHit(6000, BOSS, true, 50);
		tracker.npcDied(7200, BOSS, true);

		assertNull(tracker.getCurrent());
		assertEquals(1, ended.size());
		Fight f = ended.get(0);
		assertEquals(7200, f.endMs);
		assertEquals("Boss died", f.endReason);
		assertEquals(7200, f.durationMs(99_999));
		assertEquals("Vorkath", f.title());
	}

	@Test
	public void normalNpcDeathDoesNotEndTheFight()
	{
		myHit(0, RAT, false, 5);
		tracker.npcDied(600, RAT, false);
		assertNotNull(tracker.getCurrent());
	}

	@Test
	public void fightWithTwoBossesEndsWhenBothAreDead()
	{
		String other = "301:11";
		myHit(0, BOSS, true, 10);
		myHit(600, other, true, 10);
		tracker.npcDied(1200, BOSS, true);
		assertNotNull(tracker.getCurrent());
		tracker.npcDied(1800, other, true);
		assertNull(tracker.getCurrent());
	}

	@Test
	public void lateReportFromPartyGoesIntoTheEndedFight()
	{
		myHit(0, BOSS, true, 50);
		tracker.npcDied(3000, BOSS, true);
		// network delay: the friend's last hit arrives after the boss died
		partyHit(3500, "Friend", BOSS, true, 25, false);
		tracker.heal(3600, "Friend", PlayerKind.PARTY, HealType.FOOD, 18);

		assertNull("no new fight started", tracker.getCurrent());
		Fight f = tracker.getHistory().get(0);
		assertEquals(25, f.players.get("Friend").byTarget.get(BOSS).damage);
		assertEquals(18, f.players.get("Friend").healFood);
	}

	@Test
	public void veryLateReportStartsANewFight()
	{
		myHit(0, BOSS, true, 50);
		tracker.npcDied(3000, BOSS, true);
		partyHit(3000 + FightTracker.LATE_REPORT_MS + 1, "Friend", BOSS, true, 25, false);
		assertNotNull(tracker.getCurrent());
	}

	@Test
	public void idleTimeoutEndsAtTheLastHit()
	{
		myHit(0, RAT, false, 5);
		myHit(3000, RAT, false, 5);
		tracker.tick(12_999);
		assertNotNull(tracker.getCurrent());
		tracker.tick(13_000);
		assertNull(tracker.getCurrent());
		Fight f = tracker.getHistory().get(0);
		assertEquals(3000, f.endMs);
		assertTrue(f.endReason.startsWith("No hits"));
	}

	@Test
	public void bossOnlyModeHidesOtherTargets()
	{
		myHit(0, BOSS, true, 40);
		myHit(600, RAT, false, 10);
		tracker.seenHit(600, RAT, 1, "Rat", false, 7);

		Fight f = tracker.getCurrent();
		FightReport all = FightReport.of(f, false, false, 6000);
		FightReport boss = FightReport.of(f, true, false, 6000);
		assertEquals(57, all.getTotalDamage());
		assertEquals(40, boss.getTotalDamage());
		assertNull(boss.row(FightReport.UNKNOWN_NAME));
		assertEquals(1, boss.getTargets().size());
		assertEquals(2, all.getTargets().size());
	}

	@Test
	public void targetsAreSplitByName()
	{
		myHit(0, "301:1", false, 10);
		myHit(600, "301:2", false, 10); // second rat, same name
		myHit(1200, BOSS, true, 50);
		partyHit(1200, "Friend", BOSS, true, 25, true);

		FightReport r = report(tracker.getCurrent(), 6000);
		assertEquals(2, r.getTargets().size());
		FightReport.TargetRow vork = r.getTargets().get(0);
		assertEquals("Vorkath", vork.name);
		assertEquals(75, vork.total);
		assertEquals("Me", vork.byPlayer.keySet().iterator().next());
		FightReport.TargetRow rats = r.getTargets().get(1);
		assertEquals("Rat", rats.name);
		assertEquals(20, rats.total);
	}

	@Test
	public void damageTakenAndHealingOnlyCountDuringAFight()
	{
		tracker.damageTaken(0, "Me", PlayerKind.LOCAL, 10);
		tracker.heal(0, "Me", PlayerKind.LOCAL, HealType.FOOD, 20);
		assertNull(tracker.getCurrent());

		myHit(600, RAT, false, 5);
		tracker.damageTaken(1200, "Me", PlayerKind.LOCAL, 12);
		tracker.heal(1800, "Me", PlayerKind.LOCAL, HealType.FOOD, 20);
		tracker.heal(1800, "Me", PlayerKind.LOCAL, HealType.LIFESTEAL, 5);
		tracker.heal(2400, "Me", PlayerKind.LOCAL, HealType.REGEN, 1);
		tracker.heal(2400, "Me", PlayerKind.LOCAL, HealType.RECEIVED, 30);
		tracker.heal(3000, "Healer", PlayerKind.PARTY, HealType.HEAL_OTHER, 40);
		tracker.damageTaken(3000, "Healer", PlayerKind.PARTY, 3);

		Fight f = tracker.getCurrent();
		FightReport r = FightReport.of(f, false, false, 6000);
		assertEquals(12, r.row("Me").damageTaken);
		assertEquals(25, r.row("Me").healing);
		assertEquals(40, r.row("Healer").healing);
		assertEquals(26, FightReport.of(f, false, true, 6000).row("Me").healing);

		List<FightReport.PlayerRow> heals = r.sorted(MeterMode.HEALING);
		assertEquals("Healer", heals.get(0).name);
		assertEquals(2, heals.size());
		assertEquals(65.0, r.total(MeterMode.HEALING), D);
		assertEquals(15.0, r.total(MeterMode.DAMAGE_TAKEN), D);
	}

	@Test
	public void sessionTotalAddsFightsTogether()
	{
		myHit(0, BOSS, true, 50);
		tracker.npcDied(6000, BOSS, true);
		myHit(60_000, BOSS, true, 70);   // same NPC slot again (new kill)
		partyHit(61_000, "Friend", BOSS, true, 30, true);
		tracker.npcDied(66_000, BOSS, true);

		Fight total = tracker.getSessionTotal(70_000);
		assertNotNull(total);
		assertEquals(2, total.mergedFights);
		assertEquals(12_000, total.durationMs(70_000));
		FightReport r = report(total, 70_000);
		assertEquals(120, r.row("Me").damage);
		assertEquals(30, r.row("Friend").damage);
		assertEquals(150.0 / 12.0, r.getGroupDps(), D);
		assertEquals(1, r.getTargets().size());
		assertFalse(total.isActive());
	}

	@Test
	public void resetSessionStartsANewTotalButKeepsHistory()
	{
		myHit(0, RAT, false, 5);
		tracker.resetSession(1000);
		assertNull(tracker.getSessionTotal(2000));
		assertEquals(1, tracker.getHistory().size());

		myHit(5000, RAT, false, 7);
		assertEquals(7, report(tracker.getSessionTotal(6000), 6000).row("Me").damage);
	}

	@Test
	public void historyIsTrimmedNewestFirst()
	{
		tracker.setMaxHistory(2);
		for (int i = 0; i < 3; i++)
		{
			long t = i * 100_000L;
			myHit(t, RAT, false, i + 1);
			tracker.tick(t + 20_000);
		}
		assertEquals(2, tracker.getHistory().size());
		assertEquals(3, tracker.getHistory().get(0).players.get("Me").byTarget.get(RAT).damage);
		assertSame(tracker.getHistory().get(0), tracker.getDisplayFight());
	}

	@Test
	public void npcChangingIdKeepsOneTarget()
	{
		tracker.playerHit(0, "Me", PlayerKind.LOCAL, BOSS, 100, "Verzik Vitur", true, 10, false);
		tracker.playerHit(600, "Me", PlayerKind.LOCAL, BOSS, 101, "Verzik Vitur", true, 10, false);
		assertEquals(1, tracker.getCurrent().targets.size());
		assertEquals(101, tracker.getCurrent().targets.get(BOSS).npcId);
	}

	@Test
	public void guessedPlayerTakesDamageOutOfUnknown()
	{
		myHit(0, BOSS, true, 30);
		tracker.seenHit(600, BOSS, 1, "Vorkath", true, 40);
		tracker.estimateHit(600, "Bob", BOSS, 40, false);
		tracker.seenHit(1200, BOSS, 1, "Vorkath", true, 0);
		tracker.estimateHit(1200, "Bob", BOSS, 0, true);
		tracker.seenHit(1800, BOSS, 1, "Vorkath", true, 25); // nobody fits

		FightReport r = report(tracker.getCurrent(), 6000);
		FightReport.PlayerRow bob = r.row("Bob");
		assertEquals(PlayerKind.ESTIMATE, bob.kind);
		assertTrue(bob.isEstimate());
		assertEquals(40, bob.damage);
		assertEquals(1, bob.hits);
		assertEquals(1, bob.misses);
		assertEquals(25, r.row(FightReport.UNKNOWN_NAME).damage);
		assertEquals(95, r.getTotalDamage());
		assertEquals(Long.valueOf(40), r.getTargets().get(0).byPlayer.get("Bob"));
	}

	@Test
	public void seenDamageTakenOnOtherPlayers()
	{
		tracker.seenDamageTaken(0, "Bob", 10); // no fight yet
		myHit(0, RAT, false, 5);
		tracker.seenDamageTaken(600, "Bob", 12);
		tracker.seenDamageTaken(600, "Bob", 8);

		FightReport r = report(tracker.getCurrent(), 6000);
		assertEquals(20, r.row("Bob").damageTaken);
		assertEquals("Bob", r.sorted(MeterMode.DAMAGE_TAKEN).get(0).name);
		assertTrue("no healing data for guessed players", r.sorted(MeterMode.HEALING).isEmpty());
	}

	@Test
	public void partyReportsWinOverGuesses()
	{
		myHit(0, BOSS, true, 30);
		tracker.seenHit(600, BOSS, 1, "Vorkath", true, 40);
		tracker.estimateHit(600, "Friend", BOSS, 40, false);
		tracker.seenDamageTaken(600, "Friend", 9);
		// then Friend's own report arrives: they run GoldenDPS
		partyHit(900, "Friend", BOSS, true, 40, false);
		tracker.damageTaken(900, "Friend", PlayerKind.PARTY, 9);
		// later guesses for Friend are ignored
		tracker.estimateHit(1200, "Friend", BOSS, 99, false);

		FightReport r = report(tracker.getCurrent(), 6000);
		FightReport.PlayerRow f = r.row("Friend");
		assertEquals(PlayerKind.PARTY, f.kind);
		assertEquals(40, f.damage);
		assertEquals(9, f.damageTaken);
		assertEquals(70, r.getTotalDamage());
		assertNull(r.row(FightReport.UNKNOWN_NAME));
	}

	@Test
	public void guessesOnTargetsWeDidNotFightDoNotCount()
	{
		myHit(0, RAT, false, 5);
		tracker.seenHit(600, BOSS, 1, "Vorkath", true, 40);
		tracker.estimateHit(600, "Bob", BOSS, 40, false);

		FightReport r = report(tracker.getCurrent(), 6000);
		assertEquals(0, r.row("Bob").damage);
		assertEquals(5, r.getTotalDamage());
	}

	@Test
	public void guessesSurviveSessionMerge()
	{
		myHit(0, BOSS, true, 30);
		tracker.seenHit(600, BOSS, 1, "Vorkath", true, 40);
		tracker.estimateHit(600, "Bob", BOSS, 40, false);
		tracker.seenDamageTaken(600, "Bob", 7);
		tracker.npcDied(1200, BOSS, true);

		FightReport r = report(tracker.getSessionTotal(5000), 5000);
		assertEquals(40, r.row("Bob").damage);
		assertEquals(7, r.row("Bob").damageTaken);
	}

	@Test
	public void copyIsDeep()
	{
		myHit(0, RAT, false, 5);
		Fight c = tracker.getCurrent().copy();
		myHit(600, RAT, false, 5);
		assertEquals(5, c.players.get("Me").byTarget.get(RAT).damage);
		assertEquals(10, tracker.getCurrent().players.get("Me").byTarget.get(RAT).damage);
	}

	@Test
	public void veryShortFightUsesOneTickForDps()
	{
		myHit(0, RAT, false, 6);
		FightReport r = report(tracker.getCurrent(), 0);
		assertEquals(10.0, r.row("Me").dps, D);
	}
}
