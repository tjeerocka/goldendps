package com.goldendps.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringJoiner;

/**
 * The defence (and magic level) of monsters after special attacks that lower it (yours and your party's),
 * with the rules of the OSRS Wiki. Drained stats come back 1 level a minute. Not yet: raids, Tekton's misses,
 * and monsters that restore faster or limit drains.
 */
public class DefenceDrains
{
	/** One special attack that lowered the defence. */
	public static final class Drain
	{
		public final String player;
		public final String weapon;
		public final int amount;
		/** It lowered the Magic level (Seercull, Accursed sceptre), not the Defence. */
		public final boolean magic;

		Drain(String player, String weapon, int amount, boolean magic)
		{
			this.player = player;
			this.weapon = weapon;
			this.amount = amount;
			this.magic = magic;
		}
	}

	/** The defence and magic level of one monster. */
	public static final class Target
	{
		public final String name;
		public final int base;
		public final int magicBase;
		private final boolean demon;
		private int current;
		private int magicCurrent;
		/** The tick the next level comes back is counted from; -1 while the stat is not lowered. */
		private int restoreTick = -1;
		private int magicRestoreTick = -1;
		private final List<Drain> drains = new ArrayList<>();

		Target(NpcStats.Npc npc)
		{
			name = npc.name;
			base = npc.defence;
			magicBase = npc.magicLevel;
			demon = npc.is("demon");
			current = npc.defence;
			magicCurrent = npc.magicLevel;
		}

		public int getCurrent()
		{
			return current;
		}

		/** The magic level blocks spells, so a lower one helps your magic hit chance. */
		public int getMagicCurrent()
		{
			return magicCurrent;
		}

		public List<Drain> getDrains()
		{
			return Collections.unmodifiableList(drains);
		}

		/** "DWH x2 -126, BGS -34, Seercull -30 magic": the drains per weapon, in the order they first hit. */
		public String summary()
		{
			Map<String, int[]> perWeapon = new LinkedHashMap<>();
			for (Drain d : drains)
			{
				String weapon = shortName(d.weapon) + (d.magic ? MAGIC : "");
				int[] countAndTotal = perWeapon.computeIfAbsent(weapon, k -> new int[2]);
				countAndTotal[0]++;
				countAndTotal[1] += d.amount;
			}
			StringJoiner out = new StringJoiner(", ");
			perWeapon.forEach((weapon, countAndTotal) ->
			{
				boolean magic = weapon.endsWith(MAGIC);
				String name = magic ? weapon.substring(0, weapon.length() - MAGIC.length()) : weapon;
				out.add(name + (countAndTotal[0] > 1 ? " x" + countAndTotal[0] : "") + " -" + countAndTotal[1]
					+ (magic ? " magic" : ""));
			});
			return out.toString();
		}
	}

	private static final String SPEC = " (spec)";
	private static final String MAGIC = "|magic";
	/** One minute. */
	static final int RESTORE_TICKS = 100;

	private final NpcStats stats;
	private final Map<String, Target> byKey = new HashMap<>();

	public DefenceDrains(NpcStats stats)
	{
		this.stats = stats;
	}

	/**
	 * A hit by you or a party member.
	 * @param source weapon or spell, "Dragon warhammer (spec)" for a spec
	 */
	public void hit(int tick, String targetKey, int npcId, String npcName, String player, String source, int damage,
		boolean miss)
	{
		if (source == null || !source.endsWith(SPEC))
		{
			return;
		}
		Target t = byKey.get(targetKey);
		if (t == null)
		{
			NpcStats.Npc npc = stats.get(npcId, npcName);
			if (npc == null)
			{
				return;
			}
			t = new Target(npc);
		}
		String weapon = source.substring(0, source.length() - SPEC.length());
		int drain = Math.min(t.current, drain(weapon, t, miss ? 0 : damage, miss));
		int magicDrain = Math.min(t.magicCurrent, magicDrain(weapon, t, miss ? 0 : damage));
		if (drain > 0)
		{
			t.current -= drain;
			t.restoreTick = t.restoreTick < 0 ? tick : t.restoreTick;
			t.drains.add(new Drain(player, weapon, drain, false));
		}
		if (magicDrain > 0)
		{
			t.magicCurrent -= magicDrain;
			t.magicRestoreTick = t.magicRestoreTick < 0 ? tick : t.magicRestoreTick;
			t.drains.add(new Drain(player, weapon, magicDrain, true));
		}
		if (!t.drains.isEmpty())
		{
			byKey.put(targetKey, t);
		}
	}

	/** How much Magic level this special attack takes away. */
	static int magicDrain(String weapon, Target t, int damage)
	{
		String w = weapon.toLowerCase(Locale.ROOT);
		if (w.startsWith("seercull"))
		{
			// only when the magic level is not lowered yet
			return t.magicCurrent >= t.magicBase ? damage : 0;
		}
		if (w.startsWith("accursed sceptre") && damage > 0)
		{
			// does not stack: at most 15% below the base in total
			return t.magicCurrent - (t.magicBase - t.magicBase * 15 / 100);
		}
		return 0;
	}

	/** How much defence this special attack takes away. */
	static int drain(String weapon, Target t, int damage, boolean miss)
	{
		String w = weapon.toLowerCase(Locale.ROOT);
		if (w.startsWith("bandos godsword"))
		{
			return damage;
		}
		if (miss)
		{
			return 0;
		}
		if (w.startsWith("dragon warhammer"))
		{
			return t.current * 30 / 100;
		}
		if (w.startsWith("elder maul"))
		{
			return t.current * 35 / 100;
		}
		if (w.startsWith("arclight"))
		{
			return t.base * (t.demon ? 10 : 5) / 100 + 1;
		}
		if (w.startsWith("emberlight"))
		{
			return t.base * (t.demon ? 15 : 5) / 100 + 1;
		}
		if (w.startsWith("accursed sceptre"))
		{
			// does not stack: at most 15% below the base in total
			return t.current - (t.base - t.base * 15 / 100);
		}
		if (w.startsWith("bone dagger") || w.startsWith("dorgeshuun crossbow"))
		{
			// only when the defence is not lowered yet
			return t.current >= t.base ? damage : 0;
		}
		if (w.startsWith("barrelchest anchor"))
		{
			// 10% of the damage; the wiki does not say how it rounds
			return damage / 10;
		}
		return 0;
	}

	/** "DWH" and "BGS", the names players use; other weapons keep their own name. */
	public static String shortName(String weapon)
	{
		String w = weapon.toLowerCase(Locale.ROOT);
		if (w.startsWith("dragon warhammer"))
		{
			return "DWH";
		}
		if (w.startsWith("bandos godsword"))
		{
			return "BGS";
		}
		return weapon;
	}

	/** The lowered defence of this NPC, or null when no special attack lowered it. */
	public Target get(String targetKey)
	{
		return byKey.get(targetKey);
	}

	/**
	 * Every minute (100 ticks) a drained stat comes back 1 level; a monster with all stats back is forgotten.
	 * Call each game tick.
	 */
	public void restore(int tick)
	{
		for (Iterator<Target> it = byKey.values().iterator(); it.hasNext(); )
		{
			Target t = it.next();
			while (t.restoreTick >= 0 && tick - t.restoreTick >= RESTORE_TICKS)
			{
				t.current++;
				t.restoreTick = t.current < t.base ? t.restoreTick + RESTORE_TICKS : -1;
			}
			while (t.magicRestoreTick >= 0 && tick - t.magicRestoreTick >= RESTORE_TICKS)
			{
				t.magicCurrent++;
				t.magicRestoreTick = t.magicCurrent < t.magicBase ? t.magicRestoreTick + RESTORE_TICKS : -1;
			}
			if (t.current >= t.base && t.magicCurrent >= t.magicBase)
			{
				it.remove();
			}
		}
	}

	/** The NPC died or is gone. */
	public void remove(String targetKey)
	{
		byKey.remove(targetKey);
	}

	public void clear()
	{
		byKey.clear();
	}

	/** Every NPC with lowered defence, by target key. */
	public Map<String, Target> all()
	{
		return Collections.unmodifiableMap(byKey);
	}
}
