package com.goldendps.model;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The CoX points the game tells at the end of a raid ("Total points: 31,408, Personal points: 31,408 (100%)"). */
public class RaidPoints
{
	private static final Pattern POINTS = Pattern.compile(
		"^Total points: ([\\d,]+), Personal points: ([\\d,]+)");

	private long total;
	private long personal;
	private long atMs;

	/** A game message. */
	public void message(long now, String text)
	{
		Matcher m = POINTS.matcher(text.replaceAll("<[^>]*>", ""));
		if (m.find())
		{
			total = Long.parseLong(m.group(1).replace(",", ""));
			personal = Long.parseLong(m.group(2).replace(",", ""));
			atMs = now;
		}
	}

	/** "31,408 pts", or "12,000 of 31,408 pts" in a group; null when none were told close to {@code atMs}. */
	public String near(long atMs)
	{
		if (this.atMs == 0 || Math.abs(this.atMs - atMs) > KillCounts.NEAR_MS)
		{
			return null;
		}
		return personal == total ? exact(total) + " pts" : exact(personal) + " of " + exact(total) + " pts";
	}

	/** Points are shown exactly, as the game does: 31,408 (not 31.4k). */
	private static String exact(long points)
	{
		return String.format(Locale.US, "%,d", points);
	}
}
