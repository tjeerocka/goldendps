package com.goldendps;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;
import net.runelite.client.plugins.Plugin;

/**
 * Run this main() in IntelliJ to start RuneLite with GoldenDPS and AoRC in one client.
 * AoRC comes from its own project folder next to this one (see settings.gradle).
 */
public class BothPluginsTest
{
	public static void main(String[] args) throws Exception
	{
		Class<? extends Plugin> aorc;
		try
		{
			// by name, so GoldenDPS still builds when the AoRC folder is not there
			aorc = Class.forName("com.aorc.AorcPlugin").asSubclass(Plugin.class);
		}
		catch (ClassNotFoundException e)
		{
			System.err.println("AoRC not found. Put the AoRC project next to goldendps "
				+ "(for example IdeaProjects\\AoRC), reload Gradle in IntelliJ and run again. "
				+ "Starting with GoldenDPS only.");
			ExternalPluginManager.loadBuiltin(GoldenDpsPlugin.class);
			RuneLite.main(args);
			return;
		}
		ExternalPluginManager.loadBuiltin(GoldenDpsPlugin.class, aorc);
		RuneLite.main(args);
	}
}
