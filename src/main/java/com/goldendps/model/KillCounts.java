package com.goldendps.model;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The kill counts the game tells you in the chat ("Your Vorkath kill count is: 123.",
 * "Your Barrows chest count is: 19.", "Your completed Chambers of Xeric count is: 21."),
 * so a report can add the KC of the kill it reports.
 */
public class KillCounts
{
	/** A count belongs to a fight that ended this close to the message. */
	static final long NEAR_MS = 30_000;

	private static final Pattern COUNT = Pattern.compile(
		"^Your (?:completed )?(.+?) (?:kill |chest )?count is: ([\\d,]+)");

	private static final class Count
	{
		final int value;
		final long atMs;

		Count(int value, long atMs)
		{
			this.value = value;
			this.atMs = atMs;
		}
	}

	private final Map<String, Count> counts = new HashMap<>();

	/** A game message. */
	public void message(long now, String text)
	{
		Matcher m = COUNT.matcher(text.replaceAll("<[^>]*>", ""));
		if (m.find())
		{
			int value = Integer.parseInt(m.group(2).replace(",", ""));
			counts.put(m.group(1).toLowerCase(Locale.ROOT), new Count(value, now));
		}
	}

	/** The KC of this boss, raid or chest told close to {@code atMs}, or 0. */
	public int near(String name, long atMs)
	{
		Count c = name == null ? null : counts.get(name.toLowerCase(Locale.ROOT));
		return c != null && Math.abs(c.atMs - atMs) <= NEAR_MS ? c.value : 0;
	}
}
