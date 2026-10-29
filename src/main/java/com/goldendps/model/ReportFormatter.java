package com.goldendps.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a short report of a fight, one chat line per player:
 * <pre>
 * GoldenDPS Damage - Vorkath 1:23 - total 27.4k (330.1/s)
 * 1. Kurt Yoghurt 12.3k 148.2/s 45%
 * 2. Zezima 9.1k 109.6/s 33%
 * </pre>
 * Every line fits in one RuneLite party chat message.
 */
public final class ReportFormatter
{
	/** RuneLite party chat cuts messages at 150 characters; stay below that. */
	public static final int MAX_LINE = 140;
	public static final int DEFAULT_TOP = 5;

	private ReportFormatter()
	{
	}

	/**
	 * @param report the fight to report
	 * @param mode   what to report (damage, DPS, healing, damage taken)
	 * @param title  what the fight is called ("Vorkath", "Session")
	 * @param top    how many players
	 * @return the lines to send, header first; only the header when there is nothing to report
	 */
	public static List<String> lines(FightReport report, MeterMode mode, String title, int top)
	{
		List<String> out = new ArrayList<>();
		List<FightReport.PlayerRow> rows = report.sorted(mode);
		double total = report.total(mode);

		StringBuilder header = new StringBuilder("GoldenDPS ").append(mode.getLabel());
		if (title != null && !title.isEmpty())
		{
			header.append(" - ").append(title);
		}
		header.append(' ').append(Format.duration(report.getDurationMs()));
		if (!rows.isEmpty())
		{
			header.append(" - total ").append(totalText(report, mode, total));
		}
		out.add(cut(header.toString()));

		int n = Math.min(Math.max(1, top), rows.size());
		for (int i = 0; i < n; i++)
		{
			FightReport.PlayerRow row = rows.get(i);
			String name = (row.isEstimate() ? "~" : "") + row.name;
			out.add(cut((i + 1) + ". " + name + " " + valueText(row, mode, total)));
		}
		return out;
	}

	private static String totalText(FightReport report, MeterMode mode, double total)
	{
		switch (mode)
		{
			case DAMAGE:
			case DPS:
				return Format.amount(report.getTotalDamage()) + " (" + Format.dps(report.getGroupDps()) + "/s)";
			case HEALING:
				double oh = report.groupOverhealPercent();
				return Format.amount(total) + (oh > 0 ? " OH " + Format.percent(oh) : "");
			default:
				return Format.amount(total);
		}
	}

	private static String valueText(FightReport.PlayerRow row, MeterMode mode, double total)
	{
		double v = FightReport.value(row, mode);
		String share = Format.percent(total <= 0 ? 0 : 100.0 * v / total);
		switch (mode)
		{
			case DAMAGE:
				return Format.amount(row.damage) + " " + Format.dps(row.dps) + "/s " + Format.percent(row.percent);
			case DPS:
				return Format.dps(row.dps) + "/s " + Format.amount(row.damage) + " " + Format.percent(row.percent);
			case HEALING:
				return Format.amount(v) + (row.overheal > 0 ? " OH " + Format.percent(row.overhealPercent()) : "") + " " + share;
			default:
				return Format.amount(v) + " " + share;
		}
	}

	private static String cut(String s)
	{
		return s.length() <= MAX_LINE ? s : s.substring(0, MAX_LINE);
	}
}
