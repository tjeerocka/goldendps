package com.goldendps;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Run this main() in IntelliJ to start RuneLite with the plugin loaded. */
public class GoldenDpsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GoldenDpsPlugin.class);
		RuneLite.main(args);
	}
}
