package com.goldendps;

import static com.goldendps.PanelWidgets.GOLD;
import static com.goldendps.PanelWidgets.bold;
import static com.goldendps.PanelWidgets.line;
import static com.goldendps.PanelWidgets.section;
import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.PlayerKind;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;

/** The cards of the panel's fight details: the summary, one per player and one per target. Swing thread only. */
final class FightCards
{
	private FightCards()
	{
	}

	/** Only one player did damage: no group numbers, no shares. */
	static boolean isSolo(FightReport report)
	{
		return report.getRows().size() <= 1;
	}

	static JPanel summary(GoldenDpsPanel.Data d)
	{
		FightReport r = d.report;
		JPanel card = section();
		card.add(bold(r.getTitle(), GOLD));
		card.add(line("Time", Format.duration(r.getDurationMs()) + (d.paused ? " (paused)" : d.running ? " (live)" : "")));
		if (d.fights > 0)
		{
			card.add(line("Fights", String.valueOf(d.fights)));
		}
		else
		{
			card.add(line("Started", new SimpleDateFormat("dd MMM HH:mm").format(new Date(d.startMs))));
		}
		boolean solo = isSolo(r);
		card.add(line(solo ? "Damage" : "Group damage", Format.amount(r.getTotalDamage())));
		card.add(line(solo ? "DPS" : "Group DPS", Format.dps(r.getGroupDps())));
		FightReport.PlayerRow topHit = r.topHit();
		if (topHit != null)
		{
			card.add(line("Max hit", topHit.biggestHit + (solo ? "" : " (" + topHit.name + ")")));
		}
		if (bossDiffers(d))
		{
			card.add(line("Boss time", Format.duration(d.bossReport.getDurationMs())));
			card.add(line("Boss damage", Format.amount(d.bossReport.getTotalDamage())));
			card.add(line("Boss DPS", Format.dps(d.bossReport.getGroupDps())));
		}
		for (String[] killLine : d.killLines)
		{
			String label = killLine[0];
			String value = killLine[1];
			card.add(line(label, value, value.contains("NEW") ? GOLD : null));
		}
		if (d.endReason != null && d.fights == 0)
		{
			card.add(line("Ended", d.endReason));
		}
		return card;
	}

	/** Boss lines only when they say something else than the fight lines (other targets were hit too). */
	private static boolean bossDiffers(GoldenDpsPanel.Data d)
	{
		return d.bossReport != null && (d.bossReport.getTotalDamage() != d.report.getTotalDamage()
			|| d.bossReport.getDurationMs() != d.report.getDurationMs());
	}

	/** In a solo fight the summary already has the damage and DPS. */
	static JPanel player(FightReport.PlayerRow row, FightReport.PlayerRow bossRow, boolean solo)
	{
		JPanel card = section();
		Color nameColor = row.kind == PlayerKind.LOCAL ? GOLD
			: row.kind == PlayerKind.PARTY ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR;
		String name = (row.isEstimate() ? "~" : "") + row.name
			+ (row.kind == PlayerKind.LOCAL ? " (you)" : row.isEstimate() ? " (guess)" : "");
		card.add(bold(name, nameColor));
		if (!solo)
		{
			card.add(line("Damage", Format.amount(row.damage) + "  (" + Format.percent(row.percent) + ")"));
			card.add(line("DPS", Format.dps(row.dps)));
		}
		if (!solo && bossRow != null && bossRow.damage > 0 && bossRow.damage != row.damage)
		{
			card.add(line("Boss damage", Format.amount(bossRow.damage) + "  (" + Format.percent(bossRow.percent) + ")"));
			card.add(line("Boss DPS", Format.dps(bossRow.dps)));
		}
		if (!row.isPlayer())
		{
			card.add(line("", row.kind == PlayerKind.UNKNOWN ? "Hits nobody could be matched to" : "Owner not known"));
			return card;
		}
		card.add(line("Biggest hit", String.valueOf(row.biggestHit)));
		double accuracy = row.accuracy();
		card.add(line("Accuracy", accuracy < 0 ? "-"
			: String.format("%.0f%% (%d/%d)", accuracy, row.hits, row.hits + row.misses)));
		addSources(card, row);
		if (row.isEstimate())
		{
			card.add(line("", "No GoldenDPS: damage is a guess"));
			card.add(line("Damage taken", Format.amount(row.damageTaken)));
			return card;
		}
		addHealing(card, row);
		card.add(line("Damage taken", Format.amount(row.damageTaken)));
		return card;
	}

	/** Damage per weapon, special attack or spell. */
	private static void addSources(JPanel card, FightReport.PlayerRow row)
	{
		if (row.sources.isEmpty())
		{
			return;
		}
		card.add(line("Breakdown", ""));
		for (FightReport.SourceRow source : row.sources)
		{
			JComponent l = line("  " + source.name, Format.amount(source.damage) + "  (" + Format.percent(source.percent) + ")");
			l.setToolTipText(String.format("%s: %d hits, %d misses, biggest %d, average %.1f per attack",
				source.name, source.hits, source.misses, source.biggestHit, source.average()));
			card.add(l);
		}
	}

	private static void addHealing(JPanel card, FightReport.PlayerRow row)
	{
		card.add(line("Healing", Format.amount(row.healing)));
		addIfAny(card, "  Food/potions", row.healFood);
		addIfAny(card, "  Lifesteal", row.healLifesteal);
		addIfAny(card, "  On others", row.healOther);
		addIfAny(card, "  Regen", row.healRegen);
		if (row.overheal > 0)
		{
			card.add(line("Overhealing", Format.percent(row.overhealPercent()) + "  (" + Format.amount(row.overheal) + ")"));
			addIfAny(card, "  Food/potions", row.overFood);
			addIfAny(card, "  Lifesteal", row.overLifesteal);
		}
	}

	private static void addIfAny(JPanel card, String label, long amount)
	{
		if (amount > 0)
		{
			card.add(line(label, Format.amount(amount)));
		}
	}

	static JPanel target(FightReport.TargetRow target)
	{
		JPanel card = section();
		String tags = (target.boss ? " [boss]" : "") + (target.died ? " [dead]" : "");
		card.add(bold(target.name + tags, Color.WHITE));
		card.add(line("Total", Format.amount(target.total)));
		for (Map.Entry<String, Long> byPlayer : target.byPlayer.entrySet())
		{
			double percent = target.total == 0 ? 0 : 100.0 * byPlayer.getValue() / target.total;
			card.add(line("  " + byPlayer.getKey(), Format.amount(byPlayer.getValue()) + " (" + Format.percent(percent) + ")"));
		}
		return card;
	}
}
