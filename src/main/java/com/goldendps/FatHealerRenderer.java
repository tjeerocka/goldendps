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
	/** How long growing fat (and shrinking back, the same in reverse) takes. */
	static final double GROW_MS = 800;
	/** Animation smoothing off (only while the fat copy is drawn with a slow frame). */
	private static final java.util.function.IntPredicate NO_SMOOTHING = id -> false;
	private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(FatHealerRenderer.class);

	private final Client client;
	private Player target;
	private double width;
	private double height;
	/** 0 = normal size, 1 = full fat. Grows and shrinks over GROW_MS. */
	private double progress;
	private long lastMs;
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
	/** Per vertex: highest layer of its faces (reused array). */
	private int[] vertexLayer = new int[0];
	/** Per vertex: only used by small triangles (see pushLayers). */
	private byte[] vertexDetail = new byte[0];
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
		update(player, widthPercent, heightPercent, System.nanoTime() / 1_000_000L);
	}

	void update(Player player, int widthPercent, int heightPercent, long nowMs)
	{
		double dt = lastMs == 0 ? 0 : Math.max(0, Math.min(100, nowMs - lastMs));
		lastMs = nowMs;
		double step = dt / GROW_MS;

		if (target != null && player != target)
		{
			// stop (or someone else heals more): first shrink back, the reverse of growing
			progress = FatHealer.approach(progress, 0, step);
			if (progress <= 0)
			{
				undoIfStillScaled();
				target = player;
				slowFrame = -1;
			}
		}
		else if (target == null)
		{
			// new fat healer: start at normal size
			target = player;
			progress = 0;
		}
		else
		{
			progress = FatHealer.approach(progress, 1, step);
		}
		// same curve both ways, and width and height finish together
		double eased = progress * progress * (3 - 2 * progress);
		width = eased * widthPercent;
		height = eased * heightPercent;

		boolean show = target != null && progress > 0;
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
		progress = 0;
		width = 0;
		height = 0;
		if (client.isRuneLiteObjectRegistered(this))
		{
			client.removeRuneLiteObject(this);
		}
	}

	/** 0 = normal, 1 = full fat (for tests). */
	double getProgress()
	{
		return progress;
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
		java.util.function.IntPredicate smoothing = null;
		if (slowFrame >= 0)
		{
			// draw the copy with the slow frame, then give the real player its own frame back.
			// Animation smoothing blends frames with the REAL timing, which makes a slow frame
			// jump: switch it off for this one draw only.
			real = p.getPoseAnimationFrame();
			smoothing = client.getAnimationInterpolationFilter();
			client.setAnimationInterpolationFilter(NO_SMOOTHING);
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
				client.setAnimationInterpolationFilter(smoothing);
			}
		}
		if (m == null)
		{
			return null;
		}
		return scaleModel(m);
	}

	/** Make this model as big as it should be now (not twice), and remember it. */
	private Model scaleModel(Model m)
	{
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
				scaleVertices(m, (float) sx / lastSx, (float) sy / lastSy);
			}
		}
		else
		{
			scaleVertices(m, sx / (float) FatHealer.UNIT, sy / (float) FatHealer.UNIT);
			pushLayers(m);
		}
		// The game sorts the faces of a model by depth using its size (bounds). The bounds were
		// worked out for the normal size, so faces of the bigger model were sorted wrong: the cape
		// showed through the body and the cape logo (drawn just in front of the cape) went missing.
		m.calculateBoundsCylinder();
		remember(m, vx, vy, sx, sy);
		return m;
	}

	/**
	 * Make the model wider (x and z) and taller (y), with exact decimal numbers.
	 * The game's own Model.scale() works in whole numbers and cuts off the fraction: every point
	 * moved a little toward the middle, so the cape sank into the body and the cape logo (a hair
	 * in front of the cape) fell behind it.
	 */
	static void scaleVertices(Model m, float wide, float tall)
	{
		float[] vx = m.getVerticesX();
		float[] vy = m.getVerticesY();
		float[] vz = m.getVerticesZ();
		int n = m.getVerticesCount();
		if (vx == null || vy == null || vz == null)
		{
			return;
		}
		n = Math.min(n, Math.min(vx.length, Math.min(vy.length, vz.length)));
		for (int i = 0; i < n; i++)
		{
			vx[i] *= wide;
			vy[i] *= tall;
			vz[i] *= wide;
		}
	}

	/** Space between two layers, in model units (1/128 tile). */
	static final float LAYER_STEP = 3f;
	/** The top layer is never pushed out further than this. */
	static final float MAX_PUSH = 12f;
	/**
	 * Logos and symbols are many small triangles lying flat on the big triangles of a cape: points
	 * that only belong to small triangles are lifted this much more, so they are really in front.
	 */
	static final float DETAIL_PUSH = 3f;
	/** "Small" triangle: twice its area below this (in model units squared). */
	static final float SMALL_FACE_AREA2 = 160f;

	/**
	 * Real players are drawn layer by layer (body, then cape, then the cape logo) without a depth test;
	 * the game's player models need that, because the logo lies exactly on the cape and the cape dips
	 * into the body a little. 117 HD and the GPU plugin draw our copy WITH a depth test, so the logo got
	 * lost and the body showed through the cape. Fix: move every point a tiny bit outward (away from
	 * the middle of the body) for each layer above the lowest, so higher layers are really in front.
	 */
	void pushLayers(Model m)
	{
		byte[] pri = m.getFaceRenderPriorities();
		int[] f1 = m.getFaceIndices1();
		int[] f2 = m.getFaceIndices2();
		int[] f3 = m.getFaceIndices3();
		float[] vx = m.getVerticesX();
		float[] vy = m.getVerticesY();
		float[] vz = m.getVerticesZ();
		if (f1 == null || f2 == null || f3 == null || vx == null || vy == null || vz == null)
		{
			return;
		}
		int faces = Math.min(m.getFaceCount(), Math.min(f1.length, Math.min(f2.length, f3.length)));
		if (pri != null)
		{
			faces = Math.min(faces, pri.length);
		}
		int verts = Math.min(m.getVerticesCount(), Math.min(vx.length, Math.min(vy.length, vz.length)));

		// layers that are used, numbered 0, 1, 2, ... from the lowest
		int[] rank = new int[256];
		int layers = 0;
		if (pri != null)
		{
			boolean[] used = new boolean[256];
			for (int f = 0; f < faces; f++)
			{
				used[pri[f] & 0xff] = true;
			}
			for (int p = 0; p < 256; p++)
			{
				if (used[p])
				{
					rank[p] = layers++;
				}
			}
		}
		float step = layers > 1 ? Math.min(LAYER_STEP, MAX_PUSH / (layers - 1)) : 0;

		if (vertexLayer.length < verts)
		{
			vertexLayer = new int[verts];
			vertexDetail = new byte[verts];
		}
		java.util.Arrays.fill(vertexLayer, 0, verts, 0);
		// detail: 0 = not used yet, 1 = only used by small triangles (logo, symbol), 2 = used by a big one
		java.util.Arrays.fill(vertexDetail, 0, verts, (byte) 0);
		for (int f = 0; f < faces; f++)
		{
			int a = f1[f];
			int b = f2[f];
			int c = f3[f];
			if (a < 0 || b < 0 || c < 0 || a >= verts || b >= verts || c >= verts)
			{
				continue;
			}
			int r = pri == null ? 0 : rank[pri[f] & 0xff];
			setLayer(a, r);
			setLayer(b, r);
			setLayer(c, r);

			boolean small = area2(vx, vy, vz, a, b, c) < SMALL_FACE_AREA2;
			setDetail(a, small);
			setDetail(b, small);
			setDetail(c, small);
		}
		for (int v = 0; v < verts; v++)
		{
			float out = vertexLayer[v] * step + (vertexDetail[v] == 1 ? DETAIL_PUSH : 0);
			if (out <= 0)
			{
				continue;
			}
			float x = vx[v];
			float z = vz[v];
			float d = (float) Math.sqrt(x * x + z * z);
			if (d < 1f)
			{
				continue;
			}
			float push = out / d;
			vx[v] = x + x * push;
			vz[v] = z + z * push;
		}
	}

	/** Twice the area of a triangle (length of the cross product). */
	private static float area2(float[] vx, float[] vy, float[] vz, int a, int b, int c)
	{
		float ux = vx[b] - vx[a];
		float uy = vy[b] - vy[a];
		float uz = vz[b] - vz[a];
		float wx = vx[c] - vx[a];
		float wy = vy[c] - vy[a];
		float wz = vz[c] - vz[a];
		float cx = uy * wz - uz * wy;
		float cy = uz * wx - ux * wz;
		float cz = ux * wy - uy * wx;
		return (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
	}

	private void setDetail(int v, boolean small)
	{
		if (!small)
		{
			vertexDetail[v] = 2;
		}
		else if (vertexDetail[v] == 0)
		{
			vertexDetail[v] = 1;
		}
	}

	private void setLayer(int v, int r)
	{
		if (r > vertexLayer[v])
		{
			vertexLayer[v] = r;
		}
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
			scaleVertices(m, (float) FatHealer.UNIT / lastSx, (float) FatHealer.UNIT / lastSy);
			m.calculateBoundsCylinder();
		}
		lastModel = null;
		lastVx = null;
		lastVy = null;
		lastSx = FatHealer.UNIT;
		lastSy = FatHealer.UNIT;
	}
}
