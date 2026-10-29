package com.goldendps.model;

/**
 * Plays a walk / run animation slower without changing how fast the player moves.
 * Every time the client moves the animation to its next frame, we only accept it
 * part of the time (speed 0.7 = 7 of every 10 frame steps), else we put the old frame back.
 */
public class SlowAnimation
{
	private int anim = -1;
	private int frame = -1;
	private double acc;

	/**
	 * Call every client tick.
	 *
	 * @param poseAnim  the player's current pose animation
	 * @param poseFrame its current frame
	 * @param moving    true when the pose is a walk / run animation
	 * @param speed     0..1, how fast the animation plays (1 = normal)
	 * @return the frame to put back, or -1 to leave the animation alone
	 */
	public int step(int poseAnim, int poseFrame, boolean moving, double speed)
	{
		if (!moving || speed >= 1 || poseAnim == -1)
		{
			reset();
			return -1;
		}
		if (poseAnim != anim)
		{
			// new animation (walk -> run): start counting from here
			anim = poseAnim;
			frame = poseFrame;
			acc = 0;
			return -1;
		}
		if (poseFrame == frame)
		{
			return -1;
		}
		acc += Math.max(0.05, speed);
		if (acc >= 1 - 1e-9)   // small margin: 0.7 added 10 times is 6.9999...
		{
			acc -= 1;
			frame = poseFrame;
			return -1;
		}
		return frame;
	}

	public void reset()
	{
		anim = -1;
		frame = -1;
		acc = 0;
	}
}
