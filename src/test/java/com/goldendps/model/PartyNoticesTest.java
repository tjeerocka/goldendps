package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class PartyNoticesTest
{
	private static Map<Long, String> members(Object... idName)
	{
		Map<Long, String> m = new HashMap<>();
		for (int i = 0; i < idName.length; i += 2)
		{
			m.put(((Number) idName[i]).longValue(), (String) idName[i + 1]);
		}
		return m;
	}

	@Test
	public void peopleAlreadyInThePartyAreNotAnnounced()
	{
		PartyNotices n = new PartyNotices();
		assertTrue(n.tick(0, 1, members()).isEmpty());
		// their names arrive 2 s later
		assertTrue(n.tick(2000, 1, members(5, "Zezima")).isEmpty());
	}

	@Test
	public void joinAndLeave()
	{
		PartyNotices n = new PartyNotices();
		n.tick(0, 1, members());
		List<PartyNotices.Notice> j = n.tick(10_000, 1, members(5, "Monster NRGs"));
		assertEquals(1, j.size());
		assertEquals("Monster NRGs", j.get(0).name);
		assertTrue(j.get(0).joined);
		assertTrue("only once", n.tick(11_000, 1, members(5, "Monster NRGs")).isEmpty());

		List<PartyNotices.Notice> l = n.tick(20_000, 1, members());
		assertEquals(1, l.size());
		assertFalse(l.get(0).joined);
	}

	@Test
	public void kickedMembersGetNoLeftNotice()
	{
		PartyNotices n = new PartyNotices();
		n.tick(0, 1, members());
		n.tick(10_000, 1, members(5, "Monster NRGs"));
		n.kicked(5);
		assertTrue(n.tick(12_000, 1, members()).isEmpty());
	}

	@Test
	public void aNewPartyStartsQuietAgain()
	{
		PartyNotices n = new PartyNotices();
		n.tick(0, 1, members());
		n.tick(10_000, 1, members(5, "A"));
		// you joined another party: new member id
		assertTrue(n.tick(11_000, 2, members(7, "B")).isEmpty());
	}

	@Test
	public void theOneWhoJoinsHearsWhoIsInThePartyOnce()
	{
		PartyNotices n = new PartyNotices();
		n.tick(0, 1, members());
		assertTrue(n.welcome(0) == null);
		n.tick(2000, 1, members(5, "Zezima", 6, "Bob"));
		n.tick(PartyNotices.QUIET_MS, 1, members(5, "Zezima", 6, "Bob"));
		List<String> w = n.welcome(PartyNotices.QUIET_MS);
		assertEquals("GoldenDPS: you are in the party \"tjeerocka\" with Bob, Zezima.", PartyNotices.welcomeText("tjeerocka", w));
		assertTrue("only once", n.welcome(9000) == null);
		assertEquals("GoldenDPS: you are in the party \"x\". Nobody else is in it yet.",
			PartyNotices.welcomeText("x", new java.util.ArrayList<>()));
	}
}
