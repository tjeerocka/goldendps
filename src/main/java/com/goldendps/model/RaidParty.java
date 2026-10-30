package com.goldendps.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Party for raids. Every member derives the same party name from data the game gives all of them
 * (the member names, or the CoX raid party id), so they all join the same RuneLite party
 * without exchanging anything. The party is left again when the raid party is gone, and a party
 * joined by hand is never changed.
 */
public class RaidParty
{
	/** The raid party must stay the same this long before it is joined (people join one by one). */
	public static final int STABLE_TICKS = 5;
	/**
	 * No raid party for this long outside a raid means the raid is over (short gaps happen while
	 * loading). Inside CoX the game clears the raid party id, so the party is kept there.
	 */
	public static final int LEAVE_TICKS = 10;
	public static final String PREFIX = "goldendps ";

	/** What to do with the RuneLite party this tick. */
	public static final class Action
	{
		public static final Action NONE = new Action(null, false);
		public static final Action LEAVE = new Action(null, true);

		/** The party to join, or null. */
		public final String join;
		public final boolean leave;

		private Action(String join, boolean leave)
		{
			this.join = join;
			this.leave = leave;
		}
	}

	private String candidate;
	private int stableTicks;
	private int ticksWithoutRaidParty;

	/** Party name for a raid with a member list, or null when fewer than two names are known. */
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
		return PREFIX + raid.toLowerCase(Locale.ROOT) + " " + hash(raid + "|" + world + "|" + String.join(",", clean));
	}

	/**
	 * Party name for Chambers of Xeric, from the raid party id (varp RAIDS_PARTY_GROUPHOLDER).
	 * Null without a party or when you are alone in it.
	 */
	public static String coxPartyName(int world, int partyId, int partySize)
	{
		if (partyId <= 0 || partySize == 1)
		{
			return null;
		}
		return PREFIX + "cox " + hash("CoX|" + world + "|" + partyId);
	}

	/** Your own party made outside raids (setting "Own party"). Never left here, a raid party may replace it. */
	public static final String OWN_PREFIX = PREFIX + "own ";

	/** A party GoldenDPS joined by itself: a raid party or your own party. */
	public static boolean isAuto(String party)
	{
		return party != null && party.startsWith(PREFIX);
	}

	public static boolean isOwn(String party)
	{
		return party != null && party.startsWith(OWN_PREFIX);
	}

	/**
	 * Call every game tick.
	 *
	 * @param name         party name for the raid party you are in, or null
	 * @param currentParty the RuneLite party you are in, or null
	 * @param inRaid       you are inside a raid
	 */
	public Action tick(String name, String currentParty, boolean inRaid)
	{
		boolean inAutoParty = isAuto(currentParty);
		boolean inRaidParty = inAutoParty && !isOwn(currentParty);
		if (name == null)
		{
			candidate = null;
			stableTicks = 0;
			ticksWithoutRaidParty = inRaid ? 0 : ticksWithoutRaidParty + 1;
			return inRaidParty && ticksWithoutRaidParty == LEAVE_TICKS ? Action.LEAVE : Action.NONE;
		}
		ticksWithoutRaidParty = 0;
		if (!name.equals(candidate))
		{
			candidate = name;
			stableTicks = 0;
		}
		stableTicks++;
		boolean manual = currentParty != null && !inAutoParty;
		if (manual || name.equals(currentParty) || stableTicks < STABLE_TICKS)
		{
			return Action.NONE;
		}
		return new Action(name, false);
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
}
