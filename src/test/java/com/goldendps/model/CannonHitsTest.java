package com.goldendps.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CannonHitsTest
{
	private final CannonHits cannon = new CannonHits();

	@Test
	public void aHitWhenTheBallLandsIsTheCannon()
	{
		cannon.fired("1:5", 102);
		assertFalse("too early", cannon.isCannonHit("1:5", 100));
		assertTrue(cannon.isCannonHit("1:5", 102));
		assertFalse("each ball counts once", cannon.isCannonHit("1:5", 102));
	}

	@Test
	public void aTickEarlyOrLateStillCounts()
	{
		cannon.fired("1:5", 102);
		cannon.fired("1:5", 106);
		assertTrue(cannon.isCannonHit("1:5", 103));
		assertTrue(cannon.isCannonHit("1:5", 105));
	}

	@Test
	public void otherMonstersAndOldBallsDoNotCount()
	{
		cannon.fired("1:5", 102);
		assertFalse(cannon.isCannonHit("1:6", 102));
		assertFalse("the ball's hitsplat never came", cannon.isCannonHit("1:5", 110));
		cannon.fired("1:5", 120);
		cannon.clear();
		assertFalse(cannon.isCannonHit("1:5", 120));
	}
}
