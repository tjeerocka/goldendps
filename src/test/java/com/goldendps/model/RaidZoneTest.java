package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RaidZoneTest
{
	@Test
	public void regionsNeedAnInstance()
	{
		assertEquals(RaidZone.TOB, RaidZone.raidFor(12613, true));
		assertEquals(RaidZone.TFA, RaidZone.raidFor(16214, true));
		assertNull("same region but not instanced", RaidZone.raidFor(12613, false));
		assertNull("ToB lobby (Ver Sinhaza)", RaidZone.raidFor(14642, true));
	}

	@Test
	public void startsAtOnceAndEndsAfterSomeTicksOutside()
	{
		RaidZone z = new RaidZone();
		assertEquals(RaidZone.TOA, z.tick(RaidZone.TOA).start);
		assertNull(z.tick(RaidZone.TOA).start);
		// loading screen between rooms
		assertFalse(z.tick(null).end);
		assertNull(z.tick(RaidZone.TOA).start);
		for (int i = 1; i < RaidZone.LEAVE_TICKS; i++)
		{
			assertFalse(z.tick(null).end);
		}
		assertTrue(z.tick(null).end);
		assertNull(z.getInside());
	}

	@Test
	public void goingStraightToAnotherRaidStartsIt()
	{
		RaidZone z = new RaidZone();
		z.tick(RaidZone.TOB);
		assertEquals(RaidZone.COX, z.tick(RaidZone.COX).start);
	}
}
