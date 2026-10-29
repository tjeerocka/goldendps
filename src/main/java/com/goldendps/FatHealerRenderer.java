package com.goldendps;

import com.goldendps.model.FatHealer;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.RuneLiteObjectController;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.callback.RenderCallback;

/**
 * Draws the "fat healer": the real character is hidden (only on your screen) and a copy
 * of its model is drawn in the same place, wider and a bit taller. The copy is made
 * at draw time from the player's current model, so walking / attacking / eating
 * animations stay the same. Client thread only.
 */
class FatHealerRenderer extends RuneLiteObjectController implements RenderCallback
{
	/** How fast the size changes per frame (in %), so the healer "grows". */
	private static final double GROW_STEP = 1.5;
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FatHealerRenderer.class);

	private final Client client;
	private Player target;
	private double width;
	private double height;
	private double targetWidth;
	private double targetHeight;
	/** Safety check data (see getModel). */
	private Model lastModel;
	private float[] lastVx;
	private float lastMark;
	private int lastMarkIdx;
	private int lastSx;
	private int lastSy;
	private boolean unsafe;

	FatHealerRenderer(Client client)
	{
		this.client = client;
	}

	/** Who should be fat (null = nobody) and how fat, in % bigger. Call every frame. */
	void update(Player player, int widthPercent, int heightPercent)
	{
		if (player != target)
		{
			// new healer: start at normal size and grow
			target = player;
			width = 0;
			height = 0;
		}
		targetWidth = player == null ? 0 : widthPercent;
		targetHeight = player == null ? 0 : heightPercent;
		width = FatHealer.approach(width, targetWidth, GROW_STEP);
		height = FatHealer.approach(height, targetHeight, GROW_STEP / 4);

		boolean show = target != null && (width > 0 || height > 0);
		if (show)
		{
			LocalPoint lp = target.getLocalLocation();
			if (lp == null)
			{
				show = false;
			}
			else
			{
				setLocation(lp, target.getWorldView().getPlane());
				setZ(net.runelite.api.Perspective.getTileHeight(client, lp, target.getWorldView().getPlane()));
				setOrientation(target.getCurrentOrientation());
			}
		}

		boolean registered = client.isRuneLiteObjectRegistered(this);
		if (show && !registered)
		{
			client.registerRuneLiteObject(this);
		}
		else if (!show && registered)
		{
			client.removeRuneLiteObject(this);
		}
	}

	void clear()
	{
		target = null;
		lastModel = null;
		lastVx = null;
		width = 0;
		height = 0;
		if (client.isRuneLiteObjectRegistered(this))
		{
			client.removeRuneLiteObject(this);
		}
	}

	/** The player that looks fat right now, or null. */
	Player getFatPlayer()
	{
		return target != null && client.isRuneLiteObjectRegistered(this) ? target : null;
	}

	/** True while a fat copy is drawn for this player (so the real one is hidden). */
	private boolean isFat(Player p)
	{
		return p != null && p == target && client.isRuneLiteObjectRegistered(this);
	}

	// ---- RenderCallback: hide the real character while the fat copy is drawn ----

	@Override
	public boolean addEntity(Renderable renderable, boolean ui)
	{
		// ui = overhead text, HP bars, hitsplats: keep those
		return ui || !(renderable instanceof Player) || !isFat((Player) renderable);
	}

	// ---- RuneLiteObjectController: the fat copy ----

	@Override
	public Model getModel()
	{
		Player p = target;
		if (p == null)
		{
			return null;
		}
		Model m = p.getModel();
		if (m == null || unsafe)
		{
			return m;
		}
		float[] vx = m.getVerticesX();
		if (vx == null || vx.length == 0)
		{
			return m;
		}
		// Safety: players are animated every frame, so the client gives a fresh copy of the
		// model each time. If we get the same copy back still scaled from last frame, it is
		// the real cached model: undo once and stop, so nobody stays fat by mistake.
		if (m == lastModel && vx == lastVx && lastSx != FatHealer.UNIT && lastMarkIdx < vx.length
			&& lastMark != 0 && vx[lastMarkIdx] == lastMark)
		{
			m.scale(FatHealer.UNIT * FatHealer.UNIT / lastSx, FatHealer.UNIT * FatHealer.UNIT / lastSy,
				FatHealer.UNIT * FatHealer.UNIT / lastSx);
			unsafe = true;
			log.warn("GoldenDPS: fat healer turned off (player model is not a per-frame copy)");
			return m;
		}
		int sx = FatHealer.scale(width);
		int sy = FatHealer.scale(height);
		m.scale(sx, sy, sx);
		// remember the vertex furthest from the middle (it always changes when scaled)
		int idx = 0;
		for (int i = 1; i < vx.length; i++)
		{
			if (Math.abs(vx[i]) > Math.abs(vx[idx]))
			{
				idx = i;
			}
		}
		lastModel = m;
		lastVx = vx;
		lastMarkIdx = idx;
		lastMark = vx[idx];
		lastSx = sx;
		lastSy = sy;
		return m;
	}
}
