package com.goldendps.model;

public enum PlayerKind
{
	LOCAL,
	/** Party member running GoldenDPS. */
	PARTY,
	/** Player without GoldenDPS; damage is estimated from attack animations. */
	ESTIMATE,
	/** Damage nobody reported. */
	UNKNOWN,
	/** Poison, venom, burn and bleed. */
	EFFECTS
}
