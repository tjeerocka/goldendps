package com.goldendps;

import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.MeterMode;
import com.goldendps.model.PlayerKind;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Bar meter on the game screen, with a second meter for the current boss. */
class GoldenDpsOverlay extends Overlay
{
	/** Bar colors for other players, picked per player name. */
	private static final Color[] PALETTE = {
		new Color(0x4FA3E0), new Color(0x8BC34A), new Color(0xE57BB0), new Color(0xB08A5A),
		new Color(0x7E7EE6), new Color(0x26C6A0), new Color(0xF0883E), new Color(0x2F6FD6),
		new Color(0xD04848), new Color(0x9C4FD6), new Color(0x4E9E8C), new Color(0xE0E0E0),
	};
	private static final Color UNKNOWN_COLOR = new Color(120, 120, 120);
	private static final Color EFFECTS_COLOR = new Color(70, 160, 70);
	private static final Color BACKGROUND = new Color(20, 20, 20, 170);
	private static final Color HEADER_BG = new Color(60, 45, 0, 210);
	private static final Color BOSS_HEADER_BG = new Color(90, 10, 10, 210);
	private static final Color BAR_BG = new Color(255, 255, 255, 18);
	private static final int PAD = 3;
	private static final int COL_GAP = 9;
	private static final Color CATEGORY_COLOR = new Color(190, 190, 190);
	private static final Color TITLE_COLOR = new Color(255, 200, 0);
	private static final Color PB_COLOR = new Color(255, 196, 0);

	private final GoldenDpsPlugin plugin;
	private final GoldenDpsConfig config;

	@Inject
	GoldenDpsOverlay(GoldenDpsPlugin plugin, GoldenDpsConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(PRIORITY_LOW);

		for (MeterMode mode : MeterMode.values())
		{
			addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Show", mode.getLabel(), e -> plugin.setMode(mode));
		}
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Toggle", "Boss only", e -> plugin.setBossOnly(!config.bossOnly()));
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Toggle", "Fight / Session", e -> plugin.setScope(
			config.overlayScope() == GoldenDpsConfig.Scope.FIGHT ? GoldenDpsConfig.Scope.SESSION : GoldenDpsConfig.Scope.FIGHT));
		for (ReportKind kind : ReportKind.values())
		{
			addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Report to party", kind.label, e -> plugin.reportOverlayToParty(kind));
		}
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Reset", "Session", e -> plugin.resetSession());
		addMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OverlayManager.OPTION_CONFIGURE, "GoldenDPS");
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showOverlay())
		{
			return null;
		}
		MeterMode mode = config.overlayMode();
		FightReport report = plugin.overlayReport();
		List<FightReport.PlayerRow> rows = report == null ? Collections.emptyList() : report.sorted(mode);
		BossMeter boss = plugin.bossMeter();
		List<FightReport.PlayerRow> bossRows = boss == null ? Collections.emptyList() : boss.report.sorted(MeterMode.DAMAGE);
		String barrows = plugin.barrowsRunTime();
		if (rows.isEmpty() && bossRows.isEmpty() && barrows == null && config.hideWhenEmpty())
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int width = config.overlayWidth();

		int y = 0;
		if (!rows.isEmpty() || !config.hideWhenEmpty())
		{
			y = drawMainMeter(g, fm, width, mode, report, rows);
		}
		if (boss != null && !bossRows.isEmpty())
		{
			String title = "Boss - " + boss.report.getTitle() + (boss.dead ? " (dead)" : "");
			String time = (boss.paused ? "paused " : "") + Format.duration(boss.report.getDurationMs())
				+ (boss.pb != null ? "  " + boss.pb : "");
			Color timeColor = boss.pb != null && boss.pb.startsWith("NEW") ? PB_COLOR
				: boss.paused ? Color.YELLOW : boss.dead ? Color.LIGHT_GRAY : Color.WHITE;
			y = drawMeter(g, fm, gap(y), width, title, time, timeColor,
				BOSS_HEADER_BG, boss.report, bossRows, MeterMode.DAMAGE);
		}
		if (barrows != null)
		{
			boolean runPaused = plugin.isBarrowsRunPaused();
			y = drawMeter(g, fm, gap(y), width, "Barrows run", (runPaused ? "paused " : "") + barrows,
				runPaused ? Color.YELLOW : Color.WHITE, BOSS_HEADER_BG, null, Collections.emptyList(), MeterMode.DAMAGE);
		}
		return new Dimension(width, y);
	}

	/** Space between two meters, none above the first one. */
	private static int gap(int y)
	{
		return y > 0 ? y + 2 : 0;
	}

	private int drawMainMeter(Graphics2D g, FontMetrics fm, int width, MeterMode mode, FightReport report,
		List<FightReport.PlayerRow> rows)
	{
		boolean session = config.overlayScope() == GoldenDpsConfig.Scope.SESSION;
		String title = mode.getLabel() + (session ? " - Session" : " overall");
		if (config.bossOnly())
		{
			title += " (boss)";
		}
		if (mode == MeterMode.HEALING && report != null && report.groupOverhealPercent() > 0)
		{
			title += "  OH " + Format.percent(report.groupOverhealPercent());
		}
		boolean paused = plugin.isFightPaused();
		String right = report == null ? "" : (paused ? "paused " : "") + Format.duration(report.getDurationMs());
		Color rightColor = paused ? Color.YELLOW : plugin.isFightRunning() ? Color.WHITE : Color.LIGHT_GRAY;
		return drawMeter(g, fm, 0, width, title, right, rightColor, HEADER_BG, report, rows, mode);
	}

	/** Draws a header and its bars; returns the y below them. */
	private int drawMeter(Graphics2D g, FontMetrics fm, int top, int width, String title, String right,
		Color rightColor, Color headerBg, FightReport report, List<FightReport.PlayerRow> rows, MeterMode mode)
	{
		int rowH = fm.getHeight() + 3;
		int shown = Math.min(rows.size(), config.maxRows());
		// header, column labels (only with bars), bars
		int height = rowH * (shown + 1 + (shown > 0 ? 1 : 0)) + PAD;

		g.setColor(BACKGROUND);
		g.fillRect(0, top, width, height);
		g.setColor(headerBg);
		g.fillRect(0, top, width, rowH);

		int textY = rowH - (rowH - fm.getAscent()) / 2 - 1;
		int rightW = fm.stringWidth(right);
		drawText(g, fit(fm, title, width - rightW - 3 * PAD), PAD, top + textY, TITLE_COLOR);
		drawText(g, right, width - PAD - rightW, top + textY, rightColor);

		if (shown == 0)
		{
			return top + height;
		}

		// each column is as wide as its widest cell, laid out from the right
		boolean maxHit = config.showMaxHit();
		String[] labels = columnLabels(mode, maxHit);
		double total = report.total(mode);
		String[][] cells = new String[shown][];
		int[] colW = new int[labels.length];
		for (int c = 0; c < labels.length; c++)
		{
			colW[c] = fm.stringWidth(labels[c]);
		}
		for (int i = 0; i < shown; i++)
		{
			cells[i] = columnValues(rows.get(i), mode, total, maxHit);
			for (int c = 0; c < labels.length; c++)
			{
				colW[c] = Math.max(colW[c], fm.stringWidth(cells[i][c]));
			}
		}
		int[] colRight = new int[labels.length];
		int x = width - PAD - 2;
		for (int c = labels.length - 1; c >= 0; c--)
		{
			colRight[c] = x;
			x -= colW[c] + COL_GAP;
		}
		int nameMaxW = x - PAD - 2;

		int labelY = top + rowH + textY - 1;
		drawText(g, "Player", PAD + 2, labelY, CATEGORY_COLOR);
		for (int c = 0; c < labels.length; c++)
		{
			drawText(g, labels[c], colRight[c] - fm.stringWidth(labels[c]), labelY, CATEGORY_COLOR);
		}

		double max = FightReport.value(rows.get(0), mode);
		for (int i = 0; i < shown; i++)
		{
			FightReport.PlayerRow row = rows.get(i);
			int y = top + rowH * (i + 2) + 1;
			double value = FightReport.value(row, mode);

			g.setColor(BAR_BG);
			g.fillRect(PAD, y, width - 2 * PAD, rowH - 2);
			int barW = max <= 0 ? 0 : (int) Math.round((width - 2 * PAD) * value / max);
			Color barColor = colorFor(row);
			g.setColor(new Color(barColor.getRed(), barColor.getGreen(), barColor.getBlue(), 150));
			g.fillRect(PAD, y, barW, rowH - 2);

			int rowTextY = y + textY - 1;
			String left = (i + 1) + ". " + (row.isEstimate() ? "~" : "") + row.name;
			drawText(g, fit(fm, left, nameMaxW), PAD + 2, rowTextY, Color.WHITE);
			for (int c = 0; c < labels.length; c++)
			{
				drawText(g, cells[i][c], colRight[c] - fm.stringWidth(cells[i][c]), rowTextY, Color.WHITE);
			}
		}
		return top + height;
	}

	private Color colorFor(FightReport.PlayerRow row)
	{
		if (row.kind == PlayerKind.LOCAL)
		{
			return config.myColor();
		}
		if (row.kind == PlayerKind.UNKNOWN)
		{
			return UNKNOWN_COLOR;
		}
		if (row.kind == PlayerKind.EFFECTS)
		{
			return EFFECTS_COLOR;
		}
		return PALETTE[Math.floorMod(row.name.hashCode(), PALETTE.length)];
	}

	/** Column labels after the player name; damage modes get a "Max" (biggest hit) column. */
	static String[] columnLabels(MeterMode mode, boolean maxHit)
	{
		switch (mode)
		{
			case DPS:
				return maxHit ? new String[]{"DPS", "Damage", "Max", "%"} : new String[]{"DPS", "Damage", "%"};
			case HEALING:
				return new String[]{"Healing", "OH", "%"};
			case DAMAGE_TAKEN:
				return new String[]{"Taken", "%"};
			case DAMAGE:
			default:
				return maxHit ? new String[]{"Damage", "Max", "DPS", "%"} : new String[]{"Damage", "DPS", "%"};
		}
	}

	static String[] columnValues(FightReport.PlayerRow row, MeterMode mode, double total, boolean maxHit)
	{
		double value = FightReport.value(row, mode);
		String share = Format.percent(total <= 0 ? 0 : 100.0 * value / total);
		String max = row.biggestHit > 0 ? String.valueOf(row.biggestHit) : "-";
		switch (mode)
		{
			case DPS:
				return maxHit
					? new String[]{Format.dps(row.dps), Format.amount(row.damage), max, Format.percent(row.percent)}
					: new String[]{Format.dps(row.dps), Format.amount(row.damage), Format.percent(row.percent)};
			case HEALING:
				return new String[]{Format.amount(value), row.overheal > 0 ? Format.percent(row.overhealPercent()) : "-", share};
			case DAMAGE_TAKEN:
				return new String[]{Format.amount(value), share};
			case DAMAGE:
			default:
				return maxHit
					? new String[]{Format.amount(row.damage), max, Format.dps(row.dps), Format.percent(row.percent)}
					: new String[]{Format.amount(row.damage), Format.dps(row.dps), Format.percent(row.percent)};
		}
	}

	private static void drawText(Graphics2D g, String s, int x, int y, Color c)
	{
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	/** Shortens the text with ".." to fit. */
	private static String fit(FontMetrics fm, String s, int maxW)
	{
		if (fm.stringWidth(s) <= maxW)
		{
			return s;
		}
		String t = s;
		while (t.length() > 1 && fm.stringWidth(t + "..") > maxW)
		{
			t = t.substring(0, t.length() - 1);
		}
		return t + "..";
	}
}
