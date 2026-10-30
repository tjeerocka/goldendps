package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class KillCountsTest
{
	@Test
	public void readsTheGameMessages()
	{
		KillCounts k = new KillCounts();
		k.message(1000, "Your Vorkath kill count is: <col=ff0000>1,234</col>.");
		k.message(1000, "Your Barrows chest count is: <col=ff0000>19</col>.");
		k.message(1000, "Your completed Chambers of Xeric count is: <col=ff0000>21</col>.");
		k.message(1000, "Your chest is worth around 36,190 coins.");
		assertEquals(1234, k.near("Vorkath", 3000));
		assertEquals(19, k.near("Barrows", 3000));
		assertEquals(21, k.near("Chambers of Xeric", 3000));
		assertEquals(0, k.near("Zulrah", 3000));
	}

	@Test
	public void anOldCountIsNotUsed()
	{
		KillCounts k = new KillCounts();
		k.message(1000, "Your Vorkath kill count is: 5.");
		assertEquals(0, k.near("Vorkath", 1000 + KillCounts.NEAR_MS + 1));
	}
}
