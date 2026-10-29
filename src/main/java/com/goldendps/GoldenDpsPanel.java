package com.goldendps;

import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.PlayerKind;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
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

/** Side panel with all the details. Only touched on the Swing thread. */
class GoldenDpsPanel extends PluginPanel
{
	/** One entry in the fight list. */
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

	/** Everything the panel shows, made on the client thread. */
	static class Data
	{
		List<Choice> choices = new ArrayList<>();
		FightReport report;
		boolean running;
		boolean bossOnly;
		String endReason;
		int fights;
		long startMs;
		String localName;
		/** Only boss damage over the boss time. Null when no boss was fought (or boss only is on). */
		FightReport bossReport;
	}

	private static final Color GOLD = new Color(255, 196, 0);

	private final GoldenDpsPlugin plugin;
	private final JComboBox<Choice> fightBox = new JComboBox<>();
	private final JCheckBox bossOnlyBox = new JCheckBox("Boss only");
	private final JPanel content = new JPanel();
	private volatile String selectedId = GoldenDpsPlugin.LIVE;
	private volatile boolean active;
	private boolean updating;

	GoldenDpsPanel(GoldenDpsPlugin plugin)
	{
		this.plugin = plugin;
		setBorder(new EmptyBorder(8, 8, 8, 8));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setLayout(new BorderLayout(0, 8));

		JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
		top.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("GoldenDPS");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(GOLD);
		top.add(title);

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
		add(top, BorderLayout.NORTH);

		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);
		add(content, BorderLayout.CENTER);

		showEmpty("No fights yet. Hit something!");
	}

	private static JButton button(String text, String tip, Runnable r)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tip);
		b.setFocusable(false);
		b.setMargin(new java.awt.Insets(2, 2, 2, 2));
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

	/** True while the panel is open (safe to call from any thread). */
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
		return time + "  " + f.title() + "  " + Format.duration(f.durationMs(now));
	}

	void update(Data d)
	{
		updating = true;
		try
		{
			updateChoices(d.choices);
			bossOnlyBox.setSelected(d.bossOnly);
		}
		finally
		{
			updating = false;
		}

		content.removeAll();
		if (d.report == null)
		{
			showEmpty(GoldenDpsPlugin.SESSION.equals(selectedId) ? "No fights in this session yet." : "No fights yet. Hit something!");
			return;
		}
		FightReport r = d.report;

		// summary
		JPanel sum = section();
		sum.add(bold(r.getTitle(), GOLD));
		sum.add(line("Time", Format.duration(r.getDurationMs()) + (d.running ? " (live)" : "")));
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
		if (d.bossReport != null)
		{
			sum.add(line("Boss time", Format.duration(d.bossReport.getDurationMs())));
			sum.add(line("Boss damage", Format.amount(d.bossReport.getTotalDamage())));
			sum.add(line("Boss DPS", Format.dps(d.bossReport.getGroupDps())));
		}
		if (d.endReason != null && d.fights == 0)
		{
			sum.add(line("Ended", d.endReason));
		}
		content.add(sum);

		// players, biggest damage first
		List<FightReport.PlayerRow> rows = new ArrayList<>(r.getRows());
		rows.sort((a, b) -> Long.compare(b.damage, a.damage));
		for (FightReport.PlayerRow row : rows)
		{
			content.add(Box.createVerticalStrut(6));
			content.add(playerCard(row, d.bossReport == null ? null : d.bossReport.row(row.name)));
		}

		// targets
		if (!r.getTargets().isEmpty())
		{
			content.add(Box.createVerticalStrut(10));
			JLabel h = bold("Damage per target", GOLD);
			h.setAlignmentX(LEFT_ALIGNMENT);
			content.add(h);
			for (FightReport.TargetRow t : r.getTargets())
			{
				content.add(Box.createVerticalStrut(4));
				content.add(targetCard(t));
			}
		}

		content.revalidate();
		content.repaint();
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
			// the selected fight was deleted: go back to the live view
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
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		JLabel k = new JLabel(key);
		k.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		k.setFont(FontManager.getRunescapeSmallFont());
		JLabel v = new JLabel(value);
		v.setForeground(Color.WHITE);
		v.setFont(FontManager.getRunescapeSmallFont());
		p.add(k, BorderLayout.WEST);
		p.add(v, BorderLayout.EAST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		return p;
	}
}
