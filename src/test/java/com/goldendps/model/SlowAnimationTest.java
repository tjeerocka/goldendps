package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SlowAnimationTest
{
	private static final int WALK = 819;
	private static final int RUN = 824;
	private static final int FRAMES = 12;

	/** Feeds the real animation (0..11 looping) and counts how many slow frame steps happen. */
	private int slowSteps(double speed, int realSteps)
	{
		SlowAnimation s = new SlowAnimation();
		int real = 0;
		int prev = s.update(WALK, real, true, speed);
		int steps = 0;
		for (int i = 0; i < realSteps; i++)
		{
			real = (real + 1) % FRAMES;
			int f = s.update(WALK, real, true, speed);
			if (f != prev)
			{
				steps++;
			}
			prev = f;
		}
		return steps;
	}

	@Test
	public void seventyPercentSpeedPlaysSevenOfTenFrames()
	{
		assertEquals(70, slowSteps(0.7, 100));
		assertEquals(50, slowSteps(0.5, 100));
	}

	@Test
	public void slowFrameOnlyMovesForwardAndWraps()
	{
		SlowAnimation s = new SlowAnimation();
		int real = 0;
		int prev = s.update(WALK, real, true, 0.5);
		for (int i = 0; i < 200; i++)
		{
			real = (real + 1) % FRAMES;
			int f = s.update(WALK, real, true, 0.5);
			assertTrue("never jumps back (except the wrap to 0)", f == prev || f == prev + 1 || (f == 0 && prev == FRAMES - 1));
			assertTrue(f >= 0 && f < FRAMES);
			prev = f;
		}
	}

	@Test
	public void normalSpeedOrNotMovingDrawsTheRealFrame()
	{
		SlowAnimation s = new SlowAnimation();
		assertEquals(-1, s.update(WALK, 3, true, 1.0));
		assertEquals(-1, s.update(808, 0, false, 0.5));
	}

	@Test
	public void switchingFromWalkToRunStartsAtTheRealFrame()
	{
		SlowAnimation s = new SlowAnimation();
		s.update(WALK, 3, true, 0.5);
		assertEquals(3, s.update(WALK, 4, true, 0.5));   // half speed: not yet
		assertEquals(4, s.update(WALK, 5, true, 0.5));
		assertEquals(7, s.update(RUN, 7, true, 0.5));    // new animation: starts at the real frame
	}
}
