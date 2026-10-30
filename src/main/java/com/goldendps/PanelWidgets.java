package com.goldendps;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;

/** The small Swing pieces both panel tabs are built from. Swing thread only. */
final class PanelWidgets
{
	static final Color GOLD = new Color(255, 196, 0);

	private PanelWidgets()
	{
	}

	static JButton button(String text, String tip, Runnable action)
	{
		JButton b = new JButton(text);
		b.setToolTipText(tip);
		b.setFocusable(false);
		b.setMargin(new Insets(2, 2, 2, 2));
		b.setFont(FontManager.getRunescapeSmallFont());
		b.addActionListener(e -> action.run());
		return b;
	}

	static JLabel small(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(color);
		l.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return l;
	}

	static JLabel bold(String text, Color color)
	{
		JLabel l = new JLabel(text);
		l.setFont(FontManager.getRunescapeBoldFont());
		l.setForeground(color);
		return l;
	}

	/** A gray hint in the default font. */
	static JLabel note(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		l.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return l;
	}

	/** A dark card that holds lines. */
	static JPanel section()
	{
		// never taller than its lines, also when the panel has room to spare
		JPanel p = new JPanel(new GridLayout(0, 1, 0, 1))
		{
			@Override
			public Dimension getMaximumSize()
			{
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		p.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		p.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
		p.setAlignmentX(JComponent.LEFT_ALIGNMENT);
		return p;
	}

	static JComponent line(String key, String value)
	{
		return line(key, value, null);
	}

	/** A title on the left and a small gray action word ("hide", "reset") on the right; a click runs it. */
	static JComponent actionRow(JLabel title, String action, String tip, Runnable onClick)
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.add(title, BorderLayout.WEST);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		if (action == null)
		{
			return row;
		}
		row.add(small(action, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		row.setToolTipText(tip);
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				onClick.run();
			}
		});
		return row;
	}

	/** A key on the left and a value on the right; {@code highlight} colors both. */
	static JComponent line(String key, String value, Color highlight)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		JLabel keyLabel = new JLabel(key);
		keyLabel.setForeground(highlight != null ? highlight : ColorScheme.LIGHT_GRAY_COLOR);
		keyLabel.setFont(FontManager.getRunescapeSmallFont());
		JLabel valueLabel = new JLabel(value);
		valueLabel.setForeground(highlight != null ? highlight : Color.WHITE);
		valueLabel.setFont(FontManager.getRunescapeSmallFont());
		p.add(keyLabel, BorderLayout.WEST);
		p.add(valueLabel, BorderLayout.EAST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 16));
		return p;
	}
}
