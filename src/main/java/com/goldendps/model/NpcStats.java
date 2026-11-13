package com.goldendps.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The defensive stats of monsters, from npc_stats.tsv (made from the OSRS Wiki). The wiki lists one NPC id
 * per monster version, the game has more, so an unknown id is looked up by the name.
 */
public class NpcStats
{
	/** One monster. */
	public static final class Npc
	{
		public final String name;
		public final int defence;
		public final int magicLevel;
		/** Magic attack bonus; the Twisted bow uses it. */
		public final int magicAttack;
		/** Defence bonuses: stab, slash, crush, magic, ranged light, standard, heavy. */
		public final int stab;
		public final int slash;
		public final int crush;
		public final int magic;
		public final int light;
		public final int standard;
		public final int heavy;
		/** "demon", "dragon", "undead"... */
		private final Set<String> attributes;
		/** "fire", "water", "earth" or "air": elemental spells of it hit more often and harder; or null. */
		public final String weakElement;
		/** How much better, in percent. */
		public final int weaknessPercent;
		/** Own accuracy rules: "always-hit", "always-hit-melee", "magic-uses-defence"... */
		private final Set<String> rules;

		Npc(String[] cells)
		{
			name = cells[1];
			defence = Integer.parseInt(cells[2]);
			magicLevel = Integer.parseInt(cells[3]);
			magicAttack = Integer.parseInt(cells[4]);
			stab = Integer.parseInt(cells[5]);
			slash = Integer.parseInt(cells[6]);
			crush = Integer.parseInt(cells[7]);
			magic = Integer.parseInt(cells[8]);
			light = Integer.parseInt(cells[9]);
			standard = Integer.parseInt(cells[10]);
			heavy = Integer.parseInt(cells[11]);
			attributes = new HashSet<>(Arrays.asList(cells[12].split(",")));
			String[] weakness = cells[13].split(":");
			weakElement = weakness.length == 2 ? weakness[0] : null;
			weaknessPercent = weakness.length == 2 ? Integer.parseInt(weakness[1]) : 0;
			rules = new HashSet<>(Arrays.asList(cells[14].split(",")));
		}

		public boolean is(String attribute)
		{
			return attributes.contains(attribute);
		}

		public boolean hasRule(String rule)
		{
			return rules.contains(rule);
		}
	}

	private final Map<Integer, Npc> byNpcId = new HashMap<>();
	private final Map<String, Npc> byName = new HashMap<>();

	/** @param lines the lines of npc_stats.tsv */
	public NpcStats(List<String> lines)
	{
		for (String line : lines)
		{
			if (line.isEmpty() || line.startsWith("#"))
			{
				continue;
			}
			String[] cells = line.split("\t", -1);
			Npc npc = new Npc(cells);
			byNpcId.put(Integer.parseInt(cells[0]), npc);
			if ("1".equals(cells[15]))
			{
				byName.put(npc.name.toLowerCase(Locale.ROOT), npc);
			}
		}
	}

	/** The monsters of npc_stats.tsv, which ships inside the plugin. */
	public static NpcStats load() throws IOException
	{
		try (InputStream in = NpcStats.class.getResourceAsStream("/com/goldendps/npc_stats.tsv"))
		{
			if (in == null)
			{
				throw new IOException("npc_stats.tsv is missing");
			}
			BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
			return new NpcStats(reader.lines().collect(Collectors.toList()));
		}
	}

	/** The monster with this NPC id, else with this name; or null. */
	public Npc get(int npcId, String name)
	{
		Npc npc = byNpcId.get(npcId);
		if (npc == null && name != null)
		{
			npc = byName.get(name.toLowerCase(Locale.ROOT));
		}
		return npc;
	}
}
