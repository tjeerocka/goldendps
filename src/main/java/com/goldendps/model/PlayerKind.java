package com.goldendps.model;

public enum PlayerKind
{
	/** You. */
	LOCAL,
	/** A party member who runs the plugin. */
	PARTY,
	/** Damage on the target that nobody reported (players without the plugin). */
	UNKNOWN,
	/** Poison, venom, burn, bleed: the game does not say who did it. */
	EFFECTS
}
