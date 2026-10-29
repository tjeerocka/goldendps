package com.goldendps;

import static org.junit.Assert.assertEquals;
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
	/** A fake model: scale() really changes the vertices (x and y), and records the call. */
	private static Model fakeModel(float[] vx, float[] vy, List<int[]> scaleCalls)
	{
		return (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[]{Model.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getVerticesX":
					return vx;
				case "getVerticesY":
					return vy;
				case "scale":
					for (int i = 0; i < vx.length; i++)
					{
						vx[i] = vx[i] * (int) a[0] / 128f;
						vy[i] = vy[i] * (int) a[1] / 128f;
					}
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
		assertEquals(2, calls.size());
		assertEquals(173, calls.get(1)[0]);
		assertEquals(136, calls.get(1)[1]);
		assertEquals(-20f * 173 / 128f, m.getVerticesX()[2], 0.001);
		assertEquals(-200f * 136 / 128f, m.getVerticesY()[2], 0.001);
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
		assertEquals("scaled once, then kept", 1, calls.size());
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
		calls.clear();
		r.getModel();
		assertEquals("still drawing A", 1, calls.size());
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
	public void nobodyFatDrawsNothing()
	{
		FatHealerRenderer r = new FatHealerRenderer(fakeClient());
		r.update(null, 35, 6);
		assertNull(r.getModel());
	}
}
