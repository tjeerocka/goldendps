package com.goldendps.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Automatic party for raids. Every member derives the same party name from data the game gives
 * all of them (the member names, or the CoX raid party id), so they meet in one RuneLite party
 * without exchanging anything. A party the player joined by hand is never changed.
 */
public class RaidParty
{
	/** The raid party must stay the same this long before joining (people join one by one). */
	public static final int STABLE_TICKS = 5;
	public static final String PREFIX = "goldendps ";

	private String candidate;
	private int stableTicks;

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
	 * @return the party to join, or null
	 */
	public String tick(String name, String currentParty)
	{
		if (name == null)
		{
			candidate = null;
			stableTicks = 0;
			return null;
		}
		if (!name.equals(candidate))
		{
			candidate = name;
			stableTicks = 0;
		}
		stableTicks++;
		boolean manual = currentParty != null && !isAuto(currentParty);
		if (manual || name.equals(currentParty) || stableTicks < STABLE_TICKS)
		{
			return null;
		}
		return name;
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
