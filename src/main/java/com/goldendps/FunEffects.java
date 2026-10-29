package com.goldendps;

import com.goldendps.model.FightReport;
import com.goldendps.model.Format;
import com.goldendps.model.HungryTalk;
import com.goldendps.model.MaxHits;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * The fun extras for group fights: a crown over the MVP, a burger over the top healer of the
 * whole fight (or raid), fries over the top healer on the current boss, hungry lines from both,
 * and a popup for new max hits. Updated on the client thread, read by the overlays.
 */
class FunEffects
{
	/** Icons stay this long after the fight ended. */
	private static final long SHOW_AFTER_FIGHT_MS = 30_000;
	private static final long POPUP_MS = 3000;
	private static final long TEST_MS = 15_000;
	private static final long SAY_MS = 4000;

	/** Something a player "says" over their head for a while (drawn by the overlay only). */
	private static final class Speech
	{
		final String text;
		final long until;
		final boolean maxHit;

		Speech(String text, long until, boolean maxHit)
		{
			this.text = text;
			this.until = until;
			this.maxHit = maxHit;
		}
	}

	private final MaxHits maxHits = new MaxHits();
	private final HungryTalk burgerTalk = new HungryTalk(new Random());
	private final HungryTalk friesTalk = new HungryTalk(new Random());

	private String mvp;
	private String burger;
	private String fries;
	private String popup;
	private long popupUntil;
	private String testPlayer;
	private long testCrownUntil;
	private long testSnackUntil;
	private final Map<String, Speech> speech = new HashMap<>();
	private String lastBossFightId;

	/**
	 * @param fight  the running or last fight (the whole raid inside a raid), or null
	 * @param boss   the current or last boss fight, or null
	 * @param bossId id of that boss fight, to announce its healer once
	 */
	void update(FightReport fight, long fightEndedMs, FightReport boss, long bossEndedMs, String bossId, long now)
	{
		FightReport shownFight = visible(fight, fightEndedMs, now) ? fight : null;
		FightReport shownBoss = visible(boss, bossEndedMs, now) ? boss : null;
		mvp = name(shownFight == null ? null : shownFight.topDamage());
		burger = name(shownFight == null ? null : shownFight.topHealer());
		FightReport.PlayerRow bossHealer = shownBoss == null ? null : shownBoss.topHealer();
		fries = name(bossHealer);
		if (bossHealer != null && bossEndedMs > 0 && !bossId.equals(lastBossFightId))
		{
			lastBossFightId = bossId;
			say(bossHealer.name, healedLine(bossHealer.healing), now, false);
		}
		speech.values().removeIf(s -> now >= s.until);
	}

	private static String healedLine(long healed)
	{
		return "I healed " + Format.amount(healed) + " HP!";
	}

	private void say(String player, String text, long now, boolean maxHit)
	{
		speech.put(player, new Speech(text, now + SAY_MS, maxHit));
	}

	private static boolean visible(FightReport report, long endedMs, long now)
	{
		return report != null && report.playerCount() >= 2 && (endedMs == 0 || now - endedMs < SHOW_AFTER_FIGHT_MS);
	}

	private static String name(FightReport.PlayerRow row)
	{
		return row == null ? null : row.name;
	}

	/** A hit landed; returns the new max hit to announce, or null. */
	MaxHits.NewMax hit(String player, int amount, long now)
	{
		MaxHits.NewMax max = maxHits.hit(player, amount);
		if (max != null)
		{
			showPopup(max.player + " - NEW MAX HIT! " + max.amount, now);
			say(max.player, "NEW MAX HIT! " + max.amount, now, true);
		}
		return max;
	}

	void resetSession()
	{
		maxHits.reset();
	}

	boolean hasCrown(String player, long now)
	{
		return player.equals(now < testCrownUntil ? testPlayer : mvp);
	}

	boolean hasBurger(String player, long now)
	{
		return player.equals(now < testSnackUntil ? testPlayer : burger);
	}

	boolean hasFries(String player, long now)
	{
		return player.equals(now < testSnackUntil ? testPlayer : fries);
	}

	boolean anyone(long now)
	{
		return now < testCrownUntil || now < testSnackUntil || mvp != null || burger != null || fries != null
			|| !speech.isEmpty();
	}

	/**
	 * What the player says now: a new max hit or the boss heal first, otherwise a hungry line
	 * while they hold a snack. Null when they say nothing.
	 */
	String line(String player, long now, boolean snackTalk, boolean maxHitTalk)
	{
		Speech s = speech.get(player);
		if (s != null && now < s.until && (s.maxHit ? maxHitTalk : snackTalk))
		{
			return s.text;
		}
		return snackTalk ? hungryLine(player, now) : null;
	}

	private String hungryLine(String player, long now)
	{
		if (hasBurger(player, now))
		{
			return burgerTalk.line(now);
		}
		return hasFries(player, now) ? friesTalk.line(now) : null;
	}

	String popup(long now)
	{
		return now < popupUntil ? popup : null;
	}

	void testCrown(String player, long now)
	{
		testPlayer = player;
		testCrownUntil = now + TEST_MS;
	}

	void testSnacks(String player, long now)
	{
		testPlayer = player;
		testSnackUntil = now + TEST_MS;
		say(player, healedLine(1234), now, false);
	}

	void testMaxHit(String player, long now)
	{
		showPopup(player + " - NEW MAX HIT! 99", now);
		say(player, "NEW MAX HIT! 99", now, true);
	}

	private void showPopup(String text, long now)
	{
		popup = text;
		popupUntil = now + POPUP_MS;
	}
}
