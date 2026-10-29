package com.goldendps.model;

/**
 * Works out what made each of your hits: the weapon, a special attack or a spell you clicked.
 * Used for the breakdown per weapon / spell (like the Details! spell breakdown).
 * <ul>
 * <li>An attack animation saves the weapon you hold (or the spell you clicked just before).</li>
 * <li>Spec energy going down marks the next hits (that tick and the one after, for claws) as "(spec)".</li>
 * <li>Hits use the last attack; without a recent attack, the weapon you hold now.</li>
 * </ul>
 */
public class AttackSource
{
	public static final String UNARMED = "Unarmed";
	/** A clicked spell counts for an attack that starts within this many ticks (you may walk first). */
	static final int SPELL_TICKS = 20;
	/** A hit belongs to the last attack when it lands within this many ticks. */
	static final int ATTACK_TICKS = 10;
	/** Spec hits land within this many ticks after the spec (dark bow, ballista). */
	static final int SPEC_TICKS = 6;

	private String spell;
	private int spellTick = -1;
	private String lastAttack;
	private int attackTick = -1;
	private String specWeapon;
	private int specTick = -1;
	private int specHitTick = -1;

	/** You clicked Cast with this spell on an NPC. */
	public void castClicked(int tick, String spellName)
	{
		spell = clean(spellName);
		spellTick = tick;
	}

	/** Your attack animation started. */
	public void attack(int tick, String weapon)
	{
		if (spell != null && tick - spellTick <= SPELL_TICKS && tick >= spellTick)
		{
			lastAttack = spell;
		}
		else
		{
			lastAttack = weaponName(weapon);
		}
		spell = null;
		attackTick = tick;
	}

	/** Your spec energy went down. */
	public void specUsed(int tick, String weapon)
	{
		specWeapon = weaponName(weapon);
		specTick = tick;
		specHitTick = -1;
	}

	/** The source of a hitsplat of yours that shows now. */
	public String sourceForHit(int tick, String weaponNow)
	{
		if (specTick >= 0)
		{
			int since = tick - specTick;
			if (since >= 0 && since <= SPEC_TICKS && (specHitTick < 0 || tick <= specHitTick + 1))
			{
				if (specHitTick < 0)
				{
					specHitTick = tick;
				}
				return specWeapon + " (spec)";
			}
			if (since > SPEC_TICKS || specHitTick >= 0)
			{
				specTick = -1;
			}
		}
		if (lastAttack != null && tick - attackTick >= 0 && tick - attackTick <= ATTACK_TICKS)
		{
			return lastAttack;
		}
		return weaponName(weaponNow);
	}

	public void reset()
	{
		spell = null;
		spellTick = -1;
		lastAttack = null;
		attackTick = -1;
		specWeapon = null;
		specTick = -1;
		specHitTick = -1;
	}

	private static String weaponName(String weapon)
	{
		String w = clean(weapon);
		return w == null ? UNARMED : w;
	}

	/** "Ice Barrage -> Vorkath  (level-732)" gives "Ice Barrage". */
	static String clean(String s)
	{
		if (s == null)
		{
			return null;
		}
		int arrow = s.indexOf("->");
		String c = (arrow >= 0 ? s.substring(0, arrow) : s).replace(' ', ' ').trim();
		return c.isEmpty() ? null : c;
	}
}
