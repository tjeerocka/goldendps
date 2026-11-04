package com.goldendps;

import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;

/** Local chat messages (only visible to you). */
class ChatNotifier
{
	private static final String PREFIX = "GoldenDPS: ";
	private static final String LEAVE_COLOR = "c27c00";
	private static final String KICK_COLOR = "d42020";

	private final ChatMessageManager chatMessageManager;
	private final GoldenDpsConfig config;

	@Inject
	ChatNotifier(ChatMessageManager chatMessageManager, GoldenDpsConfig config)
	{
		this.chatMessageManager = chatMessageManager;
		this.config = config;
	}

	/** Info and good news (joined, raid started) in the color the player picked; leaving and kicks keep theirs. */
	void info(String text)
	{
		send(colored(chatHex(), PREFIX + text));
	}

	void left(String text)
	{
		send(colored(LEAVE_COLOR, PREFIX + text));
	}

	void kicked(String text)
	{
		send(colored(KICK_COLOR, PREFIX + text));
	}

	private String chatHex()
	{
		return String.format("%06x", config.chatColor().getRGB() & 0xffffff);
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
