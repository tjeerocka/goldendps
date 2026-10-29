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
		RaidParty.Action action = RaidParty.Action.NONE;
		for (int i = 0; i < RaidParty.STABLE_TICKS; i++)
		{
			action = r.tick(name, current);
		}
		return action.offer;
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
	public void offersOnceTheNamesAreStable()
	{
		RaidParty r = new RaidParty();
		for (int i = 1; i < RaidParty.STABLE_TICKS; i++)
		{
			assertNull(r.tick(P1, null).offer);
		}
		assertEquals(P1, r.tick(P1, null).offer);
		assertNull(r.tick(P1, P1).offer);
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
			assertNull(r.tick(P1, "my friends").offer);
			assertFalse(r.tick(null, "my friends").leave);
		}
	}

	@Test
	public void leavesOnceAfterTheRaid()
	{
		RaidParty r = new RaidParty();
		tickUntilStable(r, P1, null);
		for (int i = 1; i < RaidParty.LEAVE_TICKS; i++)
		{
			assertFalse(r.tick(null, P1).leave);
		}
		assertTrue(r.tick(null, P1).leave);
		assertFalse("only once", r.tick(null, P1).leave);
	}

	@Test
	public void shortGapsWhileLoadingDoNotLeave()
	{
		RaidParty r = new RaidParty();
		tickUntilStable(r, P1, null);
		for (int i = 0; i < 30; i++)
		{
			assertFalse(r.tick(i % 5 == 0 ? P1 : null, P1).leave);
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
