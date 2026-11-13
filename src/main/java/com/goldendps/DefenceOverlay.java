package com.goldendps;

import com.goldendps.model.TargetMatcher;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

/** Draws above monsters their defence after special attacks lowered it, and your hit chance. */
class DefenceOverlay extends Overlay
{
	/** Above the health bar. */
	private static final int ABOVE_HEAD = 60;
	private static final int LINE_HEIGHT = 14;
	private static final Color DEFENCE_COLOR = new Color(255, 255, 0);

	private final Client client;
	private final GoldenDpsPlugin plugin;

	@Inject
	DefenceOverlay(Client client, GoldenDpsPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Map<String, String[]> labels = plugin.defenceLabels();
		WorldView wv = client.getTopLevelWorldView();
		if (labels.isEmpty() || wv == null)
		{
			return null;
		}
		g.setFont(FontManager.getRunescapeSmallFont());
		for (NPC npc : wv.npcs())
		{
			String[] lines = npc == null ? null : labels.get(TargetMatcher.key(client.getWorld(), npc.getIndex()));
			if (lines != null)
			{
				draw(g, npc, lines);
			}
		}
		return null;
	}

	/** The first line in yellow, the others in white below it. */
	private void draw(Graphics2D g, NPC npc, String[] lines)
	{
		int height = npc.getLogicalHeight() + ABOVE_HEAD;
		Point top = npc.getCanvasTextLocation(g, lines[0], height);
		if (top == null)
		{
			return;
		}
		for (int i = 0; i < lines.length; i++)
		{
			Point centered = npc.getCanvasTextLocation(g, lines[i], height);
			if (centered != null)
			{
				Point at = new Point(centered.getX(), top.getY() + i * LINE_HEIGHT);
				OverlayUtil.renderTextLocation(g, at, lines[i], i == 0 ? DEFENCE_COLOR : Color.WHITE);
			}
		}
	}
}
