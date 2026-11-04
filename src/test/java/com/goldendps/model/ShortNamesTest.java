package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Map;
import org.junit.Test;

public class ShortNamesTest
{
	@Test
	public void twoLettersUnlessTwoPlayersWouldLookTheSame()
	{
		Map<String, String> s = ShortNames.of(Arrays.asList("Franz", "Bob", "Frank", "Bo", "Kurt Yoghurt"));
		assertEquals("Bob", s.get("Bob"));
		assertEquals("Bo", s.get("Bo"));
		assertEquals("Franz", s.get("Franz"));
		assertEquals("Frank", s.get("Frank"));
		assertEquals("Ku", s.get("Kurt Yoghurt"));
	}

	@Test
	public void aloneEveryoneGetsTwoLetters()
	{
		Map<String, String> s = ShortNames.of(Arrays.asList("Franz", "Zezima"));
		assertEquals("Fr", s.get("Franz"));
		assertEquals("Ze", s.get("Zezima"));
	}
}
