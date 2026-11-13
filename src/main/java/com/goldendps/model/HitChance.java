package com.goldendps.model;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Your chance to hit a monster with your next normal attack, with the formulas of the OSRS Wiki
 * ("Damage per second" pages), for a normal attack, a special attack or a spell you click.
 */
public final class HitChance
{
	private HitChance()
	{
	}

	/**
	 * @param weaponStyle the style selected for your weapon
	 * @param defenceLevel the monster's defence now, lowered by special attacks
	 * @param magicLevel the monster's magic level now, lowered by special attacks
	 */
	public static double of(PlayerState p, WeaponStyles.Style weaponStyle, NpcStats.Npc npc, int defenceLevel,
		int magicLevel)
	{
		WeaponStyles.Style style = p.clickedSpell != null ? WeaponStyles.CLICKED_SPELL : weaponStyle;
		if (alwaysHit(npc, style.type, p.clickedSpell != null))
		{
			return 1;
		}
		Spec spec = p.specialAttack && p.clickedSpell == null ? Spec.of(p.weaponName) : null;
		WeaponStyles.Type defendedWith = spec != null && spec.defendedWith != null ? spec.defendedWith : style.type;
		long roll = attackRoll(p, style, npc);
		if (spec != null)
		{
			roll = roll * spec.accuracyTimes / spec.accuracyPer;
		}
		if (spec != null && p.weaponName.toLowerCase(Locale.ROOT).startsWith("soulreaper axe"))
		{
			roll = roll * (100 + 12 * p.soulreaperStacks) / 100;
		}
		long defence = defenceRoll(defendedWith, npc, defenceLevel, magicLevel);
		if (spec != null && spec.rolls > 1)
		{
			// a spec that rolls again after a miss hits when any roll hits
			return 1 - Math.pow(1 - chance(roll, defence), spec.rolls);
		}
		String weapon = p.weaponName == null ? "" : p.weaponName.toLowerCase(Locale.ROOT);
		if (defendedWith == WeaponStyles.Type.STAB && weapon.startsWith("osmumten's fang"))
		{
			return fangChance(roll, defence);
		}
		if (defendedWith == WeaponStyles.Type.MAGIC && wears(p, "brimstone ring"))
		{
			// a quarter of the time it ignores 10% of the monster's magic defence
			return 0.75 * chance(roll, defence) + 0.25 * chance(roll, defence * 9 / 10);
		}
		return chance(roll, defence);
	}

	/**
	 * Osmumten's fang rolls its accuracy twice and keeps the better roll, so it hits more often than one roll.
	 * The exact chance for two rolls, from the OSRS Wiki DPS calculator.
	 */
	static double fangChance(long attackRoll, long defenceRoll)
	{
		double a = attackRoll < 0 ? Math.min(0, attackRoll + 2) : attackRoll;
		double d = defenceRoll < 0 ? Math.min(0, defenceRoll + 2) : defenceRoll;
		if (a >= 0 && d >= 0)
		{
			if (a > d)
			{
				return 1 - (d + 2) * (2 * d + 3) / (a + 1) / (a + 1) / 6;
			}
			return a * (4 * a + 5) / 6 / (a + 1) / (d + 1);
		}
		if (a >= 0)
		{
			return 1 - 1 / (-d + 1) / (a + 1);
		}
		// both negative: swap them, like the calculator does
		return d >= 0 ? 0 : 1 - (-a + 2) * (2 * -a + 3) / 6 / (-a + 1) / (-d + 1);
	}
	/** What a special attack changes: more accuracy, and some hit another defence than your style. */
	static final class Spec
	{
		private static final Map<String, Spec> BY_WEAPON = new LinkedHashMap<>();

		static
		{
			// from the OSRS Wiki DPS calculator; a weapon not listed specs like a normal attack
			for (String godsword : new String[]{"armadyl", "bandos", "saradomin", "zamorak", "ancient"})
			{
				add(godsword + " godsword", 2, 1, WeaponStyles.Type.SLASH);
			}
			add("osmumten's fang", 3, 2, null);
			add("arkan blade", 3, 2, WeaponStyles.Type.SLASH);
			add("granite hammer", 3, 2, null);
			add("elder maul", 5, 4, null);
			add("dragon mace", 5, 4, WeaponStyles.Type.CRUSH);
			add("dragon sword", 5, 4, WeaponStyles.Type.STAB);
			add("dragon scimitar", 5, 4, WeaponStyles.Type.SLASH);
			add("abyssal whip", 5, 4, null);
			add("dragon dagger", 23, 20, WeaponStyles.Type.SLASH);
			add("abyssal dagger", 5, 4, WeaponStyles.Type.SLASH);
			add("brine sabre", 2, 1, null);
			add("barrelchest anchor", 2, 1, null);
			add("dragon claws", 1, 1, WeaponStyles.Type.SLASH).rolls = 4;
			add("dragon halberd", 1, 1, WeaponStyles.Type.SLASH);
			add("crystal halberd", 1, 1, WeaponStyles.Type.SLASH);
			add("dragon longsword", 1, 1, WeaponStyles.Type.SLASH);
			add("saradomin sword", 1, 1, WeaponStyles.Type.SLASH);
			add("arclight", 1, 1, WeaponStyles.Type.STAB);
			add("emberlight", 1, 1, WeaponStyles.Type.STAB);
			add("voidwaker", 1, 1, WeaponStyles.Type.MAGIC);
			add("saradomin's blessed sword", 1, 1, WeaponStyles.Type.MAGIC);
			add("crimson kisten", 1, 1, WeaponStyles.Type.CRUSH);
			add("zaryte crossbow", 2, 1, null);
			add("webweaver bow", 2, 1, null);
			add("toxic blowpipe", 2, 1, null);
			add("blazing blowpipe", 2, 1, null);
			add("magic shortbow", 10, 7, null);
			add("heavy ballista", 5, 4, null);
			add("light ballista", 5, 4, null);
			add("tonalztics of ralos", 3, 2, null);
			add("accursed sceptre", 3, 2, null);
			add("volatile nightmare staff", 3, 2, null);
			add("eye of ayak", 2, 1, null);
			add("zorya's tome", 5, 2, null);
		}

		final int accuracyTimes;
		final int accuracyPer;
		/** Dragon claws roll up to four times. */
		int rolls = 1;
		/** null: the defence of your style. */
		final WeaponStyles.Type defendedWith;

		private Spec(int accuracyTimes, int accuracyPer, WeaponStyles.Type defendedWith)
		{
			this.accuracyTimes = accuracyTimes;
			this.accuracyPer = accuracyPer;
			this.defendedWith = defendedWith;
		}

		private static Spec add(String weapon, int times, int per, WeaponStyles.Type defendedWith)
		{
			Spec spec = new Spec(times, per, defendedWith);
			BY_WEAPON.put(weapon, spec);
			return spec;
		}

		/** The special attack of this weapon; a weapon not listed specs like a normal attack. */
		static Spec of(String weaponName)
		{
			String weapon = weaponName == null ? "" : weaponName.toLowerCase(Locale.ROOT);
			for (Map.Entry<String, Spec> e : BY_WEAPON.entrySet())
			{
				if (weapon.startsWith(e.getKey()))
				{
					return e.getValue();
				}
			}
			return new Spec(1, 1, null);
		}
	}

	/** Some monsters are always hit: by every style, by one, or by anything but a spell you click. */
	static boolean alwaysHit(NpcStats.Npc npc, WeaponStyles.Type type, boolean clickedSpell)
	{
		String skill = type.isMelee() ? "melee" : type.isRanged() ? "ranged" : "magic";
		return npc.hasRule("always-hit") || npc.hasRule("always-hit-" + skill)
			|| npc.hasRule("always-hit-not-clicked") && !clickedSpell;
	}

	/** The game's hit chance; a roll can be negative with very low (or negative) bonuses. */
	static double chance(long attackRoll, long defenceRoll)
	{
		long attack = attackRoll < 0 ? Math.min(0, attackRoll + 2) : attackRoll;
		long defence = defenceRoll < 0 ? Math.min(0, defenceRoll + 2) : defenceRoll;
		if (attack >= 0 && defence >= 0)
		{
			return positiveChance(attack, defence);
		}
		if (attack >= 0)
		{
			return 1 - 1.0 / (-defence + 1) / (attack + 1);
		}
		return defence >= 0 ? 0 : positiveChance(-defence, -attack);
	}

	private static double positiveChance(long attack, long defence)
	{
		if (attack > defence)
		{
			return 1 - (defence + 2) / (2.0 * (attack + 1));
		}
		return attack / (2.0 * (defence + 1));
	}
	static long defenceRoll(WeaponStyles.Type type, NpcStats.Npc npc, int defenceLevel, int magicLevel)
	{
		// magic is blocked with the magic level (a few monsters use defence), so lowered defence does not help magic
		boolean magicLevelBlocks = type == WeaponStyles.Type.MAGIC && !npc.hasRule("magic-uses-defence");
		int level = magicLevelBlocks ? magicLevel : defenceLevel;
		return (long) (level + 9) * (defenceBonus(type, npc) + 64);
	}

	private static int defenceBonus(WeaponStyles.Type type, NpcStats.Npc npc)
	{
		switch (type)
		{
			case STAB:
				return npc.stab;
			case SLASH:
				return npc.slash;
			case CRUSH:
				return npc.crush;
			case MAGIC:
				return npc.magic;
			case LIGHT:
				return npc.light;
			case STANDARD:
				return npc.standard;
			case HEAVY:
				return npc.heavy;
			default:
				return (npc.light + npc.standard + npc.heavy) / 3;
		}
	}

	static long attackRoll(PlayerState p, WeaponStyles.Style style, NpcStats.Npc npc)
	{
		WeaponStyles.Type type = style.type;
		String weapon = p.weaponName == null ? "" : p.weaponName.toLowerCase(Locale.ROOT);
		if (type == WeaponStyles.Type.MAGIC)
		{
			long level = p.magic * (100L + prayerPercent(p, MAGIC_PRAYERS, MAGIC_PERCENT)) / 100 + style.stanceBonus + 9;
			if (hasVoid(p, "void mage helm"))
			{
				level = level * 29 / 20;
			}
			long baseRoll = level * (magicBonus(p) + 64);
			return magicBoosts(p, npc, weapon, baseRoll) + elementalWeakness(p, npc, baseRoll);
		}
		boolean ranged = type.isRanged();
		long level = ranged
			? p.ranged * (100L + prayerPercent(p, RANGED_PRAYERS, RANGED_PERCENT)) / 100
			: p.attack * (100L + prayerPercent(p, MELEE_PRAYERS, MELEE_PERCENT)) / 100;
		level += style.stanceBonus + 8;
		if (hasVoid(p, ranged ? "void ranger helm" : "void melee helm"))
		{
			level = level * 11 / 10;
		}
		long baseRoll = level * (attackBonus(p, type) + 64);
		return ranged ? rangedBoosts(p, npc, weapon, baseRoll) : meleeBoosts(p, type, npc, weapon, baseRoll);
	}

	/*
	 * Gear that hits better, often only some monsters. In the order of the OSRS Wiki DPS calculator, as the
	 * rounding depends on it. A salve amulet, slayer helmet and amulet of avarice do not stack: one counts.
	 */

	private static long meleeBoosts(PlayerState p, WeaponStyles.Type type, NpcStats.Npc npc, String weapon,
		long baseRoll)
	{
		long roll = baseRoll;
		if (npc.name.startsWith("Revenant") && wears(p, "amulet of avarice"))
		{
			roll = roll * 24 / 20;
		}
		else if (npc.is("undead") && (wears(p, "salve amulet(ei)") || wears(p, "salve amulet (e)")))
		{
			roll = roll * 6 / 5;
		}
		else if (npc.is("undead") && wears(p, "salve amulet"))
		{
			roll = roll * 7 / 6;
		}
		else if (p.onTask && wearsSlayerHelmet(p, false))
		{
			roll = roll * 7 / 6;
		}
		if ((weapon.startsWith("tzhaar-") || weapon.startsWith("toktz-"))
			&& wears(p, "obsidian helmet") && wears(p, "obsidian platebody") && wears(p, "obsidian platelegs"))
		{
			roll += baseRoll / 10;
		}
		if (p.inWilderness && isChargedRevenantWeapon(weapon, type))
		{
			roll = roll * 3 / 2;
		}
		if (npc.is("demon"))
		{
			if (weapon.startsWith("arclight") || weapon.startsWith("emberlight"))
			{
				roll += roll * 70 / 100;
			}
			else if (weapon.startsWith("silverlight") || weapon.startsWith("darklight"))
			{
				roll += roll * 60 / 100;
			}
			else if (weapon.startsWith("bone claws") || weapon.startsWith("burning claws"))
			{
				roll += roll * 5 / 100;
			}
		}
		if (npc.is("dragon") && weapon.startsWith("dragon hunter lance"))
		{
			roll = roll * 6 / 5;
		}
		if (npc.is("kalphite") && weapon.startsWith("keris partisan of breaching"))
		{
			roll = roll * 133 / 100;
		}
		if (isVampyre(npc))
		{
			if (weapon.startsWith("blisterwood flail") || weapon.startsWith("blisterwood sickle"))
			{
				roll = roll * 105 / 100;
			}
			if (weapon.startsWith("hallowed flail") || weapon.startsWith("sunspear"))
			{
				roll = roll * 125 / 100;
			}
			if (wears(p, "efaritay's aid") && isSilverWeapon(weapon))
			{
				roll = roll * 23 / 20;
			}
		}
		if (npc.is("golem") && weapon.startsWith("granite hammer"))
		{
			roll = roll * 13 / 10;
		}
		if (type == WeaponStyles.Type.CRUSH)
		{
			int inquisitor = (wears(p, "inquisitor's great helm") ? 1 : 0) + (wears(p, "inquisitor's hauberk") ? 2 : 0)
				+ (wears(p, "inquisitor's plateskirt") ? 2 : 0);
			roll = roll * (200 + inquisitor) / 200;
		}
		return roll;
	}

	private static long rangedBoosts(PlayerState p, NpcStats.Npc npc, String weapon, long baseRoll)
	{
		long roll = baseRoll;
		if (weapon.startsWith("crystal bow") || weapon.contains("bow of faerdhinen"))
		{
			roll = roll * (20 + crystalArmourPieces(p)) / 20;
		}
		if (npc.name.startsWith("Revenant") && wears(p, "amulet of avarice"))
		{
			roll = roll * 24 / 20;
		}
		else if (npc.is("undead") && wears(p, "salve amulet(ei)"))
		{
			roll = roll * 6 / 5;
		}
		else if (npc.is("undead") && wears(p, "salve amulet(i)"))
		{
			roll = roll * 7 / 6;
		}
		else if (p.onTask && wearsSlayerHelmet(p, true))
		{
			roll = roll * 23 / 20;
		}
		if (weapon.startsWith("twisted bow"))
		{
			roll = roll * twistedBowPercent(npc) / 100;
		}
		if (p.inWilderness && isChargedRevenantWeapon(weapon, WeaponStyles.Type.STANDARD))
		{
			roll = roll * 3 / 2;
		}
		if (npc.is("dragon") && weapon.startsWith("dragon hunter crossbow"))
		{
			roll = roll * 13 / 10;
		}
		if (npc.is("demon") && weapon.startsWith("scorching bow"))
		{
			roll += roll * 30 / 100;
		}
		return roll;
	}

	private static long magicBoosts(PlayerState p, NpcStats.Npc npc, String weapon, long baseRoll)
	{
		// these percents add up before they multiply
		int percent = 0;
		boolean slayerHelmet = false;
		if (npc.name.startsWith("Revenant") && wears(p, "amulet of avarice"))
		{
			percent += 20;
		}
		else if (npc.is("undead") && wears(p, "salve amulet(ei)"))
		{
			percent += 20;
		}
		else if (npc.is("undead") && wears(p, "salve amulet(i)"))
		{
			percent += 15;
		}
		else
		{
			slayerHelmet = p.onTask && wearsSlayerHelmet(p, true);
		}
		if (p.standardSpellbook && (weapon.contains("smoke battlestaff") || weapon.startsWith("mystic smoke staff")
			|| weapon.startsWith("twinflame staff")))
		{
			percent += 10;
		}
		long roll = baseRoll * (100 + percent) / 100;
		if (npc.is("dragon"))
		{
			if (weapon.startsWith("dragon hunter crossbow"))
			{
				roll = roll * 13 / 10;
			}
			else if (weapon.startsWith("dragon hunter lance"))
			{
				roll = roll * 6 / 5;
			}
			else if (weapon.startsWith("dragon hunter wand"))
			{
				roll = roll * 7 / 4;
			}
		}
		if (slayerHelmet)
		{
			roll = roll * 23 / 20;
		}
		boolean demonbaneSpell = p.clickedSpell != null ? p.clickedSpell.contains("Demonbane") : p.autocastDemonbane;
		if (npc.is("demon") && demonbaneSpell)
		{
			int demonbane = p.markOfDarkness ? 40 : 20;
			if (weapon.startsWith("purging staff"))
			{
				demonbane *= 2;
			}
			roll += roll * demonbane / 100;
		}
		if (p.inWilderness && isChargedRevenantWeapon(weapon, WeaponStyles.Type.MAGIC))
		{
			roll = roll * 3 / 2;
		}
		String element = p.clickedSpell != null ? elementOf(p.clickedSpell) : p.autocastElement;
		if ("water".equals(element) && wears(p, "tome of water") && !wears(p, "tome of water (empty)"))
		{
			roll = roll * 6 / 5;
		}
		return roll;
	}

	/** A black mask or slayer helmet; only the imbued ones help ranged and magic. */
	private static boolean wearsSlayerHelmet(PlayerState p, boolean imbuedOnly)
	{
		for (String item : p.worn)
		{
			String name = item == null ? "" : item.toLowerCase(Locale.ROOT);
			if ((name.startsWith("black mask") || name.contains("slayer helmet")) && (!imbuedOnly || name.contains("(i)")))
			{
				return true;
			}
		}
		return false;
	}

	private static boolean isVampyre(NpcStats.Npc npc)
	{
		return npc.is("vampyre1") || npc.is("vampyre2") || npc.is("vampyre3");
	}

	private static boolean isSilverWeapon(String weapon)
	{
		for (String silver : new String[]{"silverlight", "darklight", "silver sickle", "rod of ivandis", "ivandis flail",
			"blisterwood", "hallowed", "sunspear"})
		{
			if (weapon.startsWith(silver))
			{
				return true;
			}
		}
		return false;
	}

	/** Tumeken's shadow triples the magic attack bonus of your gear (not for a spell you click). */
	private static int magicBonus(PlayerState p)
	{
		boolean shadow = p.weaponName != null && p.weaponName.toLowerCase(Locale.ROOT).startsWith("tumeken's shadow");
		return shadow && p.clickedSpell == null ? p.magicBonus * 3 : p.magicBonus;
	}

	/**
	 * A monster weak to an element: elemental spells (strike, bolt, blast, wave, surge) get its weakness
	 * percent of the attack roll on top, before gear boosts.
	 */
	private static long elementalWeakness(PlayerState p, NpcStats.Npc npc, long roll)
	{
		String element = p.clickedSpell != null ? elementOf(p.clickedSpell) : p.autocastElement;
		return element != null && element.equals(npc.weakElement) ? roll * npc.weaknessPercent / 100 : 0;
	}

	/** "fire" for "Fire Surge"; null for spells that are not elemental strikes, bolts, blasts, waves or surges. */
	public static String elementOf(String spell)
	{
		String[] words = spell.toLowerCase(Locale.ROOT).split(" ");
		if (words.length != 2 || !Arrays.asList("strike", "bolt", "blast", "wave", "surge").contains(words[1]))
		{
			return null;
		}
		switch (words[0])
		{
			case "wind":
				return "air";
			case "water":
			case "earth":
			case "fire":
				return words[0];
			default:
				return null;
		}
	}

	private static int attackBonus(PlayerState p, WeaponStyles.Type type)
	{
		switch (type)
		{
			case STAB:
				return p.stabBonus;
			case SLASH:
				return p.slashBonus;
			case CRUSH:
				return p.crushBonus;
			default:
				return p.rangedBonus;
		}
	}

	private static final String[] MELEE_PRAYERS = {"CLARITY_OF_THOUGHT", "IMPROVED_REFLEXES", "INCREDIBLE_REFLEXES",
		"CHIVALRY", "PIETY"};
	private static final int[] MELEE_PERCENT = {5, 10, 15, 15, 20};
	private static final String[] RANGED_PRAYERS = {"SHARP_EYE", "HAWK_EYE", "EAGLE_EYE", "DEADEYE", "RIGOUR"};
	private static final int[] RANGED_PERCENT = {5, 10, 15, 18, 20};
	private static final String[] MAGIC_PRAYERS = {"MYSTIC_WILL", "MYSTIC_LORE", "MYSTIC_MIGHT", "MYSTIC_VIGOUR",
		"AUGURY"};
	private static final int[] MAGIC_PERCENT = {5, 10, 15, 18, 25};

	private static int prayerPercent(PlayerState p, String[] prayers, int[] percents)
	{
		int best = 0;
		for (int i = 0; i < prayers.length; i++)
		{
			if (p.prayers.contains(prayers[i]))
			{
				best = Math.max(best, percents[i]);
			}
		}
		return best;
	}

	/** A full void set: the helm of this style, a top, a robe and gloves (elite counts too). */
	private static boolean hasVoid(PlayerState p, String helm)
	{
		return wears(p, helm) && (wears(p, "void knight top") || wears(p, "elite void top"))
			&& (wears(p, "void knight robe") || wears(p, "elite void robe")) && wears(p, "void knight gloves");
	}

	/** Wears an item whose name starts with this (so "(or)" and "(l)" versions count). */
	private static boolean wears(PlayerState p, String name)
	{
		for (String item : p.worn)
		{
			if (item != null && item.toLowerCase(Locale.ROOT).startsWith(name))
			{
				return true;
			}
		}
		return false;
	}


	/** Helm 1, legs 2, body 3 points; with a crystal bow each point adds 5%. */
	private static int crystalArmourPieces(PlayerState p)
	{
		return (wears(p, "crystal helm") ? 1 : 0) + (wears(p, "crystal legs") ? 2 : 0) + (wears(p, "crystal body") ? 3 : 0);
	}

	/** Revenant weapons with charges ("(u)" is uncharged) of your attack type. */
	private static boolean isChargedRevenantWeapon(String weapon, WeaponStyles.Type type)
	{
		if (weapon.endsWith("(u)"))
		{
			return false;
		}
		if (type.isRanged())
		{
			return weapon.startsWith("craw's bow") || weapon.startsWith("webweaver bow");
		}
		if (type == WeaponStyles.Type.MAGIC)
		{
			return weapon.startsWith("thammaron's sceptre") || weapon.startsWith("accursed sceptre");
		}
		return weapon.startsWith("viggora's chainmace") || weapon.startsWith("ursine chainmace");
	}



	/** The Twisted bow hits monsters with high magic better, up to 140%. */
	static int twistedBowPercent(NpcStats.Npc npc)
	{
		int magic = Math.min(250, Math.max(npc.magicLevel, npc.magicAttack));
		int bonus = (3 * magic - 10) / 100;
		int penalty = (3 * magic / 10 - 100) * (3 * magic / 10 - 100) / 100;
		return Math.min(140, 140 + bonus - penalty);
	}
}
