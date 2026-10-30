package com.goldendps;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Draws the crown over the MVP and the snacks with hungry lines over the top healers. */
class HeadIconOverlay extends Overlay
{
	/** Above the head, clear of overhead prayers and skulls. */
	private static final int ABOVE_HEAD = 90;
	/** Height of one icon in the stack above a head. */
	private static final int ICON_STEP = 22;
	private static final Color TALK_COLOR = new Color(255, 255, 0);

	private final Client client;
	private final GoldenDpsConfig config;
	private final FunEffects fun;

	@Inject
	HeadIconOverlay(Client client, GoldenDpsConfig config, GoldenDpsPlugin plugin)
	{
		this.client = client;
		this.config = config;
		this.fun = plugin.fun();
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		long now = System.currentTimeMillis();
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || !fun.anyone())
		{
			return null;
		}
		g.setFont(FontManager.getRunescapeBoldFont());
		for (Player p : wv.players())
		{
			String name = p == null ? null : Names.clean(p.getName());
			if (name != null)
			{
				drawAbove(g, p, name, now);
			}
		}
		return null;
	}

	/** Stacks the icons upwards from the head: burger, fries, crown, then what the player says. */
	private void drawAbove(Graphics2D g, Player p, String name, long now)
	{
		int height = p.getLogicalHeight() + ABOVE_HEAD;
		boolean snacks = config.healerSnack();
		if (snacks && fun.hasBurger(name))
		{
			drawIcon(g, p, FunIcons.BURGER, height);
			height += ICON_STEP;
		}
		if (snacks && fun.hasFries(name))
		{
			drawIcon(g, p, FunIcons.FRIES, height);
			height += ICON_STEP;
		}
		if (config.mvpCrown() && fun.hasCrown(name))
		{
			drawIcon(g, p, FunIcons.CROWN, height);
			height += ICON_STEP;
		}
		String line = snacks ? fun.line(name, now) : null;
		if (line != null)
		{
			drawText(g, p, line, height + 8);
		}
	}

	private static void drawIcon(Graphics2D g, Player p, BufferedImage icon, int height)
	{
		Point at = p.getCanvasImageLocation(icon, height);
		if (at != null)
		{
			g.drawImage(icon, at.getX(), at.getY(), null);
		}
	}

	private static void drawText(Graphics2D g, Player p, String text, int height)
	{
		Point at = p.getCanvasTextLocation(g, text, height);
		if (at != null)
		{
			g.setColor(Color.BLACK);
			g.drawString(text, at.getX() + 1, at.getY() + 1);
			g.setColor(TALK_COLOR);
			g.drawString(text, at.getX(), at.getY());
		}
	}
}
