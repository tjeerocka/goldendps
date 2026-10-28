package com.goldendps;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * What one player sends to the party once per game tick (only when something happened).
 * Like RuneLite's DPS Counter "DpsUpdate", but with all hits of the tick in one message,
 * plus damage taken and healing.
 *
 * <p>The party server knows messages by class name, so this name must not change
 * (and must be different from other plugins' messages).</p>
 */
public class GoldenDpsUpdate extends PartyMemberMessage
{
	/** One hitsplat of the sender on an NPC. */
	public static class Hit
	{
		/** NPC index: the same on every client in the same world / instance. */
		public int i;
		public int id;
		public String nm;
		public int a;
		public boolean miss;
		public boolean boss;

		public Hit()
		{
		}

		Hit(int index, int npcId, String name, int amount, boolean miss, boolean boss)
		{
			this.i = index;
			this.id = npcId;
			this.nm = name;
			this.a = amount;
			this.miss = miss;
			this.boss = boss;
		}
	}

	/** Version of this message, so later versions can stay compatible. */
	public int v = 1;
	/** Sender's world. Hits only match your targets when you are on the same world. */
	public int w;
	/** Sender's in-game name. */
	public String n;
	public List<Hit> h = new ArrayList<>();
	public int taken;
	public int food;
	public int life;
	public int regen;
	public int other;

	boolean isEmpty()
	{
		return h.isEmpty() && taken == 0 && food == 0 && life == 0 && regen == 0 && other == 0;
	}
}
