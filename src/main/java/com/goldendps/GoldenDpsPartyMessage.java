package com.goldendps;

import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * GoldenDPS party control (RuneLite parties have no leader or kick):
 * <ul>
 * <li>{@code hi}: "I run GoldenDPS, and I think the leader is {@link #leader}" (0 = don't know)</li>
 * <li>{@code leader}: the leader says who leads</li>
 * <li>{@code kick}: the leader asks {@link #target}'s GoldenDPS to leave the party</li>
 * </ul>
 * The party server knows messages by class name, so this name must not change.
 */
public class GoldenDpsPartyMessage extends PartyMemberMessage
{
	static final String HI = "hi";
	static final String LEADER = "leader";
	static final String KICK = "kick";

	public int v = 1;
	public String type;
	public long leader;
	public long target;

	public GoldenDpsPartyMessage()
	{
	}

	GoldenDpsPartyMessage(String type, long leader, long target)
	{
		this.type = type;
		this.leader = leader;
		this.target = target;
	}
}
