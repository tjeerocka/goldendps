package com.goldendps.model;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Who leads the party. RuneLite parties have no leader and no kick, so GoldenDPS keeps its own:
 * <ul>
 * <li>Alone in a new party (nobody else after {@link #WAIT_MS}): you are the leader.</li>
 * <li>Joining a party: the leader tells you ({@link #announce}).</li>
 * <li>Nobody says who leads (old party): the GoldenDPS member with the lowest id leads;
 *     every GoldenDPS client works out the same one.</li>
 * <li>The leader leaves: the same rule picks the next one.</li>
 * </ul>
 * Only the leader can kick, and only players who run GoldenDPS can be kicked
 * (their GoldenDPS leaves the party).
 */
public class PartyLeader
{
	/** How long to wait for the leader to answer after you joined. */
	public static final long WAIT_MS = 3000;

	private long local;
	private long leader;
	private long joinedMs;
	private final Set<Long> withPlugin = new HashSet<>();

	/** You joined a party (your member id is known). */
	public void joined(long localId, long now)
	{
		local = localId;
		leader = 0;
		joinedMs = now;
		withPlugin.clear();
		withPlugin.add(localId);
	}

	/** You left the party. */
	public void left()
	{
		local = 0;
		leader = 0;
		withPlugin.clear();
	}

	public boolean isJoined()
	{
		return local != 0;
	}

	/** Someone sent a GoldenDPS message: they run the plugin. */
	public void sawPlugin(long memberId)
	{
		if (memberId != 0)
		{
			withPlugin.add(memberId);
		}
	}

	public boolean hasPlugin(long memberId)
	{
		return withPlugin.contains(memberId);
	}

	/**
	 * A GoldenDPS member says who leads (0 = "I don't know yet").
	 * Taken when we don't know yet, or when the current leader hands it over.
	 */
	public void announce(long sender, long claimedLeader)
	{
		sawPlugin(sender);
		if (claimedLeader == 0)
		{
			return;
		}
		if (leader == 0 || sender == leader)
		{
			leader = claimedLeader;
		}
	}

	/** A member left. If it was the leader, the next one is picked. */
	public void memberLeft(long memberId, Collection<Long> stillThere)
	{
		withPlugin.remove(memberId);
		if (memberId == leader)
		{
			leader = lowestWithPlugin(stillThere);
		}
	}

	/**
	 * Call every game tick. When nobody said who leads after {@link #WAIT_MS}: alone = you lead,
	 * else the GoldenDPS member with the lowest id.
	 *
	 * @return true when the leader changed (then the new leader should announce it)
	 */
	public boolean tick(long now, Collection<Long> members)
	{
		if (local == 0 || leader != 0 || now - joinedMs < WAIT_MS)
		{
			return false;
		}
		leader = members.size() <= 1 ? local : lowestWithPlugin(members);
		return leader != 0;
	}

	private long lowestWithPlugin(Collection<Long> members)
	{
		long best = 0;
		for (long id : members)
		{
			if (withPlugin.contains(id) && (best == 0 || id < best))
			{
				best = id;
			}
		}
		return best;
	}

	/** Should you leave because of this kick? Only when the leader kicks you. */
	public boolean acceptKick(long sender, long target)
	{
		return local != 0 && target == local && sender == leader && sender != local;
	}

	public long getLeader()
	{
		return leader;
	}

	public boolean isLeader()
	{
		return local != 0 && leader == local;
	}
}
