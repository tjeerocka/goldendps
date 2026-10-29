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
 * <li>Only when you are not in a party you joined yourself (auto parties start with {@link #PREFIX}).</li>
 * <li>The names must stay the same for {@link #STABLE_TICKS} ticks first (people join the lobby one by one).</li>
 * <li>After the raid you stay in the party (also after a relog), until you leave it yourself
 * or start a raid with other people.</li>
 * </ul>
 */
public class RaidParty
{
	public static final int STABLE_TICKS = 5;
	public static final String PREFIX = "goldendps ";

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

	/**
	 * The party name for this raid party, or null when there is no group (alone or no names).
	 */
	public static String partyName(String raid, int world, List<String> names)
	{
		return partyName(raid, world, names, 2);
	}

	/** Same, with the smallest group that counts (1 = also alone, for testing). */
	public static String partyName(String raid, int world, List<String> names, int minPeople)
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
		if (clean.size() < Math.max(1, minPeople))
		{
			return null;
		}
		clean.sort(null);
		String key = raid + "|" + world + "|" + String.join(",", clean);
		return PREFIX + raid.toLowerCase(Locale.ROOT) + " " + hash(key);
	}

	/**
	 * Chambers of Xeric: the game gives every member of a raid party (made on the recruiting board)
	 * the same party id. Null when you are in no CoX party, or alone in it.
	 *
	 * @param partyId   varp RAIDS_PARTY_GROUPHOLDER (-1 = no party)
	 * @param partySize varbit RAIDS_CLIENT_PARTYSIZE (0 = not known yet)
	 */
	public static String coxPartyName(int world, int partyId, int partySize)
	{
		return coxPartyName(world, partyId, partySize, false);
	}

	/** Same; alone = true also gives a name when you are alone (for testing). */
	public static String coxPartyName(int world, int partyId, int partySize, boolean alone)
	{
		if (partyId <= 0 || (partySize == 1 && !alone))
		{
			return null;
		}
		return PREFIX + "cox " + hash("CoX|" + world + "|" + partyId);
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

	/** A party that GoldenDPS made for a raid. */
	public static boolean isAuto(String party)
	{
		return party != null && party.startsWith(PREFIX);
	}

	/**
	 * Call every game tick.
	 *
	 * @param name         {@link #partyName} for the raid you are in now, or null (no raid party)
	 * @param currentParty the RuneLite party you are in now (null = none)
	 */
	public Action tick(String name, String currentParty)
	{
		joined = isAuto(currentParty) ? currentParty : null;
		boolean manual = currentParty != null && !isAuto(currentParty);
		if (name == null)
		{
			candidate = null;
			stableTicks = 0;
			return Action.NONE;
		}
		if (!name.equals(candidate))
		{
			candidate = name;
			stableTicks = 0;
		}
		stableTicks++;
		if (manual || name.equals(currentParty) || stableTicks < STABLE_TICKS)
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
	}
}
