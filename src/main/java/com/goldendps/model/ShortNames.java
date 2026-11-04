package com.goldendps.model;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Short names for the compact meter: two letters, more where two players would look the same. */
public final class ShortNames
{
	private static final int LETTERS = 2;

	private ShortNames()
	{
	}

	/** "Franz" and "Bob" give "Fr" and "Bo"; "Franz" and "Frank" grow until they differ: "Franz", "Frank". */
	public static Map<String, String> of(Collection<String> names)
	{
		Map<String, Integer> length = new HashMap<>();
		names.forEach(n -> length.put(n, LETTERS));
		boolean grew = true;
		while (grew)
		{
			grew = false;
			Map<String, List<String>> byShort = names.stream().distinct()
				.collect(Collectors.groupingBy(n -> cut(n, length.get(n))));
			for (List<String> same : byShort.values())
			{
				if (same.size() < 2)
				{
					continue;
				}
				for (String n : same)
				{
					if (length.get(n) < n.length())
					{
						length.put(n, length.get(n) + 1);
						grew = true;
					}
				}
			}
		}
		Map<String, String> out = new HashMap<>();
		names.forEach(n -> out.put(n, cut(n, length.get(n))));
		return out;
	}

	private static String cut(String name, int letters)
	{
		return name.substring(0, Math.min(letters, name.length()));
	}
}
