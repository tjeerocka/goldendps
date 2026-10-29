package com.goldendps;

import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.PlayerKind;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
	}

	private static final Color GOLD = new Color(255, 196, 0);
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
	private volatile String selectedId = GoldenDpsPlugin.LIVE;
	private volatile boolean active;
	private boolean updating;

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
			selectedId = c == null ? GoldenDpsPlugin.LIVE : c.id;
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

		JPanel buttons = new JPanel(new GridLayout(1, 3, 4, 0));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(button("End fight", "End the running fight now", plugin::endFight));
		buttons.add(button("New session", "Start a new session total", plugin::resetSession));
		buttons.add(button("Clear", "Delete all saved fights", () ->
		{
			int ok = JOptionPane.showConfirmDialog(this, "Delete all saved fights for this account?",
				"GoldenDPS", JOptionPane.YES_NO_OPTION);
			if (ok == JOptionPane.YES_OPTION)
			{
				plugin.clearHistory();
			}
		}));
		top.add(buttons);
		top.add(button("Report to party", "Send the top 5 of the shown fight to your RuneLite party chat",
			plugin::reportPanelToParty));

		JPanel tests = new JPanel(new GridLayout(1, 3, 4, 0));
		tests.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tests.add(button("Test crown", "Show the MVP crown on you for 15 seconds", plugin::testCrown));
		tests.add(button("Test snacks", "Show the burger, fries and hungry lines on you for 15 seconds", plugin::testSnacks));
		tests.add(button("Test max hit", "Show the max hit line over your head", plugin::testMaxHit));
		top.add(tests);

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

		showEmpty(NO_FIGHTS);
	}

	private static JButton button(String text, String tip, Runnable r)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tip);
		b.setFocusable(false);
		b.setMargin(new Insets(2, 2, 2, 2));
		b.setFont(FontManager.getRunescapeSmallFont());
		b.addActionListener(e -> r.run());
		return b;
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
		JLabel title = new JLabel("GoldenDPS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(GOLD);
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

		if (d.report == null)
		{
			showEmpty(GoldenDpsPlugin.SESSION.equals(selectedId) ? "No fights in this session yet." : NO_FIGHTS);
			return;
		}
		FightReport r = d.report;
		content.removeAll();
		content.add(summary(d));

		List<FightReport.PlayerRow> rows = new ArrayList<>(r.getRows());
		rows.sort((a, b) -> Long.compare(b.damage, a.damage));
		for (FightReport.PlayerRow row : rows)
		{
			content.add(Box.createVerticalStrut(6));
			content.add(playerCard(row, d.bossReport == null ? null : d.bossReport.row(row.name)));
		}

		if (!r.getTargets().isEmpty())
		{
			addHeading("Damage per target");
			for (FightReport.TargetRow t : r.getTargets())
			{
				content.add(Box.createVerticalStrut(4));
				content.add(targetCard(t));
			}
		}

		if (!d.personalBests.isEmpty())
		{
			addHeading("Personal bests");
			content.add(Box.createVerticalStrut(4));
			JPanel pbs = section();
			for (BossRecords.Record rec : d.personalBests)
			{
				pbs.add(bold(rec.boss + "  (" + rec.kills + " kc)", Color.WHITE));
				pbs.add(line("Fastest", Format.duration(rec.fastestMs)));
				if (rec.bestDps > 0)
				{
					pbs.add(line("Best DPS", Format.dps(rec.bestDps)));
				}
			}
			content.add(pbs);
		}

		content.revalidate();
		content.repaint();
	}

	private JPanel summary(Data d)
	{
		FightReport r = d.report;
		JPanel sum = section();
		sum.add(bold(r.getTitle(), GOLD));
		sum.add(line("Time", Format.duration(r.getDurationMs()) + (d.paused ? " (paused)" : d.running ? " (live)" : "")));
		if (d.fights > 0)
		{
			sum.add(line("Fights", String.valueOf(d.fights)));
		}
		else
		{
			sum.add(line("Started", new SimpleDateFormat("dd MMM HH:mm").format(new Date(d.startMs))));
		}
		sum.add(line("Group damage", Format.amount(r.getTotalDamage())));
		sum.add(line("Group DPS", Format.dps(r.getGroupDps())));
		FightReport.PlayerRow topHit = r.topHit();
		if (topHit != null)
		{
			sum.add(line("Max hit", topHit.biggestHit + " (" + topHit.name + ")"));
		}
		if (d.bossReport != null)
		{
			sum.add(line("Boss time", Format.duration(d.bossReport.getDurationMs())));
			sum.add(line("Boss damage", Format.amount(d.bossReport.getTotalDamage())));
			sum.add(line("Boss DPS", Format.dps(d.bossReport.getGroupDps())));
		}
		for (String[] kl : d.killLines)
		{
			Color c = kl[1].contains("NEW") ? GOLD : null;
			sum.add(line(kl[0], kl[1], c));
		}
		if (d.endReason != null && d.fights == 0)
		{
			sum.add(line("Ended", d.endReason));
		}
		return sum;
	}

	private void addHeading(String text)
	{
		content.add(Box.createVerticalStrut(10));
		JLabel h = bold(text, GOLD);
		h.setAlignmentX(LEFT_ALIGNMENT);
		content.add(h);
	}

	private void updateChoices(List<Choice> choices)
	{
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

	private JPanel playerCard(FightReport.PlayerRow row, FightReport.PlayerRow bossRow)
	{
		JPanel card = section();
		Color nameColor = row.kind == PlayerKind.LOCAL ? GOLD
			: row.kind == PlayerKind.PARTY ? Color.WHITE : ColorScheme.LIGHT_GRAY_COLOR;
		String name = (row.isEstimate() ? "~" : "") + row.name
			+ (row.kind == PlayerKind.LOCAL ? " (you)" : row.isEstimate() ? " (guess)" : "");
		card.add(bold(name, nameColor));
		card.add(line("Damage", Format.amount(row.damage) + "  (" + Format.percent(row.percent) + ")"));
		card.add(line("DPS", Format.dps(row.dps)));
		if (bossRow != null && bossRow.damage > 0)
		{
			card.add(line("Boss damage", Format.amount(bossRow.damage) + "  (" + Format.percent(bossRow.percent) + ")"));
			card.add(line("Boss DPS", Format.dps(bossRow.dps)));
		}
		if (!row.isPlayer())
		{
			String why = row.kind == PlayerKind.UNKNOWN
				? "Hits nobody could be matched to"
				: "Owner not known";
			card.add(line("", why));
			return card;
		}
		card.add(line("Biggest hit", String.valueOf(row.biggestHit)));
		double acc = row.accuracy();
		card.add(line("Accuracy", acc < 0 ? "-" : String.format("%.0f%% (%d/%d)", acc, row.hits, row.hits + row.misses)));
		if (!row.sources.isEmpty())
		{
			card.add(line("Breakdown", ""));
			for (FightReport.SourceRow s : row.sources)
			{
				JComponent l = line("  " + s.name, Format.amount(s.damage) + "  (" + Format.percent(s.percent) + ")");
				l.setToolTipText(String.format("%s: %d hits, %d misses, biggest %d, average %.1f per attack",
					s.name, s.hits, s.misses, s.biggestHit, s.average()));
				card.add(l);
			}
		}
		if (row.isEstimate())
		{
			card.add(line("", "No GoldenDPS: damage is a guess"));
			card.add(line("Damage taken", Format.amount(row.damageTaken)));
			return card;
		}
		card.add(line("Healing", Format.amount(row.healing)));
		if (row.healing > 0 || row.healRegen > 0)
		{
			if (row.healFood > 0)
			{
				card.add(line("  Food/potions", Format.amount(row.healFood)));
			}
			if (row.healLifesteal > 0)
			{
				card.add(line("  Lifesteal", Format.amount(row.healLifesteal)));
			}
			if (row.healOther > 0)
			{
				card.add(line("  On others", Format.amount(row.healOther)));
			}
			if (row.healRegen > 0)
			{
				card.add(line("  Regen", Format.amount(row.healRegen)));
			}
		}
		if (row.overheal > 0)
		{
			card.add(line("Overhealing", Format.percent(row.overhealPercent()) + "  (" + Format.amount(row.overheal) + ")"));
			if (row.overFood > 0)
			{
				card.add(line("  Food/potions", Format.amount(row.overFood)));
			}
			if (row.overLifesteal > 0)
			{
				card.add(line("  Lifesteal", Format.amount(row.overLifesteal)));
			}
		}
		card.add(line("Damage taken", Format.amount(row.damageTaken)));
		return card;
	}

	private JPanel targetCard(FightReport.TargetRow t)
	{
		JPanel card = section();
		String tags = (t.boss ? " [boss]" : "") + (t.died ? " [dead]" : "");
		card.add(bold(t.name + tags, Color.WHITE));
		card.add(line("Total", Format.amount(t.total)));
		for (Map.Entry<String, Long> e : t.byPlayer.entrySet())
		{
			double pct = t.total == 0 ? 0 : 100.0 * e.getValue() / t.total;
			card.add(line("  " + e.getKey(), Format.amount(e.getValue()) + " (" + Format.percent(pct) + ")"));
		}
		return card;
	}

	private void showEmpty(String text)
	{
		content.removeAll();
		JLabel l = new JLabel(text);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(LEFT_ALIGNMENT);
		content.add(l);
		content.revalidate();
		content.repaint();
	}

	private static JPanel section()
	{
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 1));
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private static JLabel bold(String text, Color c)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(c);
		return l;
	}

	private static JComponent line(String key, String value)
	{
		return line(key, value, null);
	}

	/** A key on the left and a value on the right; {@code highlight} colors both. */
	private static JComponent line(String key, String value, Color highlight)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		JLabel k = new JLabel(key);
		k.setForeground(highlight != null ? highlight : ColorScheme.LIGHT_GRAY_COLOR);
		k.setFont(FontManager.getRunescapeSmallFont());
		JLabel v = new JLabel(value);
		v.setForeground(highlight != null ? highlight : Color.WHITE);
		v.setFont(FontManager.getRunescapeSmallFont());
		p.add(k, BorderLayout.WEST);
		p.add(v, BorderLayout.EAST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		return p;
	}
}
