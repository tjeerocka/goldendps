package com.goldendps;

import com.goldendps.model.FightReport;

/** The boss meter as the overlay draws it. */
final class BossMeter
{
	final FightReport report;
	final boolean dead;
	/** No boss hits for the pause time: the boss clock stands still. */
	final boolean paused;
	/** "NEW PB!", "PB 1:10" or null. */
	final String pb;

	BossMeter(FightReport report, boolean dead, boolean paused, String pb)
	{
		this.report = report;
		this.dead = dead;
		this.paused = paused;
		this.pb = pb;
	}
}
