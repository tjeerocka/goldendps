package com.goldendps.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Auto party in raids. Everyone with GoldenDPS in the same raid party works out the SAME party
 * name from the raid (raid, world and the sorted member names), so they all join the same RuneLite
 * party without typing anything.
 * <ul>
 * <li>Only when you are not in a party you joined yourself.</li>
 * <li>The names must stay the same for {@link #STABLE_TICKS} ticks first (people join the lobby one by one).</li>
 * <li>After the raid (no raid party for {@link #LEAVE_TICKS} ticks) you leave that party again.</li>
 * </ul>
 */
public class RaidParty
{
	public static final int STABLE_TICKS = 5;
	public static final int LEAVE_TICKS = 10;

	/** What the plugin should do this tick. */
	public static final class Action
	{
		public static final Action NONE = new Action(null, false);

		/** Party name to join, or null. */
		public final String join;
		public final boolean leave;

		Action(String join, boolean leave)
		{
			this.join = join;
			this.leave = leave;
		}
	}

	private String candidate;
	private int stableTicks;
	private String joined;
	private int goneTicks;

	/**
	 * The party name for this raid party, or null when there is no group (alone or no names).
	 */
	public static String partyName(String raid, int world, List<String> names)
	{
		List<String> clean = new ArrayList<>();
		for (String n : names)
		{
			if (n == null)
			{
				continue;
			}
			String c = n.replace(' ', ' ').replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
			if (!c.isEmpty() && !clean.contains(c))
			{
				clean.add(c);
			}
		}
		if (clean.size() < 2)
		{
			return null;
		}
		clean.sort(null);
		String key = raid + "|" + world + "|" + String.join(",", clean);
		return "goldendps " + raid.toLowerCase(Locale.ROOT) + " " + hash(key);
	}

	private static String hash(String s)
	{
		try
		{
			byte[] d = MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < 8; i++)
			{
				sb.append(String.format("%02x", d[i] & 0xff));
			}
			return sb.toString();
		}
		catch (NoSuchAlgorithmException e)
		{
			return Integer.toHexString(s.hashCode());
		}
	}

	/**
	 * Call every game tick.
	 *
	 * @param name         {@link #partyName} for the raid you are in now, or null (no raid party)
	 * @param currentParty the RuneLite party you are in now (null = none)
	 */
	public Action tick(String name, String currentParty)
	{
		// you joined another party yourself: hands off
		if (joined != null && !joined.equals(currentParty))
		{
			joined = null;
		}
		boolean manual = currentParty != null && !currentParty.equals(joined);

		if (name == null)
		{
			candidate = null;
			stableTicks = 0;
			if (joined != null && ++goneTicks >= LEAVE_TICKS)
			{
				joined = null;
				goneTicks = 0;
				return new Action(null, true);
			}
			return Action.NONE;
		}
		goneTicks = 0;

		if (!name.equals(candidate))
		{
			candidate = name;
			stableTicks = 0;
		}
		stableTicks++;
		if (manual || name.equals(joined) || stableTicks < STABLE_TICKS)
		{
			return Action.NONE;
		}
		joined = name;
		return new Action(name, false);
	}

	/** The auto party you are in, or null. */
	public String getJoined()
	{
		return joined;
	}

	public void reset()
	{
		candidate = null;
		stableTicks = 0;
		joined = null;
		goneTicks = 0;
	}
}
