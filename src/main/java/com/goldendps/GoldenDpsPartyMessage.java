package com.goldendps;

import com.google.gson.annotations.SerializedName;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Party control, since RuneLite parties have no leader or kick:
 * <ul>
 * <li>{@code hi}: a member runs GoldenDPS and names the leader it knows (0 if none)</li>
 * <li>{@code leader}: the leader announces itself</li>
 * <li>{@code kick}: the leader asks the {@link #target} member to leave</li>
 * </ul>
 * The party server identifies messages by class name, which must therefore never change.
 */
public class GoldenDpsPartyMessage extends PartyMemberMessage
{
	static final String HI = "hi";
	static final String LEADER = "leader";
	static final String KICK = "kick";

	@SerializedName("v")
	public int version = 1;
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
