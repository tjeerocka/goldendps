package com.goldendps.model;

/** Source of a heal, decided by {@link HealthWatcher} from timing. */
public enum HealType
{
	FOOD,
	LIFESTEAL,
	/** Natural regeneration; only counted when enabled. */
	REGEN,
	/** Heal Other / Heal Group. */
	HEAL_OTHER,
	/** Healed by someone else; not credited to you. */
	RECEIVED
}
