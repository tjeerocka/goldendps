package com.goldendps.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Party for raids. Every member derives the same party name from data the game gives all of them
 * (the member names, or the CoX raid party id), so one click puts them in the same RuneLite party
 * without exchanging anything. The party is left again when the raid party is gone. Joining is
 * only offered, never done automatically, and a party joined by hand is never changed.
 */
public class RaidParty
{
	/** The raid party must stay the same this long before it is offered (people join one by one). */
	public static final int STABLE_TICKS = 5;
	/** No raid party for this long means the raid is over (short gaps happen while loading). */
	public static final int LEAVE_TICKS = 10;
	public static final String PREFIX = "goldendps ";

	/** What to do with the RuneLite party this tick. */
	public static final class Action
	{
		public static final Action NONE = new Action(null, false);
		public static final Action LEAVE = new Action(null, true);

		/** The party to offer, or null. */
		public final String offer;
		public final boolean leave;

		private Action(String offer, boolean leave)
		{
			this.offer = offer;
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

	public static boolean isAuto(String party)
	{
		return party != null && party.startsWith(PREFIX);
	}

	/**
	 * Call every game tick.
	 *
	 * @param name         party name for the raid party you are in, or null
	 * @param currentParty the RuneLite party you are in, or null
	 */
	public Action tick(String name, String currentParty)
	{
		boolean inAutoParty = isAuto(currentParty);
		if (name == null)
		{
			candidate = null;
			stableTicks = 0;
			ticksWithoutRaidParty++;
			return inAutoParty && ticksWithoutRaidParty == LEAVE_TICKS ? Action.LEAVE : Action.NONE;
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
