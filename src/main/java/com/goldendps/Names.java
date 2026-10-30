package com.goldendps;

import net.runelite.api.Client;
import net.runelite.api.Player;
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

	/** Your own name, or "You" before the game knows it. Client thread only. */
	static String localName(Client client)
	{
		Player me = client.getLocalPlayer();
		String name = me == null ? null : clean(me.getName());
		return name == null ? "You" : name;
	}
}
