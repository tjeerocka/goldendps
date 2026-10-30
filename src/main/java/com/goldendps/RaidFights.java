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
	private final Client client;
	private final GoldenDpsConfig config;
	private final ChatNotifier chat;
	private final FightTracker tracker;
	private final RaidZone raidZone = new RaidZone();

	RaidFights(Client client, GoldenDpsConfig config, ChatNotifier chat, FightTracker tracker)
	{
		this.client = client;
		this.config = config;
		this.chat = chat;
		this.tracker = tracker;
	}

	void tick(long now)
	{
		// the zone is also needed without the raid fight: the raid party stays while you are inside
		RaidZone.Change change = raidZone.tick(currentRaid());
		if (!config.raidFight())
		{
			tracker.endRaid(now, "Raid fight turned off");
			return;
		}
		if (change.start != null || (isInsideRaid() && tracker.getRaid() == null))
		{
			String raid = raidZone.getInside();
			tracker.startRaid(now, raid);
			chat.info(raid + " started. The meter counts the whole raid until you leave.");
		}
		else if (change.end)
		{
			tracker.endRaid(now, "Left the raid");
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
