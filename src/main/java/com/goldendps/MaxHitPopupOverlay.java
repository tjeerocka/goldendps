package com.goldendps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayPosition;

/** A short "NEW MAX HIT!" banner at the top of the screen. */
class MaxHitPopupOverlay extends Overlay
{
	private static final Color GOLD = new Color(255, 196, 0);
	private static final Color BACKGROUND = new Color(20, 20, 20, 190);
	private static final int PAD = 6;

	private final GoldenDpsConfig config;
	private final FunEffects fun;

	@Inject
	MaxHitPopupOverlay(GoldenDpsConfig config, GoldenDpsPlugin plugin)
	{
		this.config = config;
		this.fun = plugin.fun();
		setPosition(OverlayPosition.TOP_CENTER);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		String text = config.maxHitPopup() ? fun.popup(System.currentTimeMillis()) : null;
		if (text == null)
		{
			return null;
		}
		Font font = FontManager.getRunescapeBoldFont();
		g.setFont(font.deriveFont(font.getSize2D() * 1.5f));
		FontMetrics fm = g.getFontMetrics();
		int w = fm.stringWidth(text) + 2 * PAD;
		int h = fm.getHeight() + PAD;
		g.setColor(BACKGROUND);
		g.fillRoundRect(0, 0, w, h, 8, 8);
		g.setColor(Color.BLACK);
		g.drawString(text, PAD + 1, fm.getAscent() + PAD / 2 + 1);
		g.setColor(GOLD);
		g.drawString(text, PAD, fm.getAscent() + PAD / 2);
		return new Dimension(w, h);
	}
}
