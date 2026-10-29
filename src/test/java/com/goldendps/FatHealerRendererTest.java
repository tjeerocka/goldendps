package com.goldendps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import org.junit.Test;

/** The fat copy logic with fake models (no game needed). */
public class FatHealerRendererTest
{
	/** A fake model: scale() really changes the vertices, and records the call. */
	private static Model fakeModel(float[] vx, List<int[]> scaleCalls)
	{
		return (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[]{Model.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getVerticesX":
					return vx;
				case "scale":
					int sx = (int) a[0];
					for (int i = 0; i < vx.length; i++)
					{
						vx[i] = vx[i] * sx / 128f;
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

	private static Player fakePlayer(java.util.function.Supplier<Model> models)
	{
		return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (p, m, a) ->
		{
			switch (m.getName())
			{
				case "getModel":
					return models.get();
				case "hashCode":
					return System.identityHashCode(p);
				case "equals":
					return p == a[0];
				default:
					return null; // getLocalLocation: null = not on screen, so nothing is registered
			}
		});
	}

	private static Client fakeClient()
	{
		return (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class},
			(p, m, a) -> m.getReturnType() == boolean.class ? false : null);
	}

	private static FatHealerRenderer grown(Player player)
	{
		FatHealerRenderer r = new FatHealerRenderer(fakeClient());
		for (int i = 0; i < 100; i++)
		{
			r.update(player, 35, 6);
		}
		return r;
	}

	@Test
	public void freshModelEveryFrameIsScaledEveryFrame()
	{
		List<int[]> calls = new ArrayList<>();
		// like the game: a new animated copy of the model every frame
		Player p = fakePlayer(() -> fakeModel(new float[]{0f, 10f, -20f}, calls));
		FatHealerRenderer r = grown(p);

		Model m1 = r.getModel();
		Model m2 = r.getModel();
		assertEquals(2, calls.size());
		assertEquals(173, calls.get(1)[0]);
		assertEquals(136, calls.get(1)[1]);
		assertEquals(173, calls.get(1)[2]);
		assertEquals(-20f * 173 / 128f, m2.getVerticesX()[2], 0.001);
		assertFalse(m1 == m2);
	}

	@Test
	public void cachedModelIsRestoredAndEffectStops()
	{
		List<int[]> calls = new ArrayList<>();
		float[] vx = {0f, 10f, -20f};
		Model cached = fakeModel(vx, calls);
		// bad case: the same real model every frame
		Player p = fakePlayer(() -> cached);
		FatHealerRenderer r = grown(p);

		assertSame(cached, r.getModel());   // scaled once
		assertSame(cached, r.getModel());   // detected: undone
		assertSame(cached, r.getModel());   // stays off
		assertEquals(2, calls.size());
		// back to (almost exactly) normal size
		assertEquals(-20f, vx[2], 0.2);
		assertTrue(calls.get(1)[0] < 128);
	}

	@Test
	public void nobodyFatDrawsNothing()
	{
		FatHealerRenderer r = new FatHealerRenderer(fakeClient());
		r.update(null, 35, 6);
		assertEquals(null, r.getModel());
	}
}
