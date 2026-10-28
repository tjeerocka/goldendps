package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Before;
import org.junit.Test;

public class HitAttributorTest
{
	private static final String BOSS = "301:10";
	private static final String RAT = "301:20";
	private HitAttributor a;

	@Before
	public void setUp()
	{
		a = new HitAttributor();
	}

	@Test
	public void meleeHitGoesToTheAttacker()
	{
		a.attack(100, "Bob", BOSS);
		assertEquals("Bob", a.assign(101, BOSS));
	}

	@Test
	public void oneAttackGivesOneHit()
	{
		a.attack(100, "Bob", BOSS);
		assertEquals("Bob", a.assign(101, BOSS));
		assertNull("next tick: no attack left", a.assign(102, BOSS));
	}

	@Test
	public void twoPlayersOldestAttackFirst()
	{
		a.attack(100, "Bob", BOSS);   // ranged, lands later
		a.attack(101, "Ann", BOSS);
		assertEquals("Bob", a.assign(103, BOSS));
		assertEquals("Ann", a.assign(104, BOSS));
	}

	@Test
	public void attackOnOtherNpcDoesNotMatch()
	{
		a.attack(100, "Bob", RAT);
		assertNull(a.assign(101, BOSS));
		assertEquals("Bob", a.assign(101, RAT));
	}

	@Test
	public void tooOldAttackIsForgotten()
	{
		a.attack(100, "Bob", BOSS);
		assertNull(a.assign(100 + HitAttributor.MAX_DELAY_TICKS + 1, BOSS));
	}

	@Test
	public void scytheThreeHitsplatsSameTick()
	{
		a.attack(100, "Bob", BOSS);
		assertEquals("Bob", a.assign(101, BOSS));
		assertEquals("Bob", a.assign(101, BOSS));
		assertEquals("Bob", a.assign(101, BOSS));
	}

	@Test
	public void extraHitWithTwoAttackersLandedIsUnknown()
	{
		a.attack(100, "Bob", BOSS);
		a.attack(100, "Ann", BOSS);
		assertEquals("Bob", a.assign(101, BOSS));
		assertEquals("Ann", a.assign(101, BOSS));
		assertNull(a.assign(101, BOSS));
	}

	@Test
	public void animationTwiceInOneAttackIsOneAttack()
	{
		a.attack(100, "Bob", BOSS);
		a.attack(101, "Bob", BOSS); // for example a block animation right after
		assertEquals("Bob", a.assign(102, BOSS));
		assertNull(a.assign(103, BOSS));
	}
}
