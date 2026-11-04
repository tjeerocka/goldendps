package com.goldendps;

import com.goldendps.model.FightTracker;
import com.goldendps.model.RaidZone;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

/** One fight per raid: starts when you enter the raid instance and ends when you leave it. Client thread only. */
class RaidFights
{
	private static final String RAID_BEGAN = "The raid has begun!";

	private final Client client;
	private final ChatNotifier chat;
	private final FightTracker tracker;
	private final RaidZone raidZone = new RaidZone();

	RaidFights(Client client, ChatNotifier chat, FightTracker tracker)
	{
		this.client = client;
		this.chat = chat;
		this.tracker = tracker;
	}

	void tick(long now)
	{
		RaidZone.Change change = raidZone.tick(currentRaid());
		if (change.start != null)
		{
			tracker.startRaid(now, change.start);
			chat.info(change.start + " started. The meter counts the whole raid until you leave.");
		}
		else if (change.end)
		{
			tracker.endRaid(now, "Left the raid");
		}
	}

	/** CoX starts its timer with this message; ToB and ToA start theirs at the first room. */
	void gameMessage(long now, String message)
	{
		if (message.startsWith(RAID_BEGAN))
		{
			tracker.raidBegan(now);
		}
	}

	boolean isInsideRaid()
	{
		return raidZone.getInside() != null;
	}

	void reset()
	{
		raidZone.reset();
	}

	/** The raid whose instance you stand in, or null. */
	private String currentRaid()
	{
		Player me = client.getLocalPlayer();
		WorldView wv = client.getTopLevelWorldView();
		if (me == null || me.getLocalLocation() == null || wv == null)
		{
			return null;
		}
		WorldPoint pos = WorldPoint.fromLocalInstance(client, me.getLocalLocation());
		return pos == null ? null : RaidZone.raidFor(pos.getRegionID(), wv.isInstance());
	}
}
