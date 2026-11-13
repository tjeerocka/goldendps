package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;

public class HitChanceTest
{
	private static final double D = 0.0001;
	private static final WeaponStyles WEAPONS = new WeaponStyles(Arrays.asList(
		"# id\tname\tcategory",
		"4151\tAbyssal whip\tWhip",
		"20997\tTwisted bow\tBow",
		"12926\tToxic blowpipe\tThrown",
		"12899\tTrident of the swamp\tPowered Staff",
		"13652\tDragon claws\tClaw"));

	private NpcStats.Npc vorkath;
	private NpcStats.Npc demon;
	private NpcStats.Npc blueDragon;
	private NpcStats.Npc kalphiteQueen;
	private NpcStats.Npc revenant;
	private NpcStats.Npc golem;
	private NpcStats.Npc vyrewatch;
	private PlayerState me;

	@Before
	public void setUp()
	{
		NpcStats stats = new NpcStats(Arrays.asList(
			"8059\tVorkath\t214\t150\t150\t26\t108\t108\t240\t26\t26\t26\tdragon,undead\t\t\t1",
			"415\tAbyssal demon\t135\t1\t0\t20\t20\t20\t0\t20\t20\t20\tdemon\t\t\t1",
			"963\tKalphite Queen\t300\t150\t0\t50\t50\t10\t100\t100\t100\t100\tkalphite\t\t\t1",
			"7881\tRevenant demon\t140\t100\t0\t10\t10\t10\t10\t10\t10\t10\tdemon\t\t\t1",
			"6503\tGolem\t100\t1\t0\t10\t10\t10\t10\t10\t10\t10\tgolem\t\t\t1",
			"9700\tVyrewatch\t100\t1\t0\t10\t10\t10\t10\t10\t10\t10\tvampyre2\t\t\t1",
			"265\tBlue dragon\t95\t1\t0\t0\t70\t70\t60\t50\t50\t10\tdragon,fiery\twater:50\t\t1"));
		blueDragon = stats.get(265, "Blue dragon");
		kalphiteQueen = stats.get(963, "Kalphite Queen");
		revenant = stats.get(7881, "Revenant demon");
		golem = stats.get(6503, "Golem");
		vyrewatch = stats.get(9700, "Vyrewatch");
		vorkath = stats.get(8059, "Vorkath");
		demon = stats.get(415, "Abyssal demon");
		me = new PlayerState();
		me.attack = 118;
		me.ranged = 112;
		me.magic = 99;
		me.weaponId = 4151;
		me.weaponName = "Abyssal whip";
		me.slashBonus = 132;
		me.rangedBonus = 100;
		me.magicBonus = 50;
	}

	@Test
	public void meleeWithPietyOnAccurate()
	{
		me.prayers.add("PIETY");
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		// (118 * 1.2 = 141) + 3 + 8 = 152; 152 * (132 + 64)
		assertEquals(29_792, HitChance.attackRoll(me, flick, vorkath));
		// (214 + 9) * (108 + 64)
		assertEquals(38_356, HitChance.defenceRoll(WeaponStyles.Type.SLASH, vorkath, 214, 150));
		assertEquals(29_792 / (2.0 * 38_357), HitChance.of(me, flick, vorkath, 214, 150), D);
	}

	@Test
	public void loweredDefenceRaisesTheChance()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		assertTrue(HitChance.of(me, flick, vorkath, 150, 150) > HitChance.of(me, flick, vorkath, 214, 150));
	}

	@Test
	public void magicUsesTheMagicLevelSoDefenceDrainsDoNotHelp()
	{
		me.weaponId = 12899;
		me.weaponName = "Trident of the swamp";
		WeaponStyles.Style accurate = WEAPONS.style(12899, me.weaponName, 0);
		assertEquals(WeaponStyles.Type.MAGIC, accurate.type);
		// 99 + 2 + 9 = 110; 110 * (50 + 64)
		assertEquals(12_540, HitChance.attackRoll(me, accurate, demon));
		assertEquals(HitChance.of(me, accurate, vorkath, 214, 150), HitChance.of(me, accurate, vorkath, 0, 150), D);
	}

	@Test
	public void attackStylesPickStabSlashOrCrush()
	{
		assertEquals(WeaponStyles.Type.SLASH, WEAPONS.style(13652, "Dragon claws", 0).type);
		assertEquals(WeaponStyles.Type.STAB, WEAPONS.style(13652, "Dragon claws", 2).type);
		assertEquals(1, WEAPONS.style(13652, "Dragon claws", 2).stanceBonus);
		assertEquals("whips have no slot 2", null, WEAPONS.style(4151, "Abyssal whip", 2));
		assertEquals(WeaponStyles.Type.SLASH, WEAPONS.style(4151, "Abyssal whip", 3).type);
		assertEquals("unarmed punches", WeaponStyles.Type.CRUSH, WEAPONS.style(0, null, 0).type);
		assertEquals("by name for an id the wiki misses", WeaponStyles.Type.SLASH,
			WEAPONS.style(99999, "Abyssal whip", 0).type);
		assertNull(WEAPONS.style(99999, "Mystery stick", 0));
		assertEquals(WeaponStyles.Type.LIGHT, WEAPONS.style(12926, "Toxic blowpipe", 1).type);
	}

	@Test
	public void voidGivesTenPercentMoreLevel()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		long without = HitChance.attackRoll(me, flick, demon);
		me.worn = Arrays.asList("Void melee helm", "Elite void top", "Elite void robe", "Void knight gloves");
		// (118 + 3 + 8) * 1.1 = 141
		assertEquals(141 * 196, HitChance.attackRoll(me, flick, demon));
		assertEquals(129 * 196, without);
	}

	@Test
	public void salveAgainstUndeadAndArclightAgainstDemons()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		long base = 129 * 196;
		me.worn = Collections.singletonList("Salve amulet(ei)");
		assertEquals(base * 6 / 5, HitChance.attackRoll(me, flick, vorkath));
		me.weaponName = "Arclight";
		assertEquals(base * 17 / 10, HitChance.attackRoll(me, flick, demon));
	}

	@Test
	public void slayerHelmetOnTaskButNotTogetherWithASalve()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		long base = 129 * 196;
		me.worn = Collections.singletonList("Slayer helmet");
		assertEquals("not on task", base, HitChance.attackRoll(me, flick, demon));
		me.onTask = true;
		assertEquals(base * 7 / 6, HitChance.attackRoll(me, flick, demon));
		me.worn = Arrays.asList("Twisted slayer helmet (i)", "Salve amulet(ei)");
		assertEquals("the salve wins on undead", base * 6 / 5, HitChance.attackRoll(me, flick, vorkath));
	}

	@Test
	public void onlyAnImbuedHelmetHelpsRanged()
	{
		me.onTask = true;
		me.weaponId = 20997;
		me.weaponName = "Twisted bow";
		WeaponStyles.Style rapid = WEAPONS.style(20997, "Twisted bow", 1);
		me.worn = Collections.singletonList("Slayer helmet");
		long plain = HitChance.attackRoll(me, rapid, demon);
		me.worn = Collections.singletonList("Slayer helmet (i)");
		assertEquals(plain * 23 / 20, HitChance.attackRoll(me, rapid, demon), 1);
	}

	@Test
	public void twistedBowLikesHighMagic()
	{
		assertEquals(114, HitChance.twistedBowPercent(vorkath));
		NpcStats.Npc strong = new NpcStats(Collections.singletonList(
			"1\tStrong\t200\t250\t0\t0\t0\t0\t0\t0\t0\t0\t\t\t\t1")).get(1, null);
		assertEquals(140, HitChance.twistedBowPercent(strong));
	}

	@Test
	public void chanceFormulaBothSides()
	{
		assertEquals(1 - 102 / (2.0 * 201), HitChance.chance(200, 100), D);
		assertEquals(100 / (2.0 * 201), HitChance.chance(100, 200), D);
	}

	@Test
	public void theWeaponFileLoads() throws Exception
	{
		assertEquals(WeaponStyles.Type.STANDARD, WeaponStyles.load().style(20997, "Twisted bow", 0).type);
	}

	@Test
	public void tumekensShadowTriplesTheMagicBonus()
	{
		me.weaponName = "Tumeken's shadow";
		WeaponStyles.Style accurate = WeaponStyles.CLICKED_SPELL;
		// 99 + 0 + 9 = 108; 108 * (50 * 3 + 64)
		assertEquals(108 * 214, HitChance.attackRoll(me, accurate, demon));
		me.clickedSpell = "Ice Barrage";
		assertEquals("not for a spell you click", 108 * 114, HitChance.attackRoll(me, accurate, demon));
	}

	@Test
	public void elementalWeaknessAddsItsPercent()
	{
		me.clickedSpell = "Water Surge";
		long roll = 108L * 114;
		assertEquals("Blue dragon: +50% for water", roll + roll / 2,
			HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, blueDragon));
		me.clickedSpell = "Fire Surge";
		assertEquals(roll, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, blueDragon));
		me.clickedSpell = null;
		me.autocastElement = "water";
		assertEquals("autocast counts too", roll + roll / 2,
			HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, blueDragon));
	}

	@Test
	public void elementsOfSpells()
	{
		assertEquals("air", HitChance.elementOf("Wind Strike"));
		assertEquals("fire", HitChance.elementOf("Fire Surge"));
		assertNull(HitChance.elementOf("Ice Barrage"));
		assertNull(HitChance.elementOf("Flames of Zamorak"));
	}

	@Test
	public void aClickedSpellIsMagicWhateverYouHold()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		double melee = HitChance.of(me, flick, vorkath, 214, 150);
		me.clickedSpell = "Ice Barrage";
		// Vorkath: magic level 150, magic defence 240
		assertEquals(HitChance.chance(108L * 114, (150 + 9) * (240 + 64)), HitChance.of(me, flick, vorkath, 214, 150), D);
		assertTrue(melee != HitChance.of(me, flick, vorkath, 214, 150));
	}

	@Test
	public void specialAttacksHaveTheirOwnAccuracyAndDefence()
	{
		me.weaponName = "Bandos godsword";
		me.crushBonus = 0;
		WeaponStyles.Style smash = new WeaponStyles(Collections.singletonList("11804\tBandos godsword\t2h Sword"))
			.style(11804, "Bandos godsword", 2);
		assertEquals(WeaponStyles.Type.CRUSH, smash.type);
		me.specialAttack = true;
		// a godsword spec rolls twice as accurately and against slash defence, whatever the style
		long roll = HitChance.attackRoll(me, smash, vorkath) * 2;
		assertEquals(HitChance.chance(roll, HitChance.defenceRoll(WeaponStyles.Type.SLASH, vorkath, 214, 150)),
			HitChance.of(me, smash, vorkath, 214, 150), D);
	}

	@Test
	public void weaponsWithoutASpecialRuleSpecLikeANormalAttack()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		me.weaponName = "Dragon warhammer";
		double normal = HitChance.of(me, flick, vorkath, 214, 150);
		me.specialAttack = true;
		assertEquals(normal, HitChance.of(me, flick, vorkath, 214, 150), D);
	}

	@Test
	public void dragonClawsSpecHitsWhenAnyOfFourRollsHits()
	{
		me.weaponName = "Dragon claws";
		WeaponStyles.Style slash = WEAPONS.style(13652, "Dragon claws", 0);
		me.specialAttack = true;
		double one = HitChance.chance(HitChance.attackRoll(me, slash, vorkath),
			HitChance.defenceRoll(WeaponStyles.Type.SLASH, vorkath, 214, 150));
		assertEquals(1 - Math.pow(1 - one, 4), HitChance.of(me, slash, vorkath, 214, 150), D);
	}

	@Test
	public void loweredMagicLevelHelpsMagic()
	{
		me.clickedSpell = "Ice Barrage";
		assertTrue(HitChance.of(me, null, vorkath, 214, 120) > HitChance.of(me, null, vorkath, 214, 150));
	}

	@Test
	public void gearForSomeMonstersAndPlaces()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		long base = 129 * 196;
		me.weaponName = "Keris partisan of breaching";
		assertEquals(base * 133 / 100, HitChance.attackRoll(me, flick, kalphiteQueen));
		me.weaponName = "Viggora's chainmace";
		assertEquals("not in the Wilderness", base, HitChance.attackRoll(me, flick, demon));
		me.inWilderness = true;
		assertEquals(base * 3 / 2, HitChance.attackRoll(me, flick, demon));
		me.weaponName = "Viggora's chainmace (u)";
		assertEquals("uncharged", base, HitChance.attackRoll(me, flick, demon));
		me.weaponName = "Tzhaar-ket-om";
		me.worn = Arrays.asList("Obsidian helmet", "Obsidian platebody", "Obsidian platelegs");
		assertEquals(base + base / 10, HitChance.attackRoll(me, flick, demon));
	}

	@Test
	public void inquisitorsHelpsCrush()
	{
		me.crushBonus = 132;
		WeaponStyles.Style pound = new WeaponStyles(Collections.singletonList("24417\tInquisitor's mace\tSpiked"))
			.style(24417, "Inquisitor's mace", 0);
		me.worn = Arrays.asList("Inquisitor's great helm", "Inquisitor's hauberk", "Inquisitor's plateskirt");
		assertEquals(129L * 196 * 205 / 200, HitChance.attackRoll(me, pound, demon));
	}

	@Test
	public void crystalArmourWithACrystalBowAndSmokeStaves()
	{
		me.weaponName = "Bow of Faerdhinen (c)";
		WeaponStyles.Style rapid = WEAPONS.style(20997, "Twisted bow", 1);
		me.worn = Arrays.asList("Crystal helm", "Crystal body", "Crystal legs");
		long plain = (112 + 8) * (100 + 64);
		assertEquals(plain * 26 / 20, HitChance.attackRoll(me, rapid, demon));
		me.weaponName = "Mystic smoke staff";
		me.standardSpellbook = true;
		assertEquals(108L * 114 * 110 / 100, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, demon));
	}

	@Test
	public void fangRollsTwice()
	{
		me.weaponName = "Osmumten's fang";
		WeaponStyles.Style lunge = new WeaponStyles(Collections.singletonList("26219\tOsmumten's fang\tStab Sword"))
			.style(26219, "Osmumten's fang", 1);
		double fang = HitChance.of(me, lunge, vorkath, 214, 150);
		long roll = HitChance.attackRoll(me, lunge, vorkath);
		long defence = HitChance.defenceRoll(WeaponStyles.Type.STAB, vorkath, 214, 150);
		assertEquals(HitChance.fangChance(roll, defence), fang, D);
		assertTrue("better than one roll", fang > HitChance.chance(roll, defence));
		// about 1 - (1 - p)^2 for a roll that wins clearly
		assertEquals(1 - Math.pow(1 - HitChance.chance(1000, 100), 2), HitChance.fangChance(1000, 100), 0.001);
	}

	@Test
	public void brimstoneRingSometimesIgnoresMagicDefence()
	{
		me.clickedSpell = "Ice Barrage";
		double without = HitChance.of(me, null, vorkath, 214, 150);
		me.worn = Collections.singletonList("Brimstone ring");
		long roll = 108L * 114;
		long defence = (150 + 9) * (240 + 64);
		assertEquals(0.75 * HitChance.chance(roll, defence) + 0.25 * HitChance.chance(roll, defence * 9 / 10),
			HitChance.of(me, null, vorkath, 214, 150), D);
		assertTrue(HitChance.of(me, null, vorkath, 214, 150) > without);
	}

	@Test
	public void demonWeapons()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		long base = 129 * 196;
		me.weaponName = "Darklight";
		assertEquals(base + base * 60 / 100, HitChance.attackRoll(me, flick, demon));
		me.weaponName = "Burning claws";
		assertEquals(base + base * 5 / 100, HitChance.attackRoll(me, flick, demon));
		me.weaponName = "Scorching bow";
		WeaponStyles.Style rapid = WEAPONS.style(20997, "Twisted bow", 1);
		long ranged = 120L * 164;
		assertEquals(ranged + ranged * 30 / 100, HitChance.attackRoll(me, rapid, demon));
	}

	@Test
	public void demonbaneSpellsWithMarkOfDarknessAndPurgingStaff()
	{
		me.clickedSpell = "Inferior Demonbane";
		long base = 108L * 114;
		assertEquals(base + base * 20 / 100, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, demon));
		me.markOfDarkness = true;
		me.weaponName = "Purging staff";
		assertEquals(base + base * 80 / 100, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, demon));
		assertEquals("not on a dragon", base, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, vorkath));
		me.clickedSpell = null;
		me.autocastDemonbane = true;
		assertEquals("autocast on the purging staff", base + base * 80 / 100,
			HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, demon));
	}

	@Test
	public void golemsRevenantsAndVampyres()
	{
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		long base = 129 * 196;
		me.weaponName = "Granite hammer";
		assertEquals(base * 13 / 10, HitChance.attackRoll(me, flick, golem));
		me.weaponName = "Abyssal whip";
		me.worn = Collections.singletonList("Amulet of avarice");
		assertEquals(base * 24 / 20, HitChance.attackRoll(me, flick, revenant));
		me.worn = Collections.singletonList("Efaritay's aid");
		me.weaponName = "Blisterwood flail";
		assertEquals(base * 105 / 100 * 23 / 20, HitChance.attackRoll(me, flick, vyrewatch));
	}

	@Test
	public void magicPercentsAddUpAndTomeOfWater()
	{
		me.standardSpellbook = true;
		me.weaponName = "Mystic smoke staff";
		me.worn = Collections.singletonList("Salve amulet(ei)");
		me.clickedSpell = "Water Surge";
		long base = 108L * 114;
		// salve +20% and smoke +10% add up to +30%
		assertEquals(base * 130 / 100, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, vorkath));
		me.worn = Collections.singletonList("Tome of water");
		assertEquals(base * 110 / 100 * 6 / 5, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, demon));
	}

	@Test
	public void voidMageMultipliesTheWholeLevel()
	{
		me.clickedSpell = "Ice Barrage";
		me.worn = Arrays.asList("Void mage helm", "Void knight top", "Void knight robe", "Void knight gloves");
		assertEquals(108L * 29 / 20 * 114, HitChance.attackRoll(me, WeaponStyles.CLICKED_SPELL, demon));
	}

	@Test
	public void monstersWithTheirOwnRules()
	{
		NpcStats stats = new NpcStats(Arrays.asList(
			"5916\tSpawn\t10\t1\t0\t0\t0\t0\t-10\t0\t0\t0\t\t\talways-hit\t1",
			"12814\tFremennik warband archer\t80\t110\t150\t0\t0\t0\t75\t50\t50\t50\t\t\talways-hit-melee\t1",
			"8917\tFragment of Seren\t102\t102\t0\t320\t220\t320\t10\t480\t480\t480\t\t\tmagic-uses-defence\t1"));
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		assertEquals(1, HitChance.of(me, flick, stats.get(5916, "Spawn"), 10, 1), D);
		NpcStats.Npc archer = stats.get(12814, "Fremennik warband archer");
		assertEquals("melee always hits it", 1, HitChance.of(me, flick, archer, 80, 110), D);
		me.clickedSpell = "Ice Barrage";
		assertTrue("magic does not", HitChance.of(me, flick, archer, 80, 110) < 1);
		NpcStats.Npc seren = stats.get(8917, "Fragment of Seren");
		assertTrue("its defence blocks magic, so a lower one helps",
			HitChance.of(me, null, seren, 50, 102) > HitChance.of(me, null, seren, 102, 102));
	}

	@Test
	public void soulreaperSpecGets12PercentPerStack()
	{
		WeaponStyles.Style hack = WEAPONS.style(4151, "Abyssal whip", 0);
		me.weaponName = "Soulreaper axe";
		me.specialAttack = true;
		me.soulreaperStacks = 5;
		long roll = HitChance.attackRoll(me, hack, vorkath) * 160 / 100;
		assertEquals(HitChance.chance(roll, HitChance.defenceRoll(WeaponStyles.Type.SLASH, vorkath, 214, 150)),
			HitChance.of(me, hack, vorkath, 214, 150), D);
	}

	@Test
	public void negativeRollsStayBetweenZeroAndOne()
	{
		// a monster with -100 defence bonus has a negative defence roll: (2 + 9) * (-100 + 64)
		assertEquals(1 - 1.0 / (394 + 1) / (1001), HitChance.chance(1000, -396), D);
		assertEquals(0, HitChance.chance(-50, 100), D);
		assertTrue(HitChance.chance(-10, -50) > 0 && HitChance.chance(-10, -50) <= 1);
		assertTrue(HitChance.fangChance(1000, -396) <= 1);
	}

	@Test
	public void scurriussRatsAreAlwaysHitButNotByAClickedSpell()
	{
		NpcStats.Npc rat = new NpcStats(Collections.singletonList(
			"7223\tGiant rat (Scurrius)\t10\t1\t0\t0\t0\t0\t0\t0\t0\t0\trat\t\talways-hit-not-clicked\t1"))
			.get(7223, null);
		WeaponStyles.Style flick = WEAPONS.style(4151, "Abyssal whip", 0);
		assertEquals(1, HitChance.of(me, flick, rat, 10, 1), D);
		me.clickedSpell = "Fire Strike";
		assertTrue(HitChance.of(me, flick, rat, 10, 1) < 1);
	}
}
