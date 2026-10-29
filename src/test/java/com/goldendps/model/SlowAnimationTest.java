package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class SlowAnimationTest
{
	private static final int WALK = 819;
	private static final int RUN = 824;

	/** Simulates the client: every call it moves one frame on, unless we put it back. */
	private int framesShown(double speed, int steps)
	{
		SlowAnimation s = new SlowAnimation();
		int frame = 0;
		s.step(WALK, frame, true, speed);
		int advances = 0;
		for (int i = 0; i < steps; i++)
		{
			int next = frame + 1;           // the client wants the next frame
			int back = s.step(WALK, next, true, speed);
			if (back >= 0)
			{
				frame = back;              // we put the old frame back
			}
			else
			{
				frame = next;
				advances++;
			}
		}
		return advances;
	}

	@Test
	public void seventyPercentSpeedPlaysSevenOfTenFrames()
	{
		assertEquals(70, framesShown(0.7, 100));
		assertEquals(50, framesShown(0.5, 100));
	}

	@Test
	public void normalSpeedChangesNothing()
	{
		assertEquals(100, framesShown(1.0, 100));
	}

	@Test
	public void notMovingLeavesTheAnimationAlone()
	{
		SlowAnimation s = new SlowAnimation();
		assertEquals(-1, s.step(808, 0, false, 0.5));
		assertEquals(-1, s.step(808, 1, false, 0.5));
	}

	@Test
	public void switchingFromWalkToRunStartsFresh()
	{
		SlowAnimation s = new SlowAnimation();
		s.step(WALK, 3, true, 0.5);
		assertEquals(3, s.step(WALK, 4, true, 0.5));   // held back
		assertEquals(-1, s.step(RUN, 0, true, 0.5));   // new animation: not touched
		assertEquals(0, s.step(RUN, 1, true, 0.5));    // then slowed again
	}
}
