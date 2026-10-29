package com.goldendps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import org.junit.Test;

/** The fat copy logic with fake models (no game needed). */
public class FatHealerRendererTest
{
	/** A fake model with real vertex arrays (z = x). scaleCalls records calls to the game's scale(). */
	private static Model fakeModel(float[] vx, float[] vy, List<int[]> scaleCalls)
	{
		float[] vz = vx.clone();
		return (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[]{Model.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getVerticesX":
					return vx;
				case "getVerticesY":
					return vy;
				case "getVerticesZ":
					return vz;
				case "getVerticesCount":
					return vx.length;
				case "scale":
					scaleCalls.add(new int[]{(int) a[0], (int) a[1], (int) a[2]});
					return p;
				case "hashCode":
					return System.identityHashCode(p);
				case "equals":
					return p == a[0];
				default:
					return null;
			}
		});
	}

	/** A fake player; frames records every setPoseAnimationFrame call. */
	private static Player fakePlayer(Supplier<Model> models, List<Integer> frames, int[] realFrame)
	{
		return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getModel":
					return models.get();
				case "getPoseAnimationFrame":
					return realFrame[0];
				case "setPoseAnimationFrame":
					realFrame[0] = (int) a[0];
					frames.add((int) a[0]);
					return null;
				case "hashCode":
					return System.identityHashCode(p);
				case "equals":
					return p == a[0];
				default:
					return m.getReturnType() == int.class ? 0 : null; // getLocalLocation: null = not on screen
			}
		});
	}

	private static Player fakePlayer(Supplier<Model> models)
	{
		return fakePlayer(models, new ArrayList<>(), new int[]{0});
	}

	private static Client fakeClient()
	{
		return fakeClient(new ArrayList<>());
	}

	/** filters records every setAnimationInterpolationFilter call. */
	private static Client fakeClient(List<Object> filters)
	{
		Object[] current = {null};
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getAnimationInterpolationFilter":
					return current[0];
				case "setAnimationInterpolationFilter":
					current[0] = a[0];
					filters.add(a[0]);
					return null;
				default:
					return m.getReturnType() == boolean.class ? false : null;
			}
		});
	}

	private static long clock = 1000;

	/** One frame every 20 ms. */
	private static void frames(FatHealerRenderer r, Player player, int width, int height, int n)
	{
		for (int i = 0; i < n; i++)
		{
			clock += 20;
			r.update(player, width, height, clock);
		}
	}

	private static FatHealerRenderer grown(Player player, int width, int height)
	{
		return grown(new FatHealerRenderer(fakeClient()), player, width, height);
	}

	private static FatHealerRenderer grown(FatHealerRenderer r, Player player, int width, int height)
	{
		frames(r, player, width, height, 100);
		return r;
	}

	@Test
	public void freshModelEveryFrameIsScaledEveryFrame()
	{
		List<int[]> calls = new ArrayList<>();
		// like the game while animating: a refilled model every frame
		Player p = fakePlayer(() -> fakeModel(new float[]{0f, 10f, -20f}, new float[]{0f, -100f, -200f}, calls));
		FatHealerRenderer r = grown(p, 35, 6);

		r.getModel();
		Model m = r.getModel();
		assertEquals("the game's whole-number scale() is never used", 0, calls.size());
		assertEquals(-20f * 173 / 128f, m.getVerticesX()[2], 0.001);
		assertEquals(-200f * 136 / 128f, m.getVerticesY()[2], 0.001);
		assertEquals(-20f * 173 / 128f, m.getVerticesZ()[2], 0.001);
	}

	@Test
	public void sameModelAgainIsNotScaledTwice()
	{
		List<int[]> calls = new ArrayList<>();
		float[] vx = {0f, 10f, -20f};
		float[] vy = {0f, -100f, -200f};
		Model cached = fakeModel(vx, vy, calls);
		// the game keeps the same animated model for a few frames
		Player p = fakePlayer(() -> cached);
		FatHealerRenderer r = grown(p, 35, 6);

		for (int i = 0; i < 5; i++)
		{
			assertSame(cached, r.getModel());
		}
		// scaled once, then kept (not bigger and bigger)
		assertEquals(-20f * 173 / 128f, vx[2], 0.001);
	}

	@Test
	public void changingTheSizeOnlyAppliesTheDifference()
	{
		List<int[]> calls = new ArrayList<>();
		float[] vx = {0f, 10f, -128f};
		float[] vy = {0f, -100f, -256f};
		Model cached = fakeModel(vx, vy, calls);
		Player p = fakePlayer(() -> cached);
		FatHealerRenderer r = grown(p, 35, 6);
		r.getModel();
		// settings changed: 50% wider, 10% taller
		frames(r, p, 50, 10, 1);
		r.getModel();
		assertEquals(-128f * 192 / 128f, vx[2], 1.5);
		assertEquals(-256f * 141 / 128f, vy[2], 3);
	}

	@Test
	public void stoppingMakesTheModelNormalAgain()
	{
		List<int[]> calls = new ArrayList<>();
		float[] vx = {0f, 10f, -20f};
		float[] vy = {0f, -100f, -200f};
		Model cached = fakeModel(vx, vy, calls);
		Player p = fakePlayer(() -> cached);
		FatHealerRenderer r = grown(p, 35, 6);
		r.getModel();
		r.clear();
		assertEquals(-20f, vx[2], 0.2);
		assertEquals(-200f, vy[2], 1.5);
	}

	@Test
	public void slowFrameOnlyForTheCopy()
	{
		List<Integer> frames = new ArrayList<>();
		int[] real = {7};
		Player p = fakePlayer(() -> fakeModel(new float[]{1f}, new float[]{1f}, new ArrayList<>()), frames, real);
		FatHealerRenderer r = grown(p, 35, 6);
		r.setSlowFrame(4);
		r.getModel();
		// set the slow frame for the copy, then the real frame back
		assertEquals(2, frames.size());
		assertEquals(4, (int) frames.get(0));
		assertEquals(7, (int) frames.get(1));
		assertEquals(7, real[0]);
	}

	@Test
	public void smoothingIsOffOnlyWhileTheSlowCopyIsDrawn()
	{
		List<Object> filters = new ArrayList<>();
		Player p = fakePlayer(() -> fakeModel(new float[]{1f}, new float[]{1f}, new ArrayList<>()));
		FatHealerRenderer r = grown(new FatHealerRenderer(fakeClient(filters)), p, 35, 6);
		r.getModel();
		assertEquals("no slow frame: smoothing untouched", 0, filters.size());
		r.setSlowFrame(2);
		r.getModel();
		assertEquals(2, filters.size());
		assertEquals(false, ((java.util.function.IntPredicate) filters.get(0)).test(819));
		assertNull("put back the way it was", filters.get(1));
	}

	@Test
	public void growingAndShrinkingTakeTheSameTime()
	{
		Player p = fakePlayer(() -> fakeModel(new float[]{1f}, new float[]{1f}, new ArrayList<>()));
		FatHealerRenderer r = new FatHealerRenderer(fakeClient());
		frames(r, p, 35, 6, 1);                 // first frame: start
		frames(r, p, 35, 6, 20);                // 400 ms: half way
		assertEquals(0.5, r.getProgress(), 0.01);
		frames(r, p, 35, 6, 20);                // 800 ms: full
		assertEquals(1.0, r.getProgress(), 0.0001);

		// stop: shrink back over the same 800 ms, still the same player
		frames(r, null, 35, 6, 20);
		assertEquals(0.5, r.getProgress(), 0.01);
		frames(r, null, 35, 6, 20);
		assertEquals(0.0, r.getProgress(), 0.0001);
		assertNull(r.getModel());
	}

	@Test
	public void newHealerWaitsUntilTheOldOneShrank()
	{
		List<int[]> calls = new ArrayList<>();
		Player a = fakePlayer(() -> fakeModel(new float[]{1f}, new float[]{1f}, calls));
		Player b = fakePlayer(() -> fakeModel(new float[]{2f}, new float[]{2f}, new ArrayList<>()));
		FatHealerRenderer r = grown(a, 35, 6);
		frames(r, b, 35, 6, 10);                // 200 ms: A is shrinking, B not yet
		assertTrue(r.getProgress() < 1 && r.getProgress() > 0);
		assertTrue("still drawing A", r.getModel().getVerticesX()[0] < 2f);
		frames(r, b, 35, 6, 40);                // A gone, B growing
		frames(r, b, 35, 6, 60);
		assertEquals(1.0, r.getProgress(), 0.0001);
	}

	@Test
	public void boundsAreWorkedOutAgainAfterScaling()
	{
		int[] bounds = {0};
		float[] vx = {0f, 10f, -20f};
		float[] vy = {0f, -100f, -200f};
		Model base = fakeModel(vx, vy, new ArrayList<>());
		Model counting = (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[]{Model.class}, (p, m, a) ->
		{
			if (m.getName().equals("calculateBoundsCylinder"))
			{
				bounds[0]++;
				return null;
			}
			return m.invoke(base, a);
		});
		Player p = fakePlayer(() -> counting);
		FatHealerRenderer r = grown(p, 35, 6);
		r.getModel();
		assertEquals(1, bounds[0]);
	}

	@Test
	public void fractionsAreKept()
	{
		// a cape point 3 units from the middle: 3 * 173 / 128 = 4.0546875, not 4
		float[] vx = {3f, 100f};
		float[] vy = {-1f, -200f};
		Player p = fakePlayer(() -> fakeModel(vx.clone(), vy.clone(), new ArrayList<>()));
		FatHealerRenderer r = grown(p, 35, 6);
		Model m = r.getModel();
		assertEquals(4.0546875f, m.getVerticesX()[0], 0f);
		assertEquals(-1.0625f, m.getVerticesY()[0], 0f);
	}

	/** A model from plain arrays: vertices + triangles + layer per triangle. */
	private static Model layeredModel(float[] vx, float[] vy, float[] vz, int[] f1, int[] f2, int[] f3, byte[] pri)
	{
		return (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[]{Model.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getVerticesX":
					return vx;
				case "getVerticesY":
					return vy;
				case "getVerticesZ":
					return vz;
				case "getVerticesCount":
					return vx.length;
				case "getFaceCount":
					return f1.length;
				case "getFaceIndices1":
					return f1;
				case "getFaceIndices2":
					return f2;
				case "getFaceIndices3":
					return f3;
				case "getFaceRenderPriorities":
					return pri;
				default:
					return null;
			}
		});
	}

	/** Distance of vertex v from the middle of the body (the vertical axis). */
	private static float out(float[] vx, float[] vz, int v)
	{
		return (float) Math.sqrt(vx[v] * vx[v] + vz[v] * vz[v]);
	}

	/** n big triangles (60 units wide) on the back (z = -20), one per layer, each with its own 3 points. */
	private static float[][] bigTriangles(int n)
	{
		float[] vx = new float[n * 3];
		float[] vy = new float[n * 3];
		float[] vz = new float[n * 3];
		for (int t = 0; t < n; t++)
		{
			vx[t * 3] = -30f;
			vx[t * 3 + 1] = 30f;
			vx[t * 3 + 2] = 0f;
			vy[t * 3] = -100f;
			vy[t * 3 + 1] = -100f;
			vy[t * 3 + 2] = -160f;
			vz[t * 3] = vz[t * 3 + 1] = vz[t * 3 + 2] = -20f;
		}
		return new float[][]{vx, vy, vz};
	}

	private static int[][] faces(int n)
	{
		int[] f1 = new int[n];
		int[] f2 = new int[n];
		int[] f3 = new int[n];
		for (int t = 0; t < n; t++)
		{
			f1[t] = t * 3;
			f2[t] = t * 3 + 1;
			f3[t] = t * 3 + 2;
		}
		return new int[][]{f1, f2, f3};
	}

	@Test
	public void higherLayersArePushedOutward()
	{
		// body (layer 0), cape (layer 5), cape logo (layer 9): same place before
		float[][] v = bigTriangles(3);
		int[][] f = faces(3);
		Model m = layeredModel(v[0], v[1], v[2], f[0], f[1], f[2], new byte[]{0, 5, 9});
		float before = out(v[0], v[2], 2);

		new FatHealerRenderer(fakeClient()).pushLayers(m);
		float step = FatHealerRenderer.LAYER_STEP;
		assertEquals("body stays", before, out(v[0], v[2], 2), 0.001);
		assertEquals("cape 1 layer out", before + step, out(v[0], v[2], 5), 0.001);
		assertEquals("logo 2 layers out", before + 2 * step, out(v[0], v[2], 8), 0.001);
	}

	@Test
	public void manyLayersAreNeverPushedFurtherThanTheMax()
	{
		int n = 12;
		float[][] v = bigTriangles(n);
		int[][] f = faces(n);
		byte[] pri = new byte[n];
		for (int i = 0; i < n; i++)
		{
			pri[i] = (byte) i;
		}
		float before = out(v[0], v[2], 2);
		new FatHealerRenderer(fakeClient()).pushLayers(layeredModel(v[0], v[1], v[2], f[0], f[1], f[2], pri));
		assertEquals(before + FatHealerRenderer.MAX_PUSH, out(v[0], v[2], (n - 1) * 3 + 2), 0.001);
	}

	@Test
	public void oneBigLayerChangesNothing()
	{
		float[][] v = bigTriangles(1);
		int[][] f = faces(1);
		float before = out(v[0], v[2], 2);
		new FatHealerRenderer(fakeClient()).pushLayers(layeredModel(v[0], v[1], v[2], f[0], f[1], f[2], new byte[]{4}));
		assertEquals(before, out(v[0], v[2], 2), 0f);
		// fake models without face data are left alone
		new FatHealerRenderer(fakeClient()).pushLayers(fakeModel(new float[]{1f}, new float[]{1f}, new ArrayList<>()));
	}

	@Test
	public void smallLogoTrianglesOnTheCapeAreLifted()
	{
		// a big cape triangle and a small logo triangle lying flat on it, SAME layer
		float[] vx = {-30f, 30f, 0f, -2f, 2f, 0f};
		float[] vy = {-100f, -100f, -160f, -130f, -130f, -134f};
		float[] vz = {-20f, -20f, -20f, -20f, -20f, -20f};
		Model m = layeredModel(vx, vy, vz, new int[]{0, 3}, new int[]{1, 4}, new int[]{2, 5}, new byte[]{3, 3});
		float capeBefore = out(vx, vz, 2);
		float logoBefore = out(vx, vz, 5);

		new FatHealerRenderer(fakeClient()).pushLayers(m);
		assertEquals("cape stays", capeBefore, out(vx, vz, 2), 0.001);
		assertEquals("logo lifted", logoBefore + FatHealerRenderer.DETAIL_PUSH, out(vx, vz, 5), 0.001);
	}

	@Test
	public void pointsSharedWithBigTrianglesAreNotLifted()
	{
		// small triangle that uses one point of the big triangle: that point stays
		float[] vx = {-30f, 30f, 0f, 2f, 1f};
		float[] vy = {-100f, -100f, -160f, -160f, -157f};
		float[] vz = {-20f, -20f, -20f, -20f, -20f};
		Model m = layeredModel(vx, vy, vz, new int[]{0, 2}, new int[]{1, 3}, new int[]{2, 4}, null);
		float sharedBefore = out(vx, vz, 2);
		new FatHealerRenderer(fakeClient()).pushLayers(m);
		assertEquals(sharedBefore, out(vx, vz, 2), 0.001);
		assertTrue("own small points are lifted", out(vx, vz, 4) > 20.5f);
	}

	@Test
	public void realCharacterIsOnlyHiddenWhenTheCopyWasDrawn()
	{
		// a client where the copy counts as added to the game
		Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(px, m, a) ->
			{
				if (m.getName().equals("isRuneLiteObjectRegistered"))
				{
					return Boolean.TRUE;
				}
				return m.getReturnType() == boolean.class ? (Object) Boolean.FALSE : null;
			});
		Player p = fakePlayer(() -> fakeModel(new float[]{1f}, new float[]{1f}, new ArrayList<>()));
		FatHealerRenderer r = grown(new FatHealerRenderer(client), p, 35, 6);

		// the game did not draw the copy: the real character stays visible (never invisible)
		frames(r, p, 35, 6, 1);
		assertTrue(r.addEntity(p, false));

		// the copy was drawn: from the next frame the real one is hidden
		r.getModel();
		frames(r, p, 35, 6, 1);
		assertFalse(r.addEntity(p, false));
		assertTrue("overhead text / HP bar stay", r.addEntity(p, true));

		// not drawn again: shown again
		frames(r, p, 35, 6, 1);
		assertTrue(r.addEntity(p, false));
	}

	@Test
	public void nobodyFatDrawsNothing()
	{
		FatHealerRenderer r = new FatHealerRenderer(fakeClient());
		r.update(null, 35, 6);
		assertNull(r.getModel());
	}
}
