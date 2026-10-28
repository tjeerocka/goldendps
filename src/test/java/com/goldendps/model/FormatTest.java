package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class FormatTest
{
	@Test
	public void amounts()
	{
		assertEquals("950", Format.amount(950));
		assertEquals("9,999", Format.amount(9999));
		assertEquals("12.3k", Format.amount(12_345));
		assertEquals("1.25m", Format.amount(1_250_000));
	}

	@Test
	public void durations()
	{
		assertEquals("0:42", Format.duration(42_000));
		assertEquals("12:05", Format.duration(725_000));
		assertEquals("1:02:03", Format.duration(3_723_000));
	}
}
