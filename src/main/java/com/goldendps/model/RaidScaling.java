package com.goldendps.model;

/**
 * Raids change their monsters' stats: the Chambers of Xeric by party size, the highest Hitpoints level in
 * the party and Challenge Mode; the Tombs of Amascut make the defence roll bigger by raid level; the Theatre
 * of Blood only changes hitpoints. With the rules of the OSRS Wiki DPS calculator. Client thread only.
 */
public class RaidScaling
{
	private static final int CHALLENGE_MODE_PERCENT = 50;

	/** RaidZone.COX, TOB or TOA, or null outside raids. */
	private String raid;
	private int partySize = 1;
	private boolean challengeMode;
	/** We only know your own Hitpoints level; someone else in the party can have a higher one. */
	private int highestHitpoints = 99;
	private int toaLevel;

	/** What the game shows now. */
	public void update(String raid, int partySize, boolean challengeMode, int highestHitpoints, int toaLevel)
	{
		this.raid = raid;
		this.partySize = Math.max(1, partySize);
		this.challengeMode = challengeMode;
		this.highestHitpoints = highestHitpoints;
		this.toaLevel = toaLevel;
	}

	/** The monster with the stats of this raid; the same monster outside raids. */
	public NpcStats.Npc apply(NpcStats.Npc npc)
	{
		if (npc == null)
		{
			return null;
		}
		if (RaidZone.COX.equals(raid) && npc.is("xerician"))
		{
			return chambersOfXeric(npc);
		}
		if (RaidZone.TOA.equals(raid) && npc.hasRule("toa") && !npc.hasRule("toa-no-defence-scaling"))
		{
			return new NpcStats.Npc(npc, npc.defence, npc.magicLevel, toaLevel);
		}
		return npc;
	}

	/** Stats at level 1 stay 1; magic is a defence stat for some monsters, an attack stat for others. */
	private NpcStats.Npc chambersOfXeric(NpcStats.Npc npc)
	{
		boolean magicDefends = npc.hasRule("cox-magic-defensive");
		int defence = npc.defence;
		int magic = npc.magicLevel;
		if (npc.hasRule("cox-singles"))
		{
			int scaler = Math.max(55, Math.min(99, highestHitpoints));
			scaler = challengeMode ? addPercent(scaler, CHALLENGE_MODE_PERCENT) : scaler;
			defence = defence == 1 ? 1 : Math.max(1, defence * scaler / 99);
			magic = magic == 1 ? 1 : Math.max(1, magic * scaler / 99);
			return new NpcStats.Npc(npc, defence, magic, 0);
		}
		int hitpoints = Math.max(55, Math.min(99, 55 + 44 * highestHitpoints / 99));
		int others = Math.min(partySize, 100) - 1;
		int defencePercent = 100 + (int) Math.sqrt(others) + others * 7 / 10;
		int attackPercent = 100 + (int) Math.sqrt(others) * 7 + others;
		if (defence != 1)
		{
			defence = scaleDefence(npc, defence, hitpoints, defencePercent);
		}
		if (magic != 1)
		{
			magic = magicDefends ? scaleDefence(npc, magic, hitpoints, defencePercent)
				: scaleAttack(magic, hitpoints, attackPercent);
		}
		if (npc.hasRule("olm-mage-hand"))
		{
			magic /= 2;
		}
		return new NpcStats.Npc(npc, defence, magic, 0);
	}

	private int scaleDefence(NpcStats.Npc npc, int level, int hitpoints, int percent)
	{
		int scaled = level * hitpoints / 99 * percent / 100;
		if (challengeMode && !npc.hasRule("cox-not-cm"))
		{
			// Tekton gets less, so specs on it stay possible
			int cm = npc.hasRule("tekton") ? (partySize < 4 ? 20 : 35) : CHALLENGE_MODE_PERCENT;
			scaled = addPercent(scaled, cm);
		}
		return Math.max(50, Math.min(20_000, scaled));
	}

	private int scaleAttack(int level, int hitpoints, int percent)
	{
		int scaled = level * hitpoints / 99 * percent / 100;
		scaled = challengeMode ? addPercent(scaled, CHALLENGE_MODE_PERCENT) : scaled;
		return Math.max(50, Math.min(5_000, scaled));
	}

	private static int addPercent(int value, int percent)
	{
		return value + value * percent / 100;
	}

	public boolean inTombsOfAmascut()
	{
		return RaidZone.TOA.equals(raid);
	}
}
