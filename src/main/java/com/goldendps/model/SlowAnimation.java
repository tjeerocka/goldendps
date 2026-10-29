package com.goldendps.model;

/**
 * Plays a walk / run animation slower without changing how fast the player moves.
 *
 * <p>We never change the real player's animation. We only follow its frames and work out
 * a slower frame (speed 0.7 = 7 frame steps for every 10 of the real one). The fat copy
 * is drawn with that frame; the real character keeps its own.</p>
 */
public class SlowAnimation
{
	private int anim = -1;
	private int lastReal = -1;
	private int maxSeen = -1;
	private int frame = -1;
	private double acc;

	/**
	 * Call every client tick with the real animation state.
	 *
	 * @param poseAnim  the player's current pose animation
	 * @param poseFrame its current (real) frame
	 * @param moving    true when the pose is a walk / run animation
	 * @param speed     0..1, how fast the animation plays (1 = normal)
	 * @return the slow frame to draw, or -1 to draw the real frame
	 */
	public int update(int poseAnim, int poseFrame, boolean moving, double speed)
	{
		if (!moving || speed >= 1 || poseAnim == -1 || poseFrame < 0)
		{
			reset();
			return -1;
		}
		if (poseAnim != anim)
		{
			// new animation (walk -> run): start at the real frame
			anim = poseAnim;
			lastReal = poseFrame;
			maxSeen = poseFrame;
			frame = poseFrame;
			acc = 0;
			return frame;
		}
		if (poseFrame != lastReal)
		{
			// how many frames the real animation moved on (1 when it wrapped to the start)
			int steps = poseFrame > lastReal ? poseFrame - lastReal : poseFrame + 1;
			lastReal = poseFrame;
			maxSeen = Math.max(maxSeen, poseFrame);
			acc += steps * Math.max(0.05, speed);
			while (acc >= 1 - 1e-9)   // small margin: 0.7 added 10 times is 6.9999...
			{
				acc -= 1;
				// after the last frame we know, go back to the first one
				frame = frame >= maxSeen ? 0 : frame + 1;
			}
		}
		return frame;
	}

	public void reset()
	{
		anim = -1;
		lastReal = -1;
		maxSeen = -1;
		frame = -1;
		acc = 0;
	}
}
