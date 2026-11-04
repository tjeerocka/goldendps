package com.goldendps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ChatNotifierTest
{
	@Test
	public void onlyInfoMessagesGetTheNewColor()
	{
		assertTrue(ChatNotifier.isInfo("<col=00bed2>GoldenDPS: you joined party \"hey\".</col>"));
		assertFalse(ChatNotifier.isInfo("<col=c27c00>GoldenDPS: Kurt left the party.</col>"));
		assertFalse(ChatNotifier.isInfo("<col=d42020>GoldenDPS: you were removed from the party.</col>"));
		assertFalse(ChatNotifier.isInfo("<col=00bed2>Another plugin: hello</col>"));
		assertFalse(ChatNotifier.isInfo(null));
	}
}
