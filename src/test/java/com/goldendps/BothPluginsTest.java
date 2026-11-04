package com.goldendps;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Run this main() in IntelliJ to start RuneLite with GoldenDPS and Runecraft Companion in one client.
 * Runecraft Companion comes from its own project folder next to this one (see settings.gradle).
 */
public class BothPluginsTest
{
	public static void main(String[] args) throws Exception
	{
		Class<? extends Plugin> companion;
		try
		{
			// by name, so GoldenDPS still builds when the Runecraft Companion folder is not there
			companion = Class.forName("com.runecraftcompanion.RunecraftCompanionPlugin").asSubclass(Plugin.class);
		}
		catch (ClassNotFoundException e)
		{
			System.err.println("Runecraft Companion not found. Put its project next to goldendps "
				+ "(for example IdeaProjects\\runecraft-companion), reload Gradle in IntelliJ and run again. "
				+ "Starting with GoldenDPS only.");
			ExternalPluginManager.loadBuiltin(GoldenDpsPlugin.class);
			RuneLite.main(args);
			return;
		}
		ExternalPluginManager.loadBuiltin(GoldenDpsPlugin.class, companion);
		RuneLite.main(args);
	}
}
