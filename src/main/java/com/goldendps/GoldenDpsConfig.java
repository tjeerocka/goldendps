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

	@ConfigSection(name = "Party", description = "Sharing with your RuneLite party", position = 1)
	String partySection = "party";

	@ConfigSection(name = "Overlay", description = "The meter on the game screen", position = 2)
	String overlaySection = "overlay";

	@ConfigSection(name = "Fun", description = "Extras for group fights", position = 3)
	String funSection = "fun";

	@ConfigItem(
		keyName = "idleSeconds",
		name = "End fight after",
		description = "The fight ends when the boss dies or nobody hits anything for this long",
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
		description = "The fight clock stops when nobody hits for this long, so AFK time does not lower DPS."
			+ " 0 disables it.",
		position = 1,
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
		description = "Only count damage on bosses",
		position = 2,
		section = fightSection
	)
	default boolean bossOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "extraBosses",
		name = "Extra bosses",
		description = "More NPC names to treat as bosses, comma separated",
		position = 3,
		section = fightSection
	)
	default String extraBosses()
	{
		return "";
	}

	@ConfigItem(
		keyName = "countRegen",
		name = "Count natural regen",
		description = "Count natural HP regeneration as healing",
		position = 4,
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
		position = 5,
		section = fightSection
	)
	@Range(min = 5, max = 500)
	default int maxHistory()
	{
		return 50;
	}

	@ConfigItem(
		keyName = "raidFight",
		name = "One fight per raid",
		description = "In CoX, ToB, ToA and the Fractured Archive, one fight counts the whole raid until you leave."
			+ " Every boss also gets its own fight.",
		position = 6,
		section = fightSection
	)
	default boolean raidFight()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareWithParty",
		name = "Share with party",
		description = "Send your numbers to party members who run GoldenDPS",
		position = 0,
		section = partySection
	)
	default boolean shareWithParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "othersOnlyTogether",
		name = "Others only when together",
		description = "Count party members only when you are in the same instance or fight the same boss",
		position = 1,
		section = partySection
	)
	default boolean othersOnlyTogether()
	{
		return true;
	}

	@ConfigItem(
		keyName = "estimateOthers",
		name = "Guess other players",
		description = "Estimate the damage of players without GoldenDPS from their attack animations (marked with ~)",
		position = 2,
		section = partySection
	)
	default boolean estimateOthers()
	{
		return true;
	}

	@ConfigItem(
		keyName = "nearbyDamageTaken",
		name = "Damage taken of others",
		description = "Count the hitsplats you see on players without GoldenDPS as their damage taken",
		position = 3,
		section = partySection
	)
	default boolean nearbyDamageTaken()
	{
		return true;
	}

	@ConfigItem(
		keyName = "autoRaidParty",
		name = "Raid party",
		description = "In CoX, ToB, ToA and the Fractured Archive, offer a one-click party with everyone in your"
			+ " raid who runs GoldenDPS, and leave it after the raid.",
		position = 4,
		section = partySection
	)
	default boolean autoRaidParty()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show overlay",
		description = "Show the meter on the game screen",
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
		description = "What the bars show. Also in the overlay right-click menu.",
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
		description = "The current or last fight, or the total of this session",
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
		description = "Overlay width",
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
		description = "A second meter with only the damage on the boss, over the boss time",
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
		description = "How long the boss meter stays after the boss died",
		position = 8,
		section = overlaySection
	)
	@Range(min = 3, max = 600)
	@Units(Units.SECONDS)
	default int bossHideSeconds()
	{
		return 20;
	}


	@ConfigItem(
		keyName = "mvpCrown",
		name = "Crown on the MVP",
		description = "A crown over the player with the most damage in a group fight",
		position = 0,
		section = funSection
	)
	default boolean mvpCrown()
	{
		return true;
	}

	@ConfigItem(
		keyName = "healerSnack",
		name = "Snack on the top healer",
		description = "A burger or fries and hungry lines over the player who healed the most in a group fight",
		position = 1,
		section = funSection
	)
	default boolean healerSnack()
	{
		return true;
	}

	@ConfigItem(
		keyName = "maxHitPopup",
		name = "Max hit announcement",
		description = "A line over the head and in the chat when someone beats their biggest hit of the session",
		position = 2,
		section = funSection
	)
	default boolean maxHitPopup()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showMaxHit",
		name = "Max hit on the meter",
		description = "Show the biggest hit of the fight under the meter",
		position = 3,
		section = funSection
	)
	default boolean showMaxHit()
	{
		return true;
	}
}
