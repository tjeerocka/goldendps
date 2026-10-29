package com.goldendps.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * Numbers for the overlay and the panel, worked out from one {@link Fight}.
 * Pure Java, so it is easy to unit test.
 */
public class FightReport
{
	public static final String UNKNOWN_NAME = "Others (unknown)";
	public static final String EFFECTS_NAME = "Poison / venom / burn";

	public static class PlayerRow
	{
		public String name;
		public PlayerKind kind;
		public long damage;
		public int hits;
		public int misses;
		public int biggestHit;
		public double dps;
		/** Share of the group damage, 0..100. */
		public double percent;
		public long healing;
		public long healFood;
		public long healLifesteal;
		public long healRegen;
		public long healOther;
		/** Wasted healing (food + lifesteal). */
		public long overheal;
		public long overFood;
		public long overLifesteal;
		public long damageTaken;
		/** Damage per weapon / spell, biggest first. Empty when not known. */
		public List<SourceRow> sources = new ArrayList<>();

		/** Share of this player's healing that was wasted, 0..100 (0 when nothing was healed). */
		public double overhealPercent()
		{
			return FightReport.overhealPercent(healing, overheal);
		}

		/** Hits / (hits + misses), 0..100. -1 when there were no attacks (or unknown). */
		public double accuracy()
		{
			int attacks = hits + misses;
			return attacks == 0 ? -1 : 100.0 * hits / attacks;
		}

		/** A real player row (you, party, or a guessed player). */
		public boolean isPlayer()
		{
			return kind == PlayerKind.LOCAL || kind == PlayerKind.PARTY || kind == PlayerKind.ESTIMATE;
		}

		/** Numbers are a guess (player without GoldenDPS). */
		public boolean isEstimate()
		{
			return kind == PlayerKind.ESTIMATE;
		}
	}

	/** One line of the breakdown: damage with one weapon or spell. */
	public static class SourceRow
	{
		public String name;
		public long damage;
		public int hits;
		public int misses;
		public int biggestHit;
		/** Share of this player's damage, 0..100. */
		public double percent;

		/** Average damage per attack (hits and misses). */
		public double average()
		{
			int attacks = hits + misses;
			return attacks == 0 ? 0 : (double) damage / attacks;
		}
	}

	public static class TargetRow
	{
		public String name;
		public boolean boss;
		public boolean died;
		public long total;
		/** Player name (or UNKNOWN_NAME / EFFECTS_NAME) to damage, biggest first. */
		public Map<String, Long> byPlayer = new LinkedHashMap<>();
	}

	private final List<PlayerRow> rows = new ArrayList<>();
	private final List<TargetRow> targets = new ArrayList<>();
	private long totalDamage;
	private long durationMs;
	private String title;

	public static FightReport of(Fight fight, boolean bossOnly, boolean includeRegen, long nowMs)
	{
		FightReport r = new FightReport();
		r.durationMs = fight.durationMs(nowMs);
		if (bossOnly)
		{
			// boss DPS: only the time spent on bosses, not the trash before
			long boss = fight.bossDurationMs(nowMs);
			if (boss > 0)
			{
				r.durationMs = boss;
			}
		}
		r.title = fight.title(bossOnly && fight.hasBossTarget());
		double seconds = r.durationMs / 1000.0;

		Map<String, Long> knownByTarget = new LinkedHashMap<>();
		for (PlayerStats p : fight.players.values())
		{
			PlayerRow row = new PlayerRow();
			row.name = p.name;
			row.kind = p.kind;
			for (Map.Entry<String, TargetDamage> e : p.damageByTarget().entrySet())
			{
				if (!counts(fight, e.getKey(), bossOnly) || !countsFor(fight, p, e.getKey()))
				{
					continue;
				}
				TargetDamage d = e.getValue();
				row.damage += d.damage;
				row.hits += d.hits;
				row.misses += d.misses;
				row.biggestHit = Math.max(row.biggestHit, d.biggestHit);
				knownByTarget.merge(e.getKey(), d.damage, Long::sum);
			}
			Map<String, SourceRow> src = new LinkedHashMap<>();
			if (p.kind != PlayerKind.ESTIMATE)
			{
				for (Map.Entry<String, Map<String, TargetDamage>> s : p.sources().entrySet())
				{
					for (Map.Entry<String, TargetDamage> e : s.getValue().entrySet())
					{
						if (!counts(fight, e.getKey(), bossOnly) || !countsFor(fight, p, e.getKey()))
						{
							continue;
						}
						SourceRow sr = src.computeIfAbsent(s.getKey(), k ->
						{
							SourceRow n = new SourceRow();
							n.name = k;
							return n;
						});
						TargetDamage d = e.getValue();
						sr.damage += d.damage;
						sr.hits += d.hits;
						sr.misses += d.misses;
						sr.biggestHit = Math.max(sr.biggestHit, d.biggestHit);
					}
				}
			}
			for (SourceRow sr : src.values())
			{
				if (sr.hits + sr.misses > 0)
				{
					sr.percent = row.damage == 0 ? 0 : 100.0 * sr.damage / row.damage;
					row.sources.add(sr);
				}
			}
			row.sources.sort((a, b) -> Long.compare(b.damage, a.damage));
			row.healFood = p.healFood;
			row.healLifesteal = p.healLifesteal;
			row.healRegen = p.healRegen;
			row.healOther = p.healOther;
			row.healing = p.healing(includeRegen);
			row.overFood = p.overFood;
			row.overLifesteal = p.overLifesteal;
			row.overheal = p.overheal();
			row.damageTaken = p.takenTotal();
			r.rows.add(row);
		}

		long unknown = 0;
		long effects = 0;
		for (TargetStats t : fight.targets.values())
		{
			if (!t.engaged || !counts(fight, t.key, bossOnly))
			{
				continue;
			}
			long known = knownByTarget.getOrDefault(t.key, 0L);
			unknown += Math.max(0, t.seenDamage - known);
			effects += t.effectDamage;
		}
		if (unknown > 0)
		{
			r.rows.add(pseudoRow(UNKNOWN_NAME, PlayerKind.UNKNOWN, unknown));
		}
		if (effects > 0)
		{
			r.rows.add(pseudoRow(EFFECTS_NAME, PlayerKind.EFFECTS, effects));
		}

		for (PlayerRow row : r.rows)
		{
			r.totalDamage += row.damage;
		}
		for (PlayerRow row : r.rows)
		{
			row.dps = row.damage / seconds;
			row.percent = r.totalDamage == 0 ? 0 : 100.0 * row.damage / r.totalDamage;
		}

		r.buildTargets(fight, bossOnly);
		return r;
	}

	private static PlayerRow pseudoRow(String name, PlayerKind kind, long damage)
	{
		PlayerRow row = new PlayerRow();
		row.name = name;
		row.kind = kind;
		row.damage = damage;
		return row;
	}

	private static boolean counts(Fight fight, String targetKey, boolean bossOnly)
	{
		if (!bossOnly)
		{
			return true;
		}
		TargetStats t = fight.targets.get(targetKey);
		return t != null && t.boss;
	}

	/** Guesses only count on targets you or your party fought (same rule as "Others (unknown)"). */
	private static boolean countsFor(Fight fight, PlayerStats p, String targetKey)
	{
		if (p.kind != PlayerKind.ESTIMATE)
		{
			return true;
		}
		TargetStats t = fight.targets.get(targetKey);
		return t != null && t.engaged;
	}

	private void buildTargets(Fight fight, boolean bossOnly)
	{
		Map<String, TargetRow> byName = new LinkedHashMap<>();
		Map<String, Long> knownByTarget = new LinkedHashMap<>();
		for (PlayerStats p : fight.players.values())
		{
			for (Map.Entry<String, TargetDamage> e : p.damageByTarget().entrySet())
			{
				TargetStats t = fight.targets.get(e.getKey());
				if (t == null || !counts(fight, t.key, bossOnly) || !countsFor(fight, p, t.key)
					|| e.getValue().damage == 0)
				{
					continue;
				}
				TargetRow row = targetRow(byName, t);
				row.byPlayer.merge(p.name, e.getValue().damage, Long::sum);
				row.total += e.getValue().damage;
				knownByTarget.merge(t.key, e.getValue().damage, Long::sum);
			}
		}
		for (TargetStats t : fight.targets.values())
		{
			if (!t.engaged || !counts(fight, t.key, bossOnly))
			{
				continue;
			}
			long unknown = Math.max(0, t.seenDamage - knownByTarget.getOrDefault(t.key, 0L));
			if (unknown > 0 || t.effectDamage > 0)
			{
				TargetRow row = targetRow(byName, t);
				if (unknown > 0)
				{
					row.byPlayer.merge(UNKNOWN_NAME, unknown, Long::sum);
					row.total += unknown;
				}
				if (t.effectDamage > 0)
				{
					row.byPlayer.merge(EFFECTS_NAME, t.effectDamage, Long::sum);
					row.total += t.effectDamage;
				}
			}
		}
		for (TargetRow row : byName.values())
		{
			Map<String, Long> sorted = new LinkedHashMap<>();
			row.byPlayer.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
			row.byPlayer = sorted;
			targets.add(row);
		}
		targets.sort(Comparator.comparingLong((TargetRow t) -> t.total).reversed());
	}

	private static TargetRow targetRow(Map<String, TargetRow> byName, TargetStats t)
	{
		String name = t.name == null ? "Unknown" : t.name;
		TargetRow row = byName.computeIfAbsent(name, n ->
		{
			TargetRow tr = new TargetRow();
			tr.name = n;
			return tr;
		});
		row.boss |= t.boss;
		row.died |= t.died;
		return row;
	}

	/** The value a bar shows in this mode. */
	public static double value(PlayerRow row, MeterMode mode)
	{
		switch (mode)
		{
			case DPS:
				return row.dps;
			case HEALING:
				return row.healing;
			case DAMAGE_TAKEN:
				return row.damageTaken;
			case DAMAGE:
			default:
				return row.damage;
		}
	}

	/** Rows for a bar view, biggest first. Rows with 0 are left out. */
	public List<PlayerRow> sorted(MeterMode mode)
	{
		List<PlayerRow> out = new ArrayList<>();
		for (PlayerRow row : rows)
		{
			boolean damageMode = mode == MeterMode.DAMAGE || mode == MeterMode.DPS;
			if (!damageMode && !row.isPlayer())
			{
				continue;
			}
			if (value(row, mode) > 0)
			{
				out.add(row);
			}
		}
		out.sort(Comparator.comparingDouble((PlayerRow r) -> value(r, mode)).reversed()
			.thenComparing(r -> r.name));
		return out;
	}

	/** Overhealing as % of all healing (effective + overhealing). */
	public static double overhealPercent(long healing, long overheal)
	{
		long full = healing + overheal;
		return full <= 0 ? 0 : 100.0 * overheal / full;
	}

	/** Overhealing of the whole group as % of all healing. */
	public double groupOverhealPercent()
	{
		long heal = 0;
		long over = 0;
		for (PlayerRow r : rows)
		{
			heal += r.healing;
			over += r.overheal;
		}
		return overhealPercent(heal, over);
	}

	/** Sum of the values in this mode (for the header). */
	public double total(MeterMode mode)
	{
		double sum = 0;
		for (PlayerRow row : sorted(mode))
		{
			sum += value(row, mode);
		}
		return sum;
	}

	/** The player with the most damage, or null. */
	public PlayerRow topDamage()
	{
		return topPlayer(r -> r.damage);
	}

	/** The player with the most healing, or null. */
	public PlayerRow topHealer()
	{
		return topPlayer(r -> r.healing);
	}

	/** The player with the biggest hit, or null. */
	public PlayerRow topHit()
	{
		return topPlayer(r -> r.biggestHit);
	}

	/** Number of real players (you, party members and estimated players). */
	public int playerCount()
	{
		return (int) rows.stream().filter(PlayerRow::isPlayer).count();
	}

	private PlayerRow topPlayer(ToLongFunction<PlayerRow> value)
	{
		PlayerRow best = null;
		for (PlayerRow r : rows)
		{
			if (r.isPlayer() && value.applyAsLong(r) > 0 && (best == null || value.applyAsLong(r) > value.applyAsLong(best)))
			{
				best = r;
			}
		}
		return best;
	}

	public List<PlayerRow> getRows()
	{
		return rows;
	}

	public PlayerRow row(String name)
	{
		for (PlayerRow r : rows)
		{
			if (r.name.equals(name))
			{
				return r;
			}
		}
		return null;
	}

	public List<TargetRow> getTargets()
	{
		return targets;
	}

	public long getTotalDamage()
	{
		return totalDamage;
	}

	public double getGroupDps()
	{
		return totalDamage / (durationMs / 1000.0);
	}

	public long getDurationMs()
	{
		return durationMs;
	}

	public String getTitle()
	{
		return title;
	}
}
