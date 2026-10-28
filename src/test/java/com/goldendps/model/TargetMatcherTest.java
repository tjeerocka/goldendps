package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class TargetMatcherTest
{
	private TargetMatcher m;

	@Before
	public void setUp()
	{
		m = new TargetMatcher();
		// my Vorkath, NPC number 5 on world 301
		m.seen("301:5", 9000, 4000, 0);
	}

	@Test
	public void sameNpcSameKey()
	{
		assertEquals("301:5", m.partyKey(301, 301, 5, 9003, 4002, 0, "Bob"));
	}

	@Test
	public void sameNumberInAnotherInstanceIsSeparate()
	{
		// Bob's own Vorkath instance: same world, same NPC number, far away
		assertEquals("p:Bob:301:5", m.partyKey(301, 301, 5, 9640, 4000, 0, "Bob"));
	}

	@Test
	public void otherWorldIsSeparate()
	{
		assertEquals("p:Bob:302:5", m.partyKey(301, 302, 5, 9000, 4000, 0, "Bob"));
	}

	@Test
	public void otherFloorIsSeparate()
	{
		assertEquals("p:Bob:301:5", m.partyKey(301, 301, 5, 9000, 4000, 1, "Bob"));
	}

	@Test
	public void npcNeverSeenIsSeparate()
	{
		assertEquals("p:Bob:301:6", m.partyKey(301, 301, 6, 9000, 4000, 0, "Bob"));
	}

	@Test
	public void separateKeysDoNotChangeOthersUnknown()
	{
		FightTracker t = new FightTracker();
		t.playerHit(0, "Me", PlayerKind.LOCAL, "301:5", 1, "Vorkath", true, 30, false);
		t.seenHit(0, "301:5", 1, "Vorkath", true, 30);
		t.seenHit(600, "301:5", 1, "Vorkath", true, 20); // a stranger at my Vorkath
		// Bob hits his own Vorkath in another instance (same NPC number)
		String bobKey = m.partyKey(301, 301, 5, 9640, 4000, 0, "Bob");
		t.playerHit(600, "Bob", PlayerKind.PARTY, bobKey, 1, "Vorkath", true, 50, false);

		FightReport r = FightReport.of(t.getCurrent(), false, false, 6000);
		assertEquals(20, r.row(FightReport.UNKNOWN_NAME).damage);
		assertEquals(50, r.row("Bob").damage);
	}
}
