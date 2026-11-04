package com.goldendps;

import static com.goldendps.PanelWidgets.GOLD;
import static com.goldendps.PanelWidgets.bold;
import static com.goldendps.PanelWidgets.line;
import static com.goldendps.PanelWidgets.section;
import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.TargetDamage;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
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
	/** A raid's bosses with their times and how each compares with its PB. */
	static JPanel bossTimes(List<String[]> bossTimes)
	{
		JPanel card = section();
		for (String[] boss : bossTimes)
		{
			String tag = boss[2];
			card.add(line(boss[0], boss[1] + (tag.isEmpty() ? "" : "  " + tag), tag.startsWith("NEW") ? GOLD : null));
		}
		return card;
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
		boolean solo = r.isSolo();
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

	/**
	 * In a solo fight the summary already has the damage and DPS.
	 * @param openSources weapons that show their accuracy, as {@link #sourceKey}
	 * @param toggleSource a click on a weapon opens or closes it
	 */
	static JPanel player(FightReport.PlayerRow row, FightReport.PlayerRow bossRow, boolean solo,
		Set<String> openSources, Consumer<String> toggleSource)
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
		card.add(line("Accuracy", Format.accuracy(row.hits, row.misses)));
		addSources(card, row, openSources, toggleSource);
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

	/** Damage per weapon, special attack or spell; a click shows its accuracy under it. */
	private static void addSources(JPanel card, FightReport.PlayerRow row, Set<String> openSources,
		Consumer<String> toggleSource)
	{
		if (row.sources.isEmpty())
		{
			return;
		}
		card.add(line("Breakdown", ""));
		for (FightReport.SourceRow source : row.sources)
		{
			String key = sourceKey(row.name, source.name);
			boolean open = openSources.contains(key);
			JComponent l = line((open ? "  - " : "  + ") + source.name,
				Format.amount(source.damage) + "  (" + Format.percent(source.percent) + ")");
			l.setToolTipText(String.format("%s: %d hits, %d misses, biggest %d, average %.1f per attack. Click for accuracy.",
				source.name, source.hits, source.misses, source.biggestHit, source.average()));
			PanelWidgets.clickable(l, () -> toggleSource.accept(key));
			card.add(l);
			if (open)
			{
				card.add(line("      Accuracy", Format.accuracy(source.hits, source.misses)));
			}
		}
	}

	/** One weapon of one player, so the same weapon of two players opens on its own. */
	static String sourceKey(String player, String source)
	{
		return player + "|" + source;
	}

	private static void addHealing(JPanel card, FightReport.PlayerRow row)
	{
		card.add(line("Healing", Format.amount(row.healing)));
		addIfAny(card, "  Food/potions", row.healFood);
		addIfAny(card, "  Lifesteal", row.healLifesteal);
		addIfAny(card, "  On others", row.healOther);
		if (row.overheal > 0)
		{
			card.add(line("Overhealing", Format.percent(row.overhealPercent()) + "  (" + Format.amount(row.overheal) + ")"));
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
			TargetDamage attacks = target.attacksByPlayer.get(byPlayer.getKey());
			if (attacks != null)
			{
				card.add(line("    Accuracy", Format.accuracy(attacks.hits, attacks.misses)));
			}
		}
		return card;
	}
}
