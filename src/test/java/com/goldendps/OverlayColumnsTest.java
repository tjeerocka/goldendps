package com.goldendps;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import com.goldendps.model.FightReport;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HealType;
import com.goldendps.model.MeterMode;
import com.goldendps.model.PlayerKind;
import org.junit.Test;

public class OverlayColumnsTest
{
	@Test
	public void everyModeHasAValueForEveryColumn()
	{
		FightTracker t = new FightTracker();
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "301:5", 1, "Rat", false, 51, false);
		t.heal(2000, "Me", PlayerKind.LOCAL, HealType.FOOD, 40, 16);
		t.damageTaken(2000, "Me", PlayerKind.LOCAL, 7);
		FightReport r = FightReport.of(t.getCurrent(), false, false, 24_000);
		for (MeterMode m : MeterMode.values())
		{
			for (boolean max : new boolean[]{false, true})
			{
				String[] labels = GoldenDpsOverlay.columnLabels(m, max);
				assertEquals(m.name(), labels.length, GoldenDpsOverlay.columnValues(r.row("Me"), m, r.total(m), max).length);
			}
		}
	}

	@Test
	public void damageColumns()
	{
		FightTracker t = new FightTracker();
		// hits every 8 s or less: the clock never pauses
		t.playerHit(1000, "Me", PlayerKind.LOCAL, "301:5", 1, "Rat", false, 20, false);
		t.playerHit(9000, "Me", PlayerKind.LOCAL, "301:5", 1, "Rat", false, 0, true);
		t.playerHit(17000, "Me", PlayerKind.LOCAL, "301:5", 1, "Rat", false, 31, false);
		FightReport r = FightReport.of(t.getCurrent(), false, false, 24_000);
		assertArrayEquals(new String[]{"Damage", "DPS", "%"}, GoldenDpsOverlay.columnLabels(MeterMode.DAMAGE, false));
		// 51 damage in 23 s = 2.2 per second, shown without "/s" because the column says DPS
		assertArrayEquals(new String[]{"51", "2.2", "100%"},
			GoldenDpsOverlay.columnValues(r.row("Me"), MeterMode.DAMAGE, r.total(MeterMode.DAMAGE), false));
		assertArrayEquals(new String[]{"Damage", "Max", "DPS", "%"}, GoldenDpsOverlay.columnLabels(MeterMode.DAMAGE, true));
		assertArrayEquals(new String[]{"51", "31", "2.2", "100%"},
			GoldenDpsOverlay.columnValues(r.row("Me"), MeterMode.DAMAGE, r.total(MeterMode.DAMAGE), true));
	}
}
