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
		"^Your (?:completed )?(.+?) (?:kill |chest |completion )?count is: ([\\d,]+)", Pattern.CASE_INSENSITIVE);

	/** Bosses whose kill count the game tells under another name. */
	private static final Map<String, String> COUNT_NAMES = new HashMap<>();

	static
	{
		COUNT_NAMES.put("dusk", "grotesque guardians");
		COUNT_NAMES.put("dawn", "grotesque guardians");
		COUNT_NAMES.put("branda the fire queen", "royal titans");
		COUNT_NAMES.put("eldric the ice king", "royal titans");
		COUNT_NAMES.put("blue moon", "lunar");
		COUNT_NAMES.put("eclipse moon", "lunar");
		COUNT_NAMES.put("blood moon", "lunar");
		COUNT_NAMES.put("crystalline hunllef", "gauntlet");
		COUNT_NAMES.put("corrupted hunllef", "corrupted gauntlet");
	}

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
		Matcher m = COUNT.matcher(ChatText.withoutTags(text));
		if (m.find())
		{
			int value = Integer.parseInt(m.group(2).replace(",", ""));
			counts.put(normalize(m.group(1)), new Count(value, now));
		}
	}

	/** Lower case without a leading "the": the game says "Nightmare" for The Nightmare. */
	private static String normalize(String name)
	{
		String n = name.toLowerCase(Locale.ROOT);
		return n.startsWith("the ") ? n.substring(4) : n;
	}

	/**
	 * The KC of this boss, raid or chest told close to {@code atMs}, or 0. Raid modes have their own
	 * count ("Tombs of Amascut: Expert Mode", "Chambers of Xeric Challenge Mode"), so a count whose
	 * name starts with the raid name also belongs to it.
	 */
	public int near(String name, long atMs)
	{
		if (name == null)
		{
			return 0;
		}
		String wanted = normalize(name);
		wanted = COUNT_NAMES.getOrDefault(wanted, wanted);
		for (Map.Entry<String, Count> e : counts.entrySet())
		{
			Count c = e.getValue();
			if (e.getKey().startsWith(wanted) && Math.abs(c.atMs - atMs) <= NEAR_MS)
			{
				return c.value;
			}
		}
		return 0;
	}
}
