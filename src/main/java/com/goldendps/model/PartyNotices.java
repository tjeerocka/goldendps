package com.goldendps.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Chat notices when someone joins or leaves your party.
 * Names come a few seconds after a member connects, so we wait for the name.
 * Right after you join a party, the people already in it are not announced ({@link #QUIET_MS}).
 */
public class PartyNotices
{
	public static final long QUIET_MS = 5000;

	public static final class Notice
	{
		public final String name;
		public final boolean joined;

		Notice(String name, boolean joined)
		{
			this.name = name;
			this.joined = joined;
		}
	}

	/** Member id → name, of members we know. */
	private final Map<Long, String> known = new HashMap<>();
	/** Members you kicked: you already got a "removed" notice for them. */
	private final Set<Long> silentLeave = new HashSet<>();
	private long partyKey = Long.MIN_VALUE;
	private long quietUntil;
	private boolean welcomed;

	/**
	 * Call every tick while in a party.
	 *
	 * @param localId your member id (a new id = a new party)
	 * @param named   other members that have a name now (member id → name)
	 */
	public List<Notice> tick(long now, long localId, Map<Long, String> named)
	{
		List<Notice> out = new ArrayList<>();
		if (localId != partyKey)
		{
			partyKey = localId;
			known.clear();
			silentLeave.clear();
			quietUntil = now + QUIET_MS;
			welcomed = false;
		}
		boolean quiet = now < quietUntil;
		for (Map.Entry<Long, String> e : named.entrySet())
		{
			if (!known.containsKey(e.getKey()))
			{
				known.put(e.getKey(), e.getValue());
				if (!quiet)
				{
					out.add(new Notice(e.getValue(), true));
				}
			}
		}
		known.entrySet().removeIf(e ->
		{
			if (named.containsKey(e.getKey()))
			{
				return false;
			}
			if (!silentLeave.remove(e.getKey()))
			{
				out.add(new Notice(e.getValue(), false));
			}
			return true;
		});
		return out;
	}

	/**
	 * Once per party, when the names are in ({@link #QUIET_MS} after you joined): who is in it with you.
	 * Null before that and after it was given. Call after {@link #tick}.
	 */
	public List<String> welcome(long now)
	{
		if (welcomed || partyKey == Long.MIN_VALUE || now < quietUntil)
		{
			return null;
		}
		welcomed = true;
		List<String> names = new ArrayList<>(known.values());
		names.sort(String.CASE_INSENSITIVE_ORDER);
		return names;
	}

	/** Text for {@link #welcome}. */
	public static String welcomeText(String party, List<String> others)
	{
		String in = "GoldenDPS: you are in the party \"" + party + "\"";
		if (others.isEmpty())
		{
			return in + ". Nobody else is in it yet.";
		}
		return in + " with " + String.join(", ", others) + ".";
	}

	/** You kicked this member: no "left" notice later. */
	public void kicked(long memberId)
	{
		silentLeave.add(memberId);
	}

	/** You left the party. */
	public void reset()
	{
		partyKey = Long.MIN_VALUE;
		known.clear();
		silentLeave.clear();
	}
}
