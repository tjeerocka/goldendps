package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.Random;
import org.junit.Test;

public class FunModelTest
{
	@Test
	public void hungryTalkShowsALineThenPauses()
	{
		HungryTalk talk = new HungryTalk(new Random(1));
		String first = talk.line(0);
		assertNotNull(first);
		assertEquals(first, talk.line(HungryTalk.SHOW_MS - 1));
		assertNull(talk.line(HungryTalk.SHOW_MS + 1));
		String next = talk.line(HungryTalk.SHOW_MS + HungryTalk.MAX_PAUSE_MS + 1);
		assertNotNull(next);
		assertNotEquals("never the same line twice in a row", first, next);
	}

	@Test
	public void topPlayersOfAFight()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Kurt", PlayerKind.LOCAL, "1:1", 1, "Vorkath", true, 50, false);
		t.playerHit(1000, "Zezima", PlayerKind.PARTY, "1:1", 1, "Vorkath", true, 30, false);
		t.playerHit(2000, "Zezima", PlayerKind.PARTY, "1:1", 1, "Vorkath", true, 40, false);
		t.heal(2000, "Zezima", PlayerKind.PARTY, HealType.FOOD, 20);
		FightReport r = FightReport.of(t.getCurrent(), false, false, 3000);
		assertEquals(2, r.playerCount());
		assertEquals("Zezima", r.topDamage().name);
		assertEquals("Kurt", r.topHit().name);
		assertEquals("Zezima", r.topHealer().name);
	}
}
