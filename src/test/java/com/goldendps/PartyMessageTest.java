package com.goldendps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import org.junit.Test;

public class PartyMessageTest
{
	@Test
	public void messageSurvivesJson()
	{
		GoldenDpsUpdate u = new GoldenDpsUpdate();
		assertTrue(u.isEmpty());
		u.w = 302;
		u.n = "Zezima";
		u.h.add(new GoldenDpsUpdate.Hit(1234, 8061, "Vorkath", 47, false, true, new net.runelite.api.coords.WorldPoint(2272, 4065, 0)));
		u.h.add(new GoldenDpsUpdate.Hit(1234, 8061, "Vorkath", 0, true, true, null));
		u.food = 20;
		u.setMemberId(99);
		assertFalse(u.isEmpty());

		Gson gson = new Gson();
		String json = gson.toJson(u);
		assertFalse("member id is set by the party server, not sent", json.contains("memberId"));

		GoldenDpsUpdate back = gson.fromJson(json, GoldenDpsUpdate.class);
		assertEquals(302, back.w);
		assertEquals("Zezima", back.n);
		assertEquals(2, back.h.size());
		assertEquals(47, back.h.get(0).a);
		assertTrue(back.h.get(1).miss);
		assertTrue(back.h.get(0).boss);
		assertEquals(20, back.food);
		assertEquals(2272, back.h.get(0).x);
		assertEquals(4065, back.h.get(0).y);
	}

	@Test
	public void targetKeyUsesWorldAndIndex()
	{
		assertEquals("302:1234", com.goldendps.model.TargetMatcher.key(302, 1234));
	}

	@Test
	public void bossesByIdAndName()
	{
		Bosses b = new Bosses();
		assertTrue(b.isBoss(net.runelite.api.gameval.NpcID.VORKATH, "Vorkath"));
		assertTrue(b.isBoss(-1, "Duke Sucellus"));
		assertFalse(b.isBoss(-1, "Goblin"));
		b.setExtraNames(" Goblin , ,Hill Giant");
		assertTrue(b.isBoss(-1, "goblin"));
		assertTrue(b.isBoss(-1, "Hill giant"));
		assertFalse(b.isBoss(-1, null));
	}
}
