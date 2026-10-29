package com.goldendps;

import com.goldendps.model.MeterMode;
import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(GoldenDpsConfig.GROUP)
public interface GoldenDpsConfig extends Config
{
	String GROUP = "goldendps";
	String MODE_KEY = "overlayMode";
	String BOSS_ONLY_KEY = "bossOnly";
	String SCOPE_KEY = "overlayScope";
	/** Hidden: the party you were in, to join it again after a relog or a client restart. */
	String LAST_PARTY_KEY = "lastParty";

	enum Scope
	{
		FIGHT("Current / last fight"),
		SESSION("Session total");

		private final String label;

		Scope(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigSection(name = "Fights", description = "When a fight starts and ends", position = 0)
	String fightSection = "fights";

	@ConfigSection(name = "Overlay", description = "The bars on the game screen", position = 1)
	String overlaySection = "overlay";

	@ConfigItem(
		keyName = "idleSeconds",
		name = "End fight after",
		description = "The fight ends when nobody hit anything for this long (or when the boss dies). AFK time is not counted in DPS (see Pause clock after).",
		position = 0,
		section = fightSection
	)
	@Range(min = 3, max = 3600)
	@Units(Units.SECONDS)
	default int idleSeconds()
	{
		return 300;
	}

	@ConfigItem(
		keyName = "pauseSeconds",
		name = "Pause clock after",
		description = "Active time, like Details!: when nobody hits for this long, the fight clock stops until the next hit, so AFK time does not lower DPS. 0 = never pause.",
		position = 0,
		section = fightSection
	)
	@Range(min = 0, max = 120)
	@Units(Units.SECONDS)
	default int pauseSeconds()
	{
		return 10;
	}

	@ConfigItem(
		keyName = BOSS_ONLY_KEY,
		name = "Boss only",
		description = "Only count damage on bosses (non-boss targets are hidden)",
		position = 1,
		section = fightSection
	)
	default boolean bossOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "extraBosses",
		name = "Extra bosses",
		description = "More NPC names to treat as a boss, comma separated (for example: Scurrius, Duke Sucellus)",
		position = 2,
		section = fightSection
	)
	default String extraBosses()
	{
		return "";
	}

	@ConfigItem(
		keyName = "countRegen",
		name = "Count natural regen",
		description = "Count small +1/+2 HP regen as healing",
		position = 3,
		section = fightSection
	)
	default boolean countRegen()
	{
		return false;
	}

	@ConfigItem(
		keyName = "maxHistory",
		name = "Fights to keep",
		description = "How many past fights are saved",
		position = 4,
		section = fightSection
	)
	@Range(min = 5, max = 500)
	default int maxHistory()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "shareWithParty",
		name = "Share with party",
		description = "Send your numbers to your RuneLite party (they need GoldenDPS too)",
		position = 5,
		section = fightSection
	)
	default boolean shareWithParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "estimateOthers",
		name = "Guess other players",
		description = "Guess who did the hits of players without GoldenDPS (from their attack animation and timing). Marked with ~",
		position = 6,
		section = fightSection
	)
	default boolean estimateOthers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nearbyDamageTaken",
		name = "Damage taken of others",
		description = "Count the hitsplats you see on players without GoldenDPS as their damage taken",
		position = 7,
		section = fightSection
	)
	default boolean nearbyDamageTaken()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoRaidParty",
		name = "Auto party in raids",
		description = "In ToB, ToA and the Fractured Archive, everyone with GoldenDPS joins the same party by "
			+ "itself (made from the raid members' names). Never changes a party you joined yourself.",
		position = 8,
		section = fightSection
	)
	default boolean autoRaidParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "raidFight",
		name = "One fight per raid",
		description = "In CoX, ToB, ToA and the Fractured Archive the meter starts fresh when you enter the raid "
			+ "and counts the whole raid until you leave. Each boss still gets its own boss meter and fight.",
		position = 9,
		section = fightSection
	)
	default boolean raidFight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the damage bars on the game screen",
		position = 0,
		section = overlaySection
	)
	default boolean showOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = MODE_KEY,
		name = "Show",
		description = "What the bars show. You can also right-click the overlay.",
		position = 1,
		section = overlaySection
	)
	default MeterMode overlayMode()
	{
		return MeterMode.DAMAGE;
	}

	@ConfigItem(
		keyName = SCOPE_KEY,
		name = "Data",
		description = "Current (or last) fight, or the total of this session",
		position = 2,
		section = overlaySection
	)
	default Scope overlayScope()
	{
		return Scope.FIGHT;
	}

	@ConfigItem(
		keyName = "maxRows",
		name = "Max bars",
		description = "How many bars to show",
		position = 3,
		section = overlaySection
	)
	@Range(min = 1, max = 20)
	default int maxRows()
	{
		return 8;
	}

	@ConfigItem(
		keyName = "overlayWidth",
		name = "Width",
		description = "Overlay width in pixels",
		position = 4,
		section = overlaySection
	)
	@Range(min = 140, max = 400)
	@Units(Units.PIXELS)
	default int overlayWidth()
	{
		return 240;
	}

	@ConfigItem(
		keyName = "hideWhenEmpty",
		name = "Hide when empty",
		description = "Hide the overlay when there is nothing to show",
		position = 5,
		section = overlaySection
	)
	default boolean hideWhenEmpty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "myColor",
		name = "My bar color",
		description = "Color of your own bar",
		position = 6,
		section = overlaySection
	)
	default Color myColor()
	{
		return new Color(255, 196, 0);
	}

	@ConfigItem(
		keyName = "bossMeter",
		name = "Boss meter",
		description = "Extra bars under the main meter with only the damage on the boss, and DPS over the boss time",
		position = 7,
		section = overlaySection
	)
	default boolean bossMeter()
	{
		return true;
	}

	@ConfigItem(
		keyName = "bossHideSeconds",
		name = "Hide boss meter after",
		description = "How long the boss meter stays after the boss died. A new boss fight shows a new one.",
		position = 8,
		section = overlaySection
	)
	@Range(min = 3, max = 600)
	@Units(Units.SECONDS)
	default int bossHideSeconds()
	{
		return 20;
	}
}
