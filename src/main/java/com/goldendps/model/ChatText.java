package com.goldendps.model;

/** Text from the game chat and from names, which can hold color and line tags. */
public final class ChatText
{
	private ChatText()
	{
	}

	/** "Fight duration: <col=ff0000>0:54.60</col>" to "Fight duration: 0:54.60" */
	public static String withoutTags(String text)
	{
		return text.replaceAll("<[^>]*>", "");
	}
}
