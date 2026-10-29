package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class RaidPartyTest
{
	private static final String TOB = "ToB";
	private static final String P1 = RaidParty.PREFIX + "tob 111";
	private static final String P2 = RaidParty.PREFIX + "tob 222";

	private static String tickUntilStable(RaidParty r, String name, String current)
	{
		String join = null;
		for (int i = 0; i < RaidParty.STABLE_TICKS; i++)
		{
			join = r.tick(name, current);
		}
		return join;
	}

	@Test
	public void everyoneGetsTheSameName()
	{
		String a = RaidParty.partyName(TOB, 302, Arrays.asList("Kurt Yoghurt", "Zezima", "", null));
		String b = RaidParty.partyName(TOB, 302, Arrays.asList("zezima", "Kurt Yoghurt"));
		assertEquals(a, b);
		assertTrue(a.startsWith("goldendps tob "));
	}

	@Test
	public void otherRaidWorldOrPeopleGiveAnotherName()
	{
		String base = RaidParty.partyName(TOB, 302, Arrays.asList("A", "B"));
		assertNotEquals(base, RaidParty.partyName("ToA", 302, Arrays.asList("A", "B")));
		assertNotEquals(base, RaidParty.partyName(TOB, 303, Arrays.asList("A", "B")));
		assertNotEquals(base, RaidParty.partyName(TOB, 302, Arrays.asList("A", "B", "C")));
	}

	@Test
	public void aloneIsNoParty()
	{
		assertNull(RaidParty.partyName(TOB, 302, Collections.singletonList("Kurt")));
		assertNull(RaidParty.partyName(TOB, 302, Collections.emptyList()));
	}

	@Test
	public void joinsAfterTheNamesAreStable()
	{
		RaidParty r = new RaidParty();
		for (int i = 1; i < RaidParty.STABLE_TICKS; i++)
		{
			assertNull(r.tick(P1, null));
		}
		assertEquals(P1, r.tick(P1, null));
		assertNull(r.tick(P1, P1));
	}

	@Test
	public void movesWhenTheRaidPartyChanges()
	{
		RaidParty r = new RaidParty();
		tickUntilStable(r, P1, null);
		assertEquals(P2, tickUntilStable(r, P2, P1));
	}

	@Test
	public void neverTouchesAManualParty()
	{
		RaidParty r = new RaidParty();
		for (int i = 0; i < 20; i++)
		{
			assertNull(r.tick(P1, "my friends"));
		}
	}

	@Test
	public void staysInThePartyAfterTheRaid()
	{
		RaidParty r = new RaidParty();
		tickUntilStable(r, P1, null);
		for (int i = 0; i < 50; i++)
		{
			assertNull(r.tick(null, P1));
		}
		assertTrue(RaidParty.isAuto(P1));
		assertFalse(RaidParty.isAuto("my friends"));
	}

	@Test
	public void coxUsesTheRaidPartyId()
	{
		String a = RaidParty.coxPartyName(302, 12345, 0);
		assertEquals(a, RaidParty.coxPartyName(302, 12345, 3));
		assertTrue(a.startsWith(RaidParty.PREFIX + "cox "));
		assertNotEquals(a, RaidParty.coxPartyName(303, 12345, 3));
		assertNotEquals(a, RaidParty.coxPartyName(302, 999, 3));
		assertNull(RaidParty.coxPartyName(302, -1, 0));
		assertNull(RaidParty.coxPartyName(302, 12345, 1));
	}
}
