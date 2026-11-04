package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class RaidPointsTest
{
	@Test
	public void readsThePointsMessage()
	{
		RaidPoints p = new RaidPoints();
		p.message(1000, "Total points: <col=ff0000>31,408</col>, Personal points: <col=ff0000>31,408</col> (100%)");
		assertEquals("31,408 pts", p.near(2000));
		p.message(1000, "Total points: 52,000, Personal points: 20,500 (39.4%)");
		assertEquals("20,500 of 52,000 pts", p.near(2000));
		assertNull("too long ago", p.near(1000 + KillCounts.NEAR_MS + 1));
	}
}
