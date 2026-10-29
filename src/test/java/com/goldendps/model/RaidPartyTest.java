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

	@Test
	public void everyoneGetsTheSameName()
	{
		// each client sees the names in its own order, with odd spaces / case
		String a = RaidParty.partyName(TOB, 302, Arrays.asList("Kurt Yoghurt", "Zezima", "", null));
		String b = RaidParty.partyName(TOB, 302, Arrays.asList("zezima", "Kurt Yoghurt"));
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
		String name = "goldendps tob abc";
		for (int i = 1; i < RaidParty.STABLE_TICKS; i++)
		{
			assertNull(r.tick(name, null).join);
		}
		assertEquals(name, r.tick(name, null).join);
		// joined: nothing more to do while it stays the same
		assertNull(r.tick(name, name).join);
	}

	@Test
	public void someoneJoinsTheRaidPartyMovesToTheNewName()
	{
		RaidParty r = new RaidParty();
		for (int i = 0; i < RaidParty.STABLE_TICKS; i++)
		{
			r.tick("p1", null);
		}
		RaidParty.Action a = RaidParty.Action.NONE;
		for (int i = 0; i < RaidParty.STABLE_TICKS; i++)
		{
			a = r.tick("p2", "p1");
		}
		assertEquals("p2", a.join);
	}

	@Test
	public void neverTouchesAPartyYouJoinedYourself()
	{
		RaidParty r = new RaidParty();
		for (int i = 0; i < 20; i++)
		{
			RaidParty.Action a = r.tick("goldendps tob abc", "my friends");
			assertNull(a.join);
			assertFalse(a.leave);
		}
	}

	@Test
	public void leavesAfterTheRaid()
	{
		RaidParty r = new RaidParty();
		for (int i = 0; i < RaidParty.STABLE_TICKS; i++)
		{
			r.tick("p1", null);
		}
		for (int i = 1; i < RaidParty.LEAVE_TICKS; i++)
		{
			assertFalse(r.tick(null, "p1").leave);
		}
		assertTrue(r.tick(null, "p1").leave);
		assertNull(r.getJoined());
	}

	@Test
	public void switchingToYourOwnPartyStopsTheAutoParty()
	{
		RaidParty r = new RaidParty();
		for (int i = 0; i < RaidParty.STABLE_TICKS; i++)
		{
			r.tick("p1", null);
		}
		// you joined "my friends" yourself during the raid
		assertNull(r.tick("p1", "my friends").join);
		assertNull(r.getJoined());
		for (int i = 0; i < 20; i++)
		{
			assertFalse("never leaves your own party", r.tick(null, "my friends").leave);
		}
	}
}
