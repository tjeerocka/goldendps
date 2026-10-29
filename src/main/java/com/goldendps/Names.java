package com.goldendps;

import net.runelite.client.util.Text;

final class Names
{
	private Names()
	{
	}

	/** Player name without tags and non-breaking spaces, or null when it is unknown. */
	static String clean(String name)
	{
		if (name == null)
		{
			return null;
		}
		String n = Text.removeTags(name).replace(' ', ' ').trim();
		return n.isEmpty() || n.equals("<unknown>") ? null : n;
	}
}
