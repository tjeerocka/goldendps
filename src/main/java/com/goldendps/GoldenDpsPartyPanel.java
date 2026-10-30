package com.goldendps;

import static com.goldendps.PanelWidgets.GOLD;
import static com.goldendps.PanelWidgets.bold;
import static com.goldendps.PanelWidgets.small;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.Timer;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** The party tab of the side panel. Swing thread only. */
class GoldenDpsPartyPanel extends JPanel
{
	static class Member
	{
		long id;
		String name;
		boolean online;
		boolean you;
		boolean leader;
		boolean hasPlugin;
	}

	/** Everything the tab shows, built on the client thread. */
	static class View
	{
		boolean inParty;
		String code;
		boolean youLead;
		/** "Raid party" or "Own party" when GoldenDPS joined it, else null. */
		String autoLabel;
		List<Member> members = new ArrayList<>();
	}

	private static final Color ONLINE = new Color(90, 200, 90);
	private static final Color OFFLINE = new Color(110, 110, 110);
	private static final Color KICK = new Color(170, 50, 50);
	/** Kick needs a second click within this time. */
	private static final int KICK_CONFIRM_MS = 4000;
	private static final String JOIN_TIP = "Join (or start) the party with this name";
	private static final String RANDOM_TIP = "Fill in a random party name";
	private static final String LEAVE_FIRST = "Leave this party first";

	private final GoldenDpsPlugin plugin;
	private final JLabel status = new JLabel();
	private final JTextField nameField = new JTextField();
	private final JPanel memberList = new JPanel();
	private final JButton joinButton;
	private final JButton randomButton;
	private final JButton copyButton;
	private final JButton leaveButton;
	private View lastView = new View();
	private String shownCode;
	private long confirmKickId = -1;
	private long confirmUntil;

	GoldenDpsPartyPanel(GoldenDpsPlugin plugin)
	{
		this.plugin = plugin;
		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARKER_GRAY_COLOR);
		setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 3, 0, 0, GOLD),
			BorderFactory.createEmptyBorder(6, 8, 8, 8)));

		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel title = bold("Party", GOLD);
		status.setFont(FontManager.getRunescapeSmallFont());
		header.add(title, BorderLayout.WEST);
		header.add(status, BorderLayout.EAST);

		JLabel nameLabel = small("Party name", ColorScheme.LIGHT_GRAY_COLOR);
		nameLabel.setToolTipText("Everyone who joins with the same name is in the same party");
		nameField.setToolTipText("Type a party name (spaces are fine), or press Random");
		nameField.addActionListener(e -> join());
		joinButton = button("Join", JOIN_TIP, ColorScheme.BRAND_ORANGE, this::join);
		JPanel nameRow = new JPanel(new BorderLayout(4, 0));
		nameRow.setOpaque(false);
		nameRow.add(nameField, BorderLayout.CENTER);
		nameRow.add(joinButton, BorderLayout.EAST);

		JPanel actions = new JPanel(new GridLayout(1, 3, 4, 0));
		actions.setOpaque(false);
		randomButton = button("Random", RANDOM_TIP, null, plugin::randomPartyName);
		actions.add(randomButton);
		copyButton = button("Copy", "Copy the party name to send to your friends", null, this::copy);
		actions.add(copyButton);
		leaveButton = button("Leave", "Leave the party", null, plugin::leaveParty);
		actions.add(leaveButton);

		JPanel top = new JPanel(new GridLayout(0, 1, 0, 4));
		top.setOpaque(false);
		top.add(header);
		top.add(nameLabel);
		top.add(nameRow);
		top.add(actions);

		memberList.setLayout(new BoxLayout(memberList, BoxLayout.Y_AXIS));
		memberList.setOpaque(false);

		add(top, BorderLayout.NORTH);
		add(memberList, BorderLayout.CENTER);
		update(new View());
	}

	void setPartyName(String name)
	{
		nameField.setText(name);
		nameField.requestFocusInWindow();
	}

	void update(View v)
	{
		lastView = v;
		if (v.inParty)
		{
			int online = 0;
			for (Member m : v.members)
			{
				online += m.online ? 1 : 0;
			}
			status.setText(online + " online");
			status.setForeground(ONLINE);
			if (v.code != null && !v.code.equals(nameField.getText()))
			{
				nameField.setText(v.code);
			}
		}
		else
		{
			status.setText("not in a party");
			status.setForeground(OFFLINE);
		}
		shownCode = v.inParty ? v.code : null;
		leaveButton.setEnabled(v.inParty);
		copyButton.setEnabled(v.inParty);
		// switching party needs an explicit Leave first
		nameField.setEditable(!v.inParty);
		randomButton.setEnabled(!v.inParty);
		joinButton.setEnabled(!v.inParty);
		joinButton.setBackground(v.inParty ? ColorScheme.DARKER_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
		randomButton.setToolTipText(v.inParty ? LEAVE_FIRST : RANDOM_TIP);
		joinButton.setToolTipText(v.inParty ? LEAVE_FIRST : JOIN_TIP);
		nameField.setToolTipText(v.inParty ? "Your party name. " + LEAVE_FIRST + " to pick another one."
			: "Type a party name (spaces are fine), or press Random");

		memberList.removeAll();
		if (v.inParty)
		{
			if (v.autoLabel != null)
			{
				memberList.add(small(v.autoLabel, GOLD));
			}
			memberList.add(small(v.youLead ? "Members - you lead" : "Members", ColorScheme.LIGHT_GRAY_COLOR));
			for (Member m : v.members)
			{
				memberList.add(memberRow(m, v.youLead));
			}
		}
		memberList.revalidate();
		memberList.repaint();
	}

	private JComponent memberRow(Member m, boolean youLead)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 4));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		row.setAlignmentX(LEFT_ALIGNMENT);

		row.add(new Dot(m.online ? ONLINE : OFFLINE), BorderLayout.WEST);

		JPanel names = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		names.setOpaque(false);
		JLabel name = new JLabel((m.leader ? "★ " : "") + m.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(m.leader ? GOLD : m.online ? Color.WHITE : OFFLINE);
		name.setToolTipText(m.leader ? "Party leader" : null);
		names.add(name);
		if (m.you)
		{
			names.add(small("(you)", ColorScheme.LIGHT_GRAY_COLOR));
		}
		else if (!m.hasPlugin)
		{
			JLabel noPlugin = small("no GoldenDPS", OFFLINE);
			noPlugin.setToolTipText("Uses only the Party plugin: their damage is a guess, and they can't be kicked");
			names.add(noPlugin);
		}
		row.add(names, BorderLayout.CENTER);

		if (youLead && !m.you)
		{
			boolean sure = confirmKickId == m.id && System.currentTimeMillis() < confirmUntil;
			JButton kick = button(sure ? "Sure?" : "Kick", !m.hasPlugin ? "Can't kick: " + m.name + " does not run GoldenDPS"
				: sure ? "Click again to remove " + m.name : "Remove " + m.name + " from the party (click twice)",
				KICK, () -> kickClicked(m));
			kick.setEnabled(m.hasPlugin);
			row.add(kick, BorderLayout.EAST);
		}

		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setOpaque(false);
		wrap.setBorder(BorderFactory.createEmptyBorder(3, 0, 0, 0));
		wrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, 27));
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		wrap.add(row);
		return wrap;
	}

	private void kickClicked(Member m)
	{
		long now = System.currentTimeMillis();
		if (confirmKickId == m.id && now < confirmUntil)
		{
			confirmKickId = -1;
			plugin.kickFromParty(m.id, m.name);
			return;
		}
		confirmKickId = m.id;
		confirmUntil = now + KICK_CONFIRM_MS;
		update(lastView);
		Timer reset = new Timer(KICK_CONFIRM_MS + 50, e -> update(lastView));
		reset.setRepeats(false);
		reset.start();
	}

	private void join()
	{
		if (!joinButton.isEnabled())
		{
			return;
		}
		String name = nameField.getText() == null ? "" : nameField.getText().trim().replaceAll("\\s+", " ");
		if (!name.isEmpty())
		{
			plugin.joinParty(name);
		}
	}

	private void copy()
	{
		if (shownCode != null)
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(shownCode), null);
		}
	}

	/** @param background null for the default look */
	private static JButton button(String text, String tip, Color background, Runnable action)
	{
		JButton b = PanelWidgets.button(text, tip, action);
		if (background != null)
		{
			b.setBackground(background);
			b.setForeground(Color.WHITE);
		}
		return b;
	}

	/** Online / offline dot. */
	private static class Dot extends JComponent
	{
		private final Color color;

		Dot(Color color)
		{
			this.color = color;
			setPreferredSize(new Dimension(10, 16));
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setColor(color);
			int d = 8;
			g2.fillOval((getWidth() - d) / 2, (getHeight() - d) / 2, d, d);
			g2.dispose();
		}
	}
}
