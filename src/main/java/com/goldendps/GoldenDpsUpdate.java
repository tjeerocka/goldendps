package com.goldendps;

import com.google.gson.annotations.SerializedName;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.messages.PartyMemberMessage;

/**
 * Everything one player did in a game tick, sent to the party. Sent every tick, so the JSON
 * field names are kept short. The party server identifies messages by class name, which must
 * therefore never change.
 */
public class GoldenDpsUpdate extends PartyMemberMessage
{
	public static class Hit
	{
		/** The same on every client in the same world or instance. */
		@SerializedName("i")
		public int npcIndex;
		@SerializedName("id")
		public int npcId;
		@SerializedName("nm")
		public String npcName;
		@SerializedName("a")
		public int amount;
		public boolean miss;
		public boolean boss;
		/** NPC position; instances have their own coordinates, which tells instances apart. */
		public int x;
		public int y;
		@SerializedName("p")
		public int plane;
		/** Weapon, special attack or spell; null from older versions. */
		@SerializedName("s")
		public String source;

		public Hit()
		{
		}

		Hit(int npcIndex, int npcId, String npcName, int amount, boolean miss, boolean boss, WorldPoint pos)
		{
			this.npcIndex = npcIndex;
			this.npcId = npcId;
			this.npcName = npcName;
			this.amount = amount;
			this.miss = miss;
			this.boss = boss;
			if (pos != null)
			{
				x = pos.getX();
				y = pos.getY();
				plane = pos.getPlane();
			}
		}
	}

	@SerializedName("v")
	public int version = 3;
	@SerializedName("w")
	public int world;
	@SerializedName("n")
	public String name;
	@SerializedName("h")
	public List<Hit> hits = new ArrayList<>();
	public int taken;
	public int food;
	@SerializedName("life")
	public int lifesteal;
	public int regen;
	@SerializedName("other")
	public int healOther;
	@SerializedName("of")
	public int foodOverheal;
	@SerializedName("ol")
	public int lifestealOverheal;

	boolean isEmpty()
	{
		return hits.isEmpty() && taken == 0 && food == 0 && lifesteal == 0 && regen == 0 && healOther == 0
			&& foodOverheal == 0 && lifestealOverheal == 0;
	}
}
