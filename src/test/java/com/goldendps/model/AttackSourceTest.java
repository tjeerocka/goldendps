package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

public class AttackSourceTest
{
	@Test
	public void hitsUseTheWeaponOfTheLastAttack()
	{
		AttackSource s = new AttackSource();
		s.attack(10, "Twisted bow");
		// you switched to the scythe while the arrow flies
		assertEquals("Twisted bow", s.sourceForHit(12, "Scythe of vitur", true));
		s.attack(13, "Scythe of vitur");
		assertEquals("Scythe of vitur", s.sourceForHit(14, "Scythe of vitur", true));
	}

	@Test
	public void noRecentAttackUsesTheWeaponNow()
	{
		AttackSource s = new AttackSource();
		assertEquals("Abyssal whip", s.sourceForHit(5, "Abyssal whip", true));
		assertEquals(AttackSource.UNARMED, s.sourceForHit(5, null, true));
		s.attack(10, "Twisted bow");
		assertEquals("Abyssal whip", s.sourceForHit(10 + AttackSource.ATTACK_TICKS + 1, "Abyssal whip", true));
	}

	@Test
	public void specHitsGetTheirOwnLine()
	{
		AttackSource s = new AttackSource();
		s.attack(10, "Dragon claws");
		s.specUsed(10, "Dragon claws");
		// claws: 2 hitsplats on the first tick, 2 on the next
		assertEquals("Dragon claws (spec)", s.sourceForHit(11, "Dragon claws", true));
		assertEquals("Dragon claws (spec)", s.sourceForHit(11, "Dragon claws", true));
		assertEquals("Dragon claws (spec)", s.sourceForHit(12, "Abyssal whip", true));
		// later hits are normal again
		s.attack(13, "Abyssal whip");
		assertEquals("Abyssal whip", s.sourceForHit(14, "Abyssal whip", true));
	}

	@Test
	public void cannonHitsOnOtherMonstersAreNotTheSpec()
	{
		AttackSource s = new AttackSource();
		s.attack(10, "Elder maul");
		s.specUsed(10, "Elder maul");
		// the cannon hits another monster first, on the spec tick and the next
		assertEquals("Elder maul", s.sourceForHit(10, "Elder maul", false));
		assertEquals("Elder maul", s.sourceForHit(11, "Elder maul", false));
		assertEquals("Elder maul (spec)", s.sourceForHit(11, "Elder maul", true));
	}

	@Test
	public void specWithoutHitsRunsOut()
	{
		AttackSource s = new AttackSource();
		s.specUsed(10, "Dragon warhammer");
		s.attack(30, "Abyssal whip");
		assertEquals("Abyssal whip", s.sourceForHit(31, "Abyssal whip", true));
	}

	@Test
	public void clickedSpellsShowByName()
	{
		AttackSource s = new AttackSource();
		s.castClicked(10, "Ice Barrage -> Vorkath  (level-732)");
		s.attack(12, "Kodai wand");
		assertEquals("Ice Barrage", s.sourceForHit(14, "Kodai wand", true));
		// the next attack is an autocast: the weapon
		s.attack(17, "Kodai wand");
		assertEquals("Kodai wand", s.sourceForHit(19, "Kodai wand", true));
	}

	@Test
	public void oldSpellClickIsIgnored()
	{
		AttackSource s = new AttackSource();
		s.castClicked(10, "Fire Surge");
		s.attack(10 + AttackSource.SPELL_TICKS + 1, "Trident of the swamp");
		assertEquals("Trident of the swamp", s.sourceForHit(35, "Trident of the swamp", true));
	}

	@Test
	public void theSpellYouCastByHand()
	{
		AttackSource s = new AttackSource();
		assertNull(s.clickedSpell(10));
		s.castClicked(10, "Ice Barrage -> Vorkath");
		assertEquals("clicked, not cast yet", "Ice Barrage", s.clickedSpell(11));
		s.attack(12, "Kodai wand");
		assertEquals("while you keep casting", "Ice Barrage", s.clickedSpell(15));
		s.attack(20, "Kodai wand");
		assertNull("you attack with your weapon again", s.clickedSpell(21));
	}
}
