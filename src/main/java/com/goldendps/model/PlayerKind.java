package com.goldendps.model;

public enum PlayerKind
{
	/** You. */
	LOCAL,
	/** A party member who runs the plugin. */
	PARTY,
	/**
	 * A player near you without GoldenDPS. Damage is a guess (attack animation + timing),
	 * damage taken is the hitsplats you saw on them.
	 */
	ESTIMATE,
	/** Damage on the target that nobody reported (players without the plugin). */
	UNKNOWN,
	/** Poison, venom, burn, bleed: the game does not say who did it. */
	EFFECTS
}
