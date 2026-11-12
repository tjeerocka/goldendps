package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class GameTimesTest
{
	@Test
	public void bossTimes()
	{
		assertBoss(54_600, "Fight duration: <col=ff0000>0:54.60</col>. Personal best: 0:50.40");
		assertBoss(412_200, "Corrupted challenge duration: <col=ff0000>6:52.20</col>. Personal best: 6:00.00");
		assertBoss(124_200, "Wave 'The Maiden of Sugadinti' (Normal Mode) complete!<br>Duration: <col=ff0000>2:04.20</col>");
		assertBoss(132_600, "Challenge complete: Path of Het. Duration: <col=ef1020>2:12.60</col>");
		assertBoss(83_000, "Fight duration: <col=ff0000>1:23</col>.");
	}

	@Test
	public void raidTimes()
	{
		assertRaid(1_513_200, "<col=ef20ff>Congratulations - your raid is complete!</col><br>Team size: <col=ff0000>Solo</col> "
			+ "Duration:</col> <col=ff0000>25:13.20</col> Personal best: </col><col=ff0000>24:00.00</col>");
		assertRaid(1_200_000, "Theatre of Blood total completion time: <col=ff0000>20:00.00</col>");
		assertRaid(3_723_400, "Tombs of Amascut: Expert Mode total completion time: <col=ff0000>1:02:03.40</col>");
	}

	@Test
	public void otherTimesAreNotBossTimes()
	{
		assertNull(GameTimes.parse("Wave 3 completed! Wave duration: <col=ff0000>0:40.20</col>"));
		assertNull(GameTimes.parse("Lap duration: <col=ff0000>1:02.40</col>"));
		assertNull(GameTimes.parse("Your Vorkath kill count is: 123."));
	}

	private static void assertBoss(long ms, String message)
	{
		GameTimes.Time time = GameTimes.parse(message);
		assertEquals(ms, time.ms);
		assertFalse(time.raid);
	}

	private static void assertRaid(long ms, String message)
	{
		GameTimes.Time time = GameTimes.parse(message);
		assertEquals(ms, time.ms);
		assertTrue(time.raid);
	}
}
