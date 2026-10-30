package com.goldendps;

import static com.goldendps.PanelWidgets.GOLD;
import static com.goldendps.PanelWidgets.actionRow;
import static com.goldendps.PanelWidgets.bold;
import static com.goldendps.PanelWidgets.button;
import static com.goldendps.PanelWidgets.line;
import static com.goldendps.PanelWidgets.note;
import static com.goldendps.PanelWidgets.section;
import static com.goldendps.PanelWidgets.small;
import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.KillRates;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/** Side panel with the fight details and the party tab. Swing thread only. */
class GoldenDpsPanel extends PluginPanel
{
	/** Ids of the two fight list choices that are not a saved fight. */
	static final String LIVE_CHOICE = "live";
	static final String SESSION_CHOICE = "session";

	static class Choice
	{
		final String id;
		final String label;

		Choice(String id, String label)
		{
			this.id = id;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	/** Everything the panel shows, built on the client thread. */
	static class Data
	{
		List<Choice> choices = new ArrayList<>();
		FightReport report;
		boolean running;
		boolean paused;
		boolean bossOnly;
		String endReason;
		int fights;
		long startMs;
		GoldenDpsPartyPanel.View party = new GoldenDpsPartyPanel.View();
		/** Boss damage over the boss time; null without a boss or in boss-only mode. */
		FightReport bossReport;
		/** {label, value} lines comparing a boss kill with the personal bests. */
		List<String[]> killLines = new ArrayList<>();
		List<BossRecords.Record> personalBests = new ArrayList<>();
		List<KillRates.Rate> killRates = new ArrayList<>();
		/** The live Barrows run time, or null without a run. */
		String barrowsRunTime;
		boolean barrowsRunPaused;
	}

	/** Sections you can show and hide; the name is saved, the flag is the default. */
	private enum Part
	{
		PER_HOUR(true),
		CHESTS(true),
		RAIDS(true),
		BOSSES(true),
		MONSTERS(false),
		FIGHT(true),
		PLAYERS(true),
		TARGETS(false),
		BESTS(false);

		final boolean openByDefault;

		Part(boolean openByDefault)
		{
			this.openByDefault = openByDefault;
		}
	}

	private static final String TAB_METER = "Meter";
	private static final String TAB_PARTY = "Party";
	private static final String NO_FIGHTS = "No fights yet. Hit something!";

	private final GoldenDpsPlugin plugin;
	private final JComboBox<Choice> fightBox = new JComboBox<>();
	private final GoldenDpsPartyPanel partyPanel;
	private final CardLayout cardLayout = new CardLayout();
	private final JPanel cards = new JPanel(cardLayout);
	private final JButton meterTab = tabButton(TAB_METER);
	private final JButton partyTab = tabButton(TAB_PARTY);
	private final JCheckBox bossOnlyBox = new JCheckBox("Boss only");
	private final JPanel content = new JPanel();
	private volatile String selectedId = LIVE_CHOICE;
	private volatile boolean active;
	private boolean updating;
	/** Sections whose show/hide differs from the default. */
	private final Set<Part> toggled = EnumSet.noneOf(Part.class);

	GoldenDpsPanel(GoldenDpsPlugin plugin)
	{
		this.plugin = plugin;
		this.partyPanel = new GoldenDpsPartyPanel(plugin);
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout(0, 8));

		JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		fightBox.setFocusable(false);
		fightBox.addActionListener(e ->
		{
			if (updating)
			{
				return;
			}
			Choice c = (Choice) fightBox.getSelectedItem();
			selectedId = c == null ? LIVE_CHOICE : c.id;
			plugin.requestPanelUpdate();
		});
		top.add(fightBox);

		bossOnlyBox.setBackground(ColorScheme.DARK_GRAY_COLOR);
		bossOnlyBox.setFocusable(false);
		bossOnlyBox.addActionListener(e ->
		{
			if (!updating)
			{
				plugin.setBossOnly(bossOnlyBox.isSelected());
			}
		});
		top.add(bossOnlyBox);
		top.add(fightButtons());
		top.add(reportButtons());

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel meterCard = new JPanel(new BorderLayout(0, 8));
		meterCard.setOpaque(false);
		meterCard.add(top, BorderLayout.NORTH);
		meterCard.add(content, BorderLayout.CENTER);
		JPanel partyCard = new JPanel(new BorderLayout());
		partyCard.setOpaque(false);
		partyCard.add(partyPanel, BorderLayout.NORTH);
		cards.setOpaque(false);
		cards.add(meterCard, TAB_METER);
		cards.add(partyCard, TAB_PARTY);

		add(tabBar(), BorderLayout.NORTH);
		add(cards, BorderLayout.CENTER);
		showTab(TAB_METER);

		loadToggles(plugin.panelToggles());
		content.add(note(NO_FIGHTS));
	}

	private JComponent fightButtons()
	{
		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(button("End fight", "End the running fight now", plugin::endFight));
		buttons.add(button("New session", "Start a new session total", plugin::resetSession));
		buttons.add(button("Clear", "Delete all saved fights", this::confirmClearHistory));
		return buttons;
	}

	private void confirmResetBest(BossRecords.Record rec)
	{
		int answer = JOptionPane.showConfirmDialog(this, "Delete the personal best for " + rec.label() + "?",
			"GoldenDPS", JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION)
		{
			plugin.resetPersonalBest(rec.boss, rec.party);
		}
	}

	private void confirmClearHistory()
	{
		int answer = JOptionPane.showConfirmDialog(this, "Delete all saved fights for this account?",
			"GoldenDPS", JOptionPane.YES_NO_OPTION);
		if (answer == JOptionPane.YES_OPTION)
		{
			plugin.clearHistory();
		}
	}

	/** "Report to party" with a button per report kind. */
	private JComponent reportButtons()
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.add(small("Report:", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		JPanel buttons = new JPanel(new GridLayout(1, ReportKind.values().length, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		for (ReportKind kind : ReportKind.values())
		{
			String text = kind.label.replace(" damage", "");
			buttons.add(button(text, "Send the top 5 (" + kind.label.toLowerCase() + ") of the shown fight to your party chat",
				() -> plugin.reportPanelToParty(kind)));
		}
		row.add(buttons, BorderLayout.CENTER);
		return row;
	}

	@Override
	public void onActivate()
	{
		active = true;
		plugin.requestPanelUpdate();
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	/** Safe to call from any thread. */
	boolean isPanelOpen()
	{
		return active;
	}

	String getSelectedId()
	{
		return selectedId;
	}

	static String label(Fight f, long now)
	{
		String time = new SimpleDateFormat("HH:mm").format(new Date(f.startMs));
		// bosses inside a raid are listed under the raid
		return time + "  " + (f.partOf != null ? "> " : "") + f.title() + "  " + Format.duration(f.durationMs(now));
	}

	private JComponent tabBar()
	{
		JLabel title = bold("GoldenDPS", GOLD);
		meterTab.addActionListener(e -> showTab(TAB_METER));
		partyTab.addActionListener(e -> showTab(TAB_PARTY));
		JPanel tabs = new JPanel(new GridLayout(1, 2, 0, 0));
		tabs.setOpaque(false);
		tabs.add(meterTab);
		tabs.add(partyTab);
		JPanel bar = new JPanel(new BorderLayout(0, 6));
		bar.setOpaque(false);
		bar.add(title, BorderLayout.NORTH);
		bar.add(tabs, BorderLayout.CENTER);
		return bar;
	}

	private static JButton tabButton(String text)
	{
		JButton b = new JButton(text);
		b.setFocusable(false);
		b.setContentAreaFilled(false);
		b.setFont(FontManager.getRunescapeBoldFont());
		return b;
	}

	private void showTab(String tab)
	{
		cardLayout.show(cards, tab);
		styleTab(meterTab, TAB_METER.equals(tab));
		styleTab(partyTab, TAB_PARTY.equals(tab));
		if (TAB_PARTY.equals(tab))
		{
			plugin.requestPanelUpdate();
		}
	}

	private static void styleTab(JButton b, boolean selected)
	{
		b.setForeground(selected ? GOLD : ColorScheme.LIGHT_GRAY_COLOR);
		b.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, selected ? GOLD : ColorScheme.MEDIUM_GRAY_COLOR),
			BorderFactory.createEmptyBorder(4, 0, 4, 0)));
	}

	void setPartyName(String name)
	{
		partyPanel.setPartyName(name);
	}

	void update(Data d)
	{
		updating = true;
		try
		{
			updateChoices(d.choices);
			bossOnlyBox.setSelected(d.bossOnly);
			partyPanel.update(d.party);
		}
		finally
		{
			updating = false;
		}

		content.removeAll();
		if (d.barrowsRunTime != null)
		{
			JPanel run = section();
			run.add(line("Barrows run", (d.barrowsRunPaused ? "paused " : "") + d.barrowsRunTime,
				d.barrowsRunPaused ? Color.YELLOW : GOLD));
			content.add(run);
		}
		addPerHour(d.killRates);
		addFight(d);
		addPersonalBests(d.personalBests);
		content.revalidate();
		content.repaint();
	}

	/** Kills per hour first, so chests and raids are always at the top. */
	private void addPerHour(List<KillRates.Rate> rates)
	{
		if (rates.isEmpty() || !addHeading("Per hour (this session)", Part.PER_HOUR, 0))
		{
			return;
		}
		addRates("Chests", rates, KillRates.Kind.RUN, Part.CHESTS);
		addRates("Raids", rates, KillRates.Kind.RAID, Part.RAIDS);
		addRates("Bosses", rates, KillRates.Kind.BOSS, Part.BOSSES);
		addRates("Monsters", rates, KillRates.Kind.MONSTER, Part.MONSTERS);
	}

	private void addFight(Data d)
	{
		FightReport r = d.report;
		if (r == null)
		{
			addHeading("Fight", null, 0);
			content.add(Box.createVerticalStrut(4));
			content.add(note(SESSION_CHOICE.equals(selectedId) ? "No fights in this session yet." : NO_FIGHTS));
			return;
		}
		if (addHeading("Fight", Part.FIGHT, 0))
		{
			content.add(Box.createVerticalStrut(4));
			content.add(FightCards.summary(d));
		}

		List<FightReport.PlayerRow> rows = new ArrayList<>(r.getRows());
		rows.sort((a, b) -> Long.compare(b.damage, a.damage));
		boolean solo = FightCards.isSolo(r);
		if (!rows.isEmpty() && addHeading(solo ? "Details" : "Players", Part.PLAYERS, solo ? 0 : rows.size()))
		{
			for (FightReport.PlayerRow row : rows)
			{
				content.add(Box.createVerticalStrut(4));
				content.add(FightCards.player(row, d.bossReport == null ? null : d.bossReport.row(row.name), solo));
			}
		}

		if (!r.getTargets().isEmpty() && addHeading("Damage per target", Part.TARGETS, r.getTargets().size()))
		{
			for (FightReport.TargetRow t : r.getTargets())
			{
				content.add(Box.createVerticalStrut(4));
				content.add(FightCards.target(t));
			}
		}
	}

	private void addPersonalBests(List<BossRecords.Record> bests)
	{
		if (bests.isEmpty() || !addHeading("Personal bests", Part.BESTS, bests.size()))
		{
			return;
		}
		content.add(Box.createVerticalStrut(4));
		JPanel pbs = section();
		for (BossRecords.Record rec : bests)
		{
			// the name gets its own line: long boss names left no room for "reset" next to them
			pbs.add(bold(rec.label(), Color.WHITE));
			pbs.add(actionRow(small(rec.kills + " kc", ColorScheme.LIGHT_GRAY_COLOR), "reset",
				"Delete this personal best", () -> confirmResetBest(rec)));
			pbs.add(line("Fastest", Format.duration(rec.fastestMs)));
			if (rec.bestDps > 0)
			{
				pbs.add(line("Best DPS", Format.dps(rec.bestDps)));
			}
		}
		content.add(pbs);
	}

	private boolean isOpen(Part part)
	{
		return part.openByDefault != toggled.contains(part);
	}

	private void toggle(Part part)
	{
		if (!toggled.remove(part))
		{
			toggled.add(part);
		}
		StringBuilder saved = new StringBuilder();
		for (Part p : toggled)
		{
			saved.append(saved.length() > 0 ? "," : "").append(p.name());
		}
		plugin.savePanelToggles(saved.toString());
		plugin.requestPanelUpdate();
	}

	/** @param saved part names, comma separated; unknown names are skipped */
	private void loadToggles(String saved)
	{
		for (String name : saved.split(","))
		{
			for (Part part : Part.values())
			{
				if (part.name().equals(name))
				{
					toggled.add(part);
				}
			}
		}
	}

	/** A click on the row shows or hides the part; "show" or "hide" on the right says which. */
	private JComponent toggleRow(JLabel title, Part part, int count)
	{
		if (count > 0)
		{
			title.setText(title.getText() + " (" + count + ")");
		}
		if (part == null)
		{
			return actionRow(title, null, null, null);
		}
		boolean open = isOpen(part);
		return actionRow(title, open ? "hide" : "show", open ? "Click to hide" : "Click to show", () -> toggle(part));
	}

	/** One card per kind: "Vorkath    12 kc  18.5/hr". */
	private void addRates(String title, List<KillRates.Rate> rates, KillRates.Kind kind, Part part)
	{
		int count = (int) rates.stream().filter(r -> r.kind == kind).count();
		if (count == 0)
		{
			return;
		}
		JPanel card = section();
		card.add(toggleRow(bold(title, Color.WHITE), part, count));
		for (KillRates.Rate rate : rates)
		{
			if (rate.kind == kind && isOpen(part))
			{
				card.add(rateLine(rate));
			}
		}
		content.add(Box.createVerticalStrut(4));
		content.add(card);
	}

	private static JComponent rateLine(KillRates.Rate rate)
	{
		String perHour = String.format("%.1f/hr", rate.perHour);
		JComponent line = line(rate.name, rate.kills + " kc  " + perHour);
		String counted = rate.kind == KillRates.Kind.RAID ? " completions" : rate.kind == KillRates.Kind.RUN ? " chests" : " kills";
		line.setToolTipText(rate.name + ": " + rate.kills + counted + ", average " + Format.duration(rate.averageMs));
		return line;
	}

	/** Adds a gold heading; returns true when its part is shown. */
	private boolean addHeading(String text, Part part, int count)
	{
		content.add(Box.createVerticalStrut(content.getComponentCount() == 0 ? 0 : 10));
		content.add(toggleRow(bold(text, GOLD), part, count));
		return part == null || isOpen(part);
	}

	private void updateChoices(List<Choice> choices)
	{
		if (fightBox.isPopupVisible())
		{
			// a new list would close the open list under your mouse
			return;
		}
		DefaultComboBoxModel<Choice> model = new DefaultComboBoxModel<>();
		Choice select = null;
		for (Choice c : choices)
		{
			model.addElement(c);
			if (c.id.equals(selectedId))
			{
				select = c;
			}
		}
		if (select == null && !choices.isEmpty())
		{
			// the selected fight is gone: fall back to the live view
			select = choices.get(0);
			selectedId = select.id;
		}
		fightBox.setModel(model);
		if (select != null)
		{
			fightBox.setSelectedItem(select);
		}
	}
}
