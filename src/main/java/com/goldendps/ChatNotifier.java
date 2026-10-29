package com.goldendps;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/** Local chat messages (only visible to you). */
@Singleton
class ChatNotifier
{
	private static final String PREFIX = "GoldenDPS: ";
	private static final String JOIN_COLOR = "1aa31a";
	private static final String LEAVE_COLOR = "c27c00";
	private static final String KICK_COLOR = "d42020";

	private final ChatMessageManager chatMessageManager;

	@Inject
	ChatNotifier(ChatMessageManager chatMessageManager)
	{
		this.chatMessageManager = chatMessageManager;
	}

	void info(String text)
	{
		send(PREFIX + text);
	}

	void joined(String text)
	{
		send(colored(JOIN_COLOR, PREFIX + text));
	}

	void left(String text)
	{
		send(colored(LEAVE_COLOR, PREFIX + text));
	}

	void kicked(String text)
	{
		send(colored(KICK_COLOR, PREFIX + text));
	}

	private static String colored(String hex, String text)
	{
		return "<col=" + hex + ">" + text + "</col>";
	}

	private void send(String text)
	{
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.CONSOLE)
			.runeLiteFormattedMessage(text)
			.build());
	}
}
