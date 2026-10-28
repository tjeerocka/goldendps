package com.goldendps.model;

/** Where a heal came from. Decided from timing, see {@link HealClassifier}. */
public enum HealType
{
	/** Ate or drank in the last 2 ticks. */
	FOOD,
	/** Hit something this tick or last tick (Sanguinesti, blood spells, Soulreaper axe...). */
	LIFESTEAL,
	/** Small +1/+2 with no other reason (natural regen). Not counted unless turned on. */
	REGEN,
	/** HP given to another player (Heal Other / Heal Group). */
	HEAL_OTHER,
	/** HP went up for no reason we know (for example someone cast Heal Other on us). Not credited. */
	RECEIVED
}
