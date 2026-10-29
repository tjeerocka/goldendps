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

/** WoW-style bars: one colored bar per player, biggest on top. Right-click to change what it shows. */
class GoldenDpsOverlay extends Overlay
{
	/** WoW class colors, so party members get nice different colors. */
	private static final Color[] PALETTE = {
		new Color(0x3FC7EB), new Color(0xAAD372), new Color(0xF48CBA), new Color(0xC69B6D),
		new Color(0x8788EE), new Color(0x00FF98), new Color(0xFF7C0A), new Color(0x0070DD),
		new Color(0xC41E3A), new Color(0xA330C9), new Color(0x33937F), new Color(0xFFFFFF),
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
		addMenuEntry(MenuAction.RUNELITE_OVERLAY, "Report", "to party", e -> plugin.reportOverlayToParty());
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
		GoldenDpsPlugin.BossMeter boss = plugin.bossMeter();
		List<FightReport.PlayerRow> bossRows = boss == null ? Collections.emptyList() : boss.report.sorted(MeterMode.DAMAGE);
		if (rows.isEmpty() && bossRows.isEmpty() && config.hideWhenEmpty())
		{
			return null;
		}

		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics fm = g.getFontMetrics();
		int width = config.overlayWidth();

		// main meter: "Damage overall     0:42" (all targets; the boss meter has the boss name)
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
		int y = drawMeter(g, fm, 0, width, title, right, rightColor, HEADER_BG, report, rows, mode);

		// boss meter: only damage on the boss, DPS over the boss time
		if (boss != null && !bossRows.isEmpty())
		{
			y += 2;
			String bTitle = "Boss - " + boss.report.getTitle() + (boss.dead ? " (dead)" : "");
			String bRight = Format.duration(boss.report.getDurationMs())
				+ (boss.hidesIn >= 0 ? "  (" + boss.hidesIn + "s)" : "");
			y = drawMeter(g, fm, y, width, bTitle, bRight, boss.dead ? Color.LIGHT_GRAY : Color.WHITE,
				BOSS_HEADER_BG, boss.report, bossRows, MeterMode.DAMAGE);
		}
		return new Dimension(width, y);
	}

	/** One block: header and bars. Returns the y under it. */
	private int drawMeter(Graphics2D g, FontMetrics fm, int top, int width, String title, String right,
		Color rightColor, Color headerBg, FightReport report, List<FightReport.PlayerRow> rows, MeterMode mode)
	{
		int rowH = fm.getHeight() + 3;
		int shown = Math.min(rows.size(), config.maxRows());
		// header + category row (only when there are bars) + bars
		int height = rowH * (shown + 1 + (shown > 0 ? 1 : 0)) + PAD;

		g.setColor(BACKGROUND);
		g.fillRect(0, top, width, height);
		g.setColor(headerBg);
		g.fillRect(0, top, width, rowH);

		int textY = rowH - (rowH - fm.getAscent()) / 2 - 1;
		int rightW = fm.stringWidth(right);
		drawText(g, fit(fm, title, width - rightW - 3 * PAD), PAD, top + textY, new Color(255, 200, 0));
		drawText(g, right, width - PAD - rightW, top + textY, rightColor);

		if (shown == 0)
		{
			return top + height;
		}

		// columns: widest of the label and the values, laid out from the right
		String[] labels = columnLabels(mode);
		double total = report.total(mode);
		String[][] cells = new String[shown][];
		int[] colW = new int[labels.length];
		for (int c = 0; c < labels.length; c++)
		{
			colW[c] = fm.stringWidth(labels[c]);
		}
		for (int i = 0; i < shown; i++)
		{
			cells[i] = columnValues(rows.get(i), mode, total);
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

		// category row: "Player   Damage  DPS  %"
		int cy = top + rowH + textY - 1;
		drawText(g, "Player", PAD + 2, cy, CATEGORY_COLOR);
		for (int c = 0; c < labels.length; c++)
		{
			drawText(g, labels[c], colRight[c] - fm.stringWidth(labels[c]), cy, CATEGORY_COLOR);
		}

		double max = FightReport.value(rows.get(0), mode);
		for (int i = 0; i < shown; i++)
		{
			FightReport.PlayerRow row = rows.get(i);
			int y = top + rowH * (i + 2) + 1;
			double v = FightReport.value(row, mode);

			g.setColor(BAR_BG);
			g.fillRect(PAD, y, width - 2 * PAD, rowH - 2);
			int barW = max <= 0 ? 0 : (int) Math.round((width - 2 * PAD) * v / max);
			Color col = colorFor(row);
			g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 150));
			g.fillRect(PAD, y, barW, rowH - 2);

			int ty = y + textY - 1;
			String left = (i + 1) + ". " + (row.isEstimate() ? "~" : "") + row.name;
			drawText(g, fit(fm, left, nameMaxW), PAD + 2, ty, Color.WHITE);
			for (int c = 0; c < labels.length; c++)
			{
				drawText(g, cells[i][c], colRight[c] - fm.stringWidth(cells[i][c]), ty, Color.WHITE);
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

	/** Column categories per mode (after the player name). */
	static String[] columnLabels(MeterMode mode)
	{
		switch (mode)
		{
			case DPS:
				return new String[]{"DPS", "Damage", "%"};
			case HEALING:
				return new String[]{"Healing", "OH", "%"};
			case DAMAGE_TAKEN:
				return new String[]{"Taken", "%"};
			case DAMAGE:
			default:
				return new String[]{"Damage", "DPS", "%"};
		}
	}

	/** Values for the columns of {@link #columnLabels}. */
	static String[] columnValues(FightReport.PlayerRow row, MeterMode mode, double total)
	{
		double v = FightReport.value(row, mode);
		String share = Format.percent(total <= 0 ? 0 : 100.0 * v / total);
		switch (mode)
		{
			case DPS:
				return new String[]{Format.dps(row.dps), Format.amount(row.damage), Format.percent(row.percent)};
			case HEALING:
				return new String[]{Format.amount(v), row.overheal > 0 ? Format.percent(row.overhealPercent()) : "-", share};
			case DAMAGE_TAKEN:
				return new String[]{Format.amount(v), share};
			case DAMAGE:
			default:
				return new String[]{Format.amount(row.damage), Format.dps(row.dps), Format.percent(row.percent)};
		}
	}

	private static void drawText(Graphics2D g, String s, int x, int y, Color c)
	{
		g.setColor(Color.BLACK);
		g.drawString(s, x + 1, y + 1);
		g.setColor(c);
		g.drawString(s, x, y);
	}

	/** Cut text with ".." so it fits. */
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
