package com.goldendps.model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** What decides your hit chance: levels, prayers, gear and attack style. Filled on the client thread. */
public class PlayerState
{
	/** 0 when you hold no weapon. */
	public int weaponId;
	/** null when you hold no weapon. */
	public String weaponName;
	/** The selected style in the combat tab (0-3, 4 for autocast). */
	public int styleSlot;
	/** Boosted levels. */
	public int attack;
	public int ranged;
	public int magic;
	/** Names of the active prayers, like "PIETY". */
	public Set<String> prayers = new HashSet<>();
	/** Attack bonuses of all your gear together. */
	public int stabBonus;
	public int slashBonus;
	public int crushBonus;
	public int magicBonus;
	public int rangedBonus;
	/** Your special attack is turned on: the next attack is a spec. */
	public boolean specialAttack;
	/** A spell you cast by clicking it, like "Ice Barrage"; null when you attack with your weapon or autocast. */
	public String clickedSpell;
	/** "fire", "water", "earth" or "air" when you autocast an elemental spell, else null. */
	public String autocastElement;
	/** You autocast a demonbane spell (with the purging staff, for example). */
	public boolean autocastDemonbane;
	/** In the Wilderness: charged revenant weapons hit better. */
	public boolean inWilderness;
	/** Soul stacks of the Soulreaper axe: its spec gets 12% more accuracy for each. */
	public int soulreaperStacks;
	/** Mark of Darkness is on: demonbane spells hit demons better. */
	public boolean markOfDarkness;
	/** On the standard spellbook: smoke staves help its spells. */
	public boolean standardSpellbook;
	/** The monster is on your slayer task (from RuneLite's Slayer plugin). */
	public boolean onTask;
	/** Names of the items you wear. */
	public List<String> worn = new ArrayList<>();
}
