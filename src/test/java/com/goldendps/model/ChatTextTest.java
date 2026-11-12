package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class ChatTextTest
{
	@Test
	public void tagsAreRemoved()
	{
		assertEquals("Fight duration: 0:54.60.", ChatText.withoutTags("Fight duration: <col=ff0000>0:54.60</col>."));
		assertEquals("complete!Duration: 2:04.20", ChatText.withoutTags("complete!<br>Duration: 2:04.20"));
	}
}
