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
	/**
	 * The last model we made bigger. The game sometimes gives the same animated model again
	 * for a few frames; then it is still bigger from us and must not be scaled twice.
	 */
	private Model lastModel;
	private float[] lastVx;
	private float[] lastVy;
	private int markX;
	private int markY;
	private float markXValue;
	private float markYValue;
	private int lastSx = FatHealer.UNIT;
	private int lastSy = FatHealer.UNIT;
	/** Slow walk: the frame to draw the copy with, or -1 for the real frame. */
	private int slowFrame = -1;
	/** For the status line: how often the copy was drawn / the real one hidden. */
	private int frames;
	private int draws;
	private int hides;
	private int lastDraws = -1;
	private int lastHides = -1;
	private String lastStatus = "";

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
			undoIfStillScaled();
			target = player;
			slowFrame = -1;
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

		// status line: count draws over about one second (50 frames)
		if (++frames >= 50)
		{
			lastDraws = draws;
			lastHides = hides;
			frames = 0;
			draws = 0;
			hides = 0;
			String st = status();
			if (!st.equals(lastStatus))
			{
				log.info("GoldenDPS fat healer: {} (draws/s {}, hides/s {}, registered {})", st, lastDraws, lastHides,
					client.isRuneLiteObjectRegistered(this));
				lastStatus = st;
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
		undoIfStillScaled();
		target = null;
		slowFrame = -1;
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
		boolean hide = !ui && renderable instanceof Player && isFat((Player) renderable);
		if (hide)
		{
			hides++;
		}
		return !hide;
	}

	/** Short text for the panel: what the fat healer is doing right now. */
	String status()
	{
		if (target == null)
		{
			return "nobody is fat";
		}
		if (!client.isRuneLiteObjectRegistered(this))
		{
			return "copy not added";
		}
		if (lastDraws == 0)
		{
			return "added, but not drawn";
		}
		if (lastDraws < 0)
		{
			return "starting...";
		}
		return "drawn +" + Math.round(width) + "%/+" + Math.round(height) + "%"
			+ (lastHides > 0 ? ", real hidden" : ", real NOT hidden");
	}

	// ---- RuneLiteObjectController: the fat copy ----

	/** Slow walk frame for the copy (-1 = the real frame). */
	void setSlowFrame(int frame)
	{
		slowFrame = frame;
	}

	@Override
	public Model getModel()
	{
		Player p = target;
		if (p == null)
		{
			return null;
		}
		draws++;
		Model m;
		int real = -1;
		if (slowFrame >= 0)
		{
			// draw the copy with the slow frame, then give the real player its own frame back
			real = p.getPoseAnimationFrame();
			p.setPoseAnimationFrame(slowFrame);
		}
		try
		{
			m = p.getModel();
		}
		finally
		{
			if (real >= 0)
			{
				p.setPoseAnimationFrame(real);
			}
		}
		if (m == null)
		{
			return null;
		}
		float[] vx = m.getVerticesX();
		float[] vy = m.getVerticesY();
		if (vx == null || vy == null || vx.length == 0 || vy.length != vx.length)
		{
			return m;
		}
		int sx = FatHealer.scale(width);
		int sy = FatHealer.scale(height);
		if (isStillScaled(m))
		{
			// same model as last frame, already bigger: only fix the difference
			if (sx != lastSx || sy != lastSy)
			{
				m.scale(sx * FatHealer.UNIT / lastSx, sy * FatHealer.UNIT / lastSy, sx * FatHealer.UNIT / lastSx);
			}
		}
		else
		{
			m.scale(sx, sy, sx);
		}
		remember(m, vx, vy, sx, sy);
		return m;
	}

	/** Is this the model we scaled last time, and is it still scaled (not refilled by the game)? */
	private boolean isStillScaled(Model m)
	{
		if (m != lastModel || (lastSx == FatHealer.UNIT && lastSy == FatHealer.UNIT))
		{
			return false;
		}
		float[] vx = m.getVerticesX();
		float[] vy = m.getVerticesY();
		if (vx != lastVx || vy != lastVy || markX >= vx.length || markY >= vy.length)
		{
			return false;
		}
		return (lastSx == FatHealer.UNIT || vx[markX] == markXValue)
			&& (lastSy == FatHealer.UNIT || vy[markY] == markYValue);
	}

	/** Remember the vertices furthest from the middle: they always change when the game refills the model. */
	private void remember(Model m, float[] vx, float[] vy, int sx, int sy)
	{
		int ix = 0;
		int iy = 0;
		for (int i = 1; i < vx.length; i++)
		{
			if (Math.abs(vx[i]) > Math.abs(vx[ix]))
			{
				ix = i;
			}
			if (Math.abs(vy[i]) > Math.abs(vy[iy]))
			{
				iy = i;
			}
		}
		lastModel = m;
		lastVx = vx;
		lastVy = vy;
		markX = ix;
		markY = iy;
		markXValue = vx[ix];
		markYValue = vy[iy];
		lastSx = sx;
		lastSy = sy;
	}

	/** When the effect ends: if the game still holds our bigger model, make it normal again. */
	private void undoIfStillScaled()
	{
		Model m = lastModel;
		if (m != null && isStillScaled(m))
		{
			m.scale(FatHealer.UNIT * FatHealer.UNIT / lastSx, FatHealer.UNIT * FatHealer.UNIT / lastSy,
				FatHealer.UNIT * FatHealer.UNIT / lastSx);
		}
		lastModel = null;
		lastVx = null;
		lastVy = null;
		lastSx = FatHealer.UNIT;
		lastSy = FatHealer.UNIT;
	}
}
