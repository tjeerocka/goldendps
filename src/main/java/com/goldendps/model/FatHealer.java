package com.goldendps.model;

/**
 * Fun: the player with the most healing becomes a "fat healer" (wider and a bit taller),
 * only on your screen. This class picks who and how fat; drawing is in the plugin.
 */
public final class FatHealer
{
	/** Model scale unit: 128 = 100%. */
	public static final int UNIT = 128;

	private FatHealer()
	{
	}

	/**
	 * The player with the most healing. Only you and party members with GoldenDPS count
	 * (their healing is real data). Null when nobody healed.
	 */
	public static String pick(FightReport report)
	{
		if (report == null)
		{
			return null;
		}
		FightReport.PlayerRow best = null;
		for (FightReport.PlayerRow r : report.getRows())
		{
			if (r.kind != PlayerKind.LOCAL && r.kind != PlayerKind.PARTY)
			{
				continue;
			}
			if (r.healing > 0 && (best == null || r.healing > best.healing
				|| (r.healing == best.healing && r.name.compareTo(best.name) < 0)))
			{
				best = r;
			}
		}
		return best == null ? null : best.name;
	}

	/** Model scale for a size bonus in % (40 = 40% bigger): 128 * 1.4 = 179. */
	public static int scale(double bonusPercent)
	{
		return (int) Math.round(UNIT * (1 + Math.max(0, bonusPercent) / 100.0));
	}

	/** Move cur toward target by at most step (smooth growing / shrinking). */
	public static double approach(double cur, double target, double step)
	{
		if (cur < target)
		{
			return Math.min(target, cur + step);
		}
		return Math.max(target, cur - step);
	}
}
