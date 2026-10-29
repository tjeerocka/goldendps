package com.goldendps.model;

import java.util.Random;

/** Random hungry lines for the top healer: one line for a few seconds, then a pause. */
public class HungryTalk
{
	static final long SHOW_MS = 5000;
	static final long MIN_PAUSE_MS = 6000;
	static final long MAX_PAUSE_MS = 14000;

	static final String[] LINES = {
		"So hungry...",
		"Is that a shark?",
		"Anyone got fries?",
		"Nom nom nom",
		"I could eat a whole Vorkath",
		"Brb, eating",
		"Who ate my karambwan?",
		"One more burger won't hurt",
		"Snack time!",
		"Healing? I just love food",
	};

	private final Random random;
	private String line;
	private long showUntil;
	private long nextAt;

	public HungryTalk(Random random)
	{
		this.random = random;
	}

	/** The line to show now, or null during a pause. */
	public String line(long now)
	{
		if (now < showUntil)
		{
			return line;
		}
		if (now < nextAt)
		{
			return null;
		}
		String next;
		do
		{
			next = LINES[random.nextInt(LINES.length)];
		}
		while (next.equals(line));
		line = next;
		showUntil = now + SHOW_MS;
		nextAt = showUntil + MIN_PAUSE_MS + (long) (random.nextDouble() * (MAX_PAUSE_MS - MIN_PAUSE_MS));
		return line;
	}
}
