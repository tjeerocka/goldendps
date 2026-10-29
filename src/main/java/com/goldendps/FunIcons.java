package com.goldendps;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

/** Small icons drawn in code, so the plugin needs no extra image files. */
final class FunIcons
{
	static final BufferedImage CROWN = crown();
	static final BufferedImage BURGER = burger();
	static final BufferedImage FRIES = fries();

	private static final Color OUTLINE = new Color(40, 25, 10);

	private FunIcons()
	{
	}

	private static Graphics2D canvas(BufferedImage img)
	{
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setStroke(new BasicStroke(1.2f));
		return g;
	}

	private static BufferedImage crown()
	{
		BufferedImage img = new BufferedImage(22, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas(img);
		Polygon p = new Polygon(new int[]{2, 2, 6, 11, 16, 20, 20}, new int[]{14, 4, 9, 2, 9, 4, 14}, 7);
		g.setColor(new Color(255, 196, 0));
		g.fillPolygon(p);
		g.setColor(OUTLINE);
		g.drawPolygon(p);
		g.setColor(new Color(220, 40, 60));
		g.fillOval(9, 9, 4, 4);
		g.setColor(new Color(60, 140, 255));
		g.fillOval(4, 10, 3, 3);
		g.fillOval(15, 10, 3, 3);
		g.dispose();
		return img;
	}

	private static BufferedImage burger()
	{
		BufferedImage img = new BufferedImage(20, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas(img);
		g.setColor(new Color(225, 150, 60));
		g.fillArc(1, 1, 18, 11, 0, 180);
		g.setColor(new Color(90, 190, 60));
		g.fillRect(1, 7, 18, 2);
		g.setColor(new Color(110, 60, 30));
		g.fillRoundRect(1, 9, 18, 3, 2, 2);
		g.setColor(new Color(225, 150, 60));
		g.fillRoundRect(2, 12, 16, 3, 3, 3);
		g.setColor(OUTLINE);
		g.drawArc(1, 1, 18, 11, 0, 180);
		g.drawRoundRect(2, 12, 16, 3, 3, 3);
		g.setColor(new Color(255, 240, 200));
		g.fillRect(6, 3, 1, 1);
		g.fillRect(11, 2, 1, 1);
		g.fillRect(14, 4, 1, 1);
		g.dispose();
		return img;
	}

	private static BufferedImage fries()
	{
		BufferedImage img = new BufferedImage(16, 20, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = canvas(img);
		g.setColor(new Color(250, 210, 70));
		for (int x = 3; x <= 11; x += 2)
		{
			g.fillRect(x, 1 + (x % 3), 2, 10);
		}
		Polygon box = new Polygon(new int[]{1, 15, 13, 3}, new int[]{8, 8, 19, 19}, 4);
		g.setColor(new Color(210, 40, 40));
		g.fillPolygon(box);
		g.setColor(OUTLINE);
		g.drawPolygon(box);
		g.setColor(new Color(255, 196, 0));
		g.fillOval(6, 11, 4, 4);
		g.dispose();
		return img;
	}
}
