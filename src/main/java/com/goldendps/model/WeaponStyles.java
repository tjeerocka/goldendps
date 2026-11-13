package com.goldendps.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The attack styles of each weapon category (the buttons in the combat tab), and the category of each
 * weapon, from weapon_categories.tsv (made from the OSRS Wiki).
 */
public class WeaponStyles
{
	/** What you attack with, and so which defence bonus of the monster blocks it. */
	public enum Type
	{
		STAB, SLASH, CRUSH, MAGIC,
		/** Ranged: darts, knives, thrown weapons. */
		LIGHT,
		/** Ranged: bows and arrows. */
		STANDARD,
		/** Ranged: crossbows, ballistae, chinchompas. */
		HEAVY,
		/** Ranged: salamanders, the average of the three. */
		MIXED;

		public boolean isRanged()
		{
			return this == LIGHT || this == STANDARD || this == HEAVY || this == MIXED;
		}

		public boolean isMelee()
		{
			return this == STAB || this == SLASH || this == CRUSH;
		}
	}

	/** One attack style. */
	public static final class Style
	{
		public final Type type;
		/** The hidden level bonus of the stance: +3 accurate, +1 controlled, +2 accurate on powered staves. */
		public final int stanceBonus;

		Style(Type type, int stanceBonus)
		{
			this.type = type;
			this.stanceBonus = stanceBonus;
		}
	}

	/** A spell you click by hand: no stance bonus. */
	public static final Style CLICKED_SPELL = new Style(Type.MAGIC, 0);

	private static final String UNARMED = "unarmed";
	/** Category to its styles, by the slot of the style in the combat tab ("-" = no style there). */
	private static final Map<String, Style[]> STYLES = new HashMap<>();

	static
	{
		// slot 4 of staves is autocast; a style with three buttons leaves slot 2 empty
		add("2h sword", "slash+3 slash crush slash");
		add("axe", "slash+3 slash crush slash");
		add("banner", "stab+3 slash crush+1 stab");
		add("bladed staff", "stab+3 slash - crush magic");
		add("bludgeon", "crush crush - crush");
		add("blunt", "crush+3 crush - crush");
		add("bulwark", "crush+3");
		add("claw", "slash+3 slash stab+1 slash");
		add("extending chain mace", "crush+3 crush - crush");
		add("flail", "slash+3 slash - slash");
		add("multi-melee", "stab+3 slash crush slash");
		add("partisan", "stab+3 stab crush stab");
		add("pickaxe", "stab+3 stab crush stab");
		add("polearm", "stab+1 slash - stab");
		add("polestaff", "crush+3 crush - crush");
		add("powered book", "magic+2 magic+2 - magic");
		add("powered staff", "magic+2 magic+2 - magic");
		add("powered wand", "magic+2 magic+2 - magic");
		add("scythe", "slash+3 slash crush slash");
		add("slash sword", "slash+3 slash stab+1 slash");
		add("spear", "stab+1 slash+1 crush+1 stab");
		add("spiked", "crush+3 crush stab+1 crush");
		add("staff", "crush+3 crush - crush magic");
		add("stab sword", "stab+3 stab slash stab");
		add(UNARMED, "crush+3 crush - crush");
		add("whip", "slash+3 slash+1 - slash");
		add("thrown", "light+3 light - light");
		add("bow", "standard+3 standard - standard");
		add("crossbow", "heavy+3 heavy - heavy");
		add("chinchompas", "heavy+3 heavy - heavy");
		add("salamander", "slash mixed - magic");
	}

	private static void add(String category, String styles)
	{
		String[] slots = styles.split(" ");
		Style[] parsed = new Style[slots.length];
		for (int i = 0; i < slots.length; i++)
		{
			if (!slots[i].equals("-"))
			{
				String[] typeAndBonus = slots[i].split("\\+");
				int bonus = typeAndBonus.length > 1 ? Integer.parseInt(typeAndBonus[1]) : 0;
				parsed[i] = new Style(Type.valueOf(typeAndBonus[0].toUpperCase(Locale.ROOT)), bonus);
			}
		}
		STYLES.put(category, parsed);
	}

	private final Map<Integer, String> categoryById = new HashMap<>();
	private final Map<String, String> categoryByName = new HashMap<>();

	/** @param lines the lines of weapon_categories.tsv */
	public WeaponStyles(List<String> lines)
	{
		for (String line : lines)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] cells = line.split("\t", -1);
			String category = cells[2].toLowerCase(Locale.ROOT);
			categoryById.put(Integer.parseInt(cells[0]), category);
			categoryByName.putIfAbsent(cells[1].toLowerCase(Locale.ROOT), category);
		}
	}

	/** The weapons of weapon_categories.tsv, which ships inside the plugin. */
	public static WeaponStyles load() throws IOException
	{
		try (InputStream in = WeaponStyles.class.getResourceAsStream("/com/goldendps/weapon_categories.tsv"))
		{
			if (in == null)
			{
				throw new IOException("weapon_categories.tsv is missing");
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			return new WeaponStyles(reader.lines().collect(Collectors.toList()));
		}
	}

	/**
	 * The style you attack with, or null when it is not known.
	 * @param weaponName null when you hold no weapon
	 * @param slot the selected style in the combat tab (0-3, 4 for autocast)
	 */
	public Style style(int weaponId, String weaponName, int slot)
	{
		String category = weaponName == null ? UNARMED : categoryById.get(weaponId);
		if (category == null)
		{
			category = categoryByName.get(weaponName.toLowerCase(Locale.ROOT));
		}
		Style[] styles = category == null ? null : STYLES.get(category);
		return styles == null || slot < 0 || slot >= styles.length ? null : styles[slot];
	}
}
