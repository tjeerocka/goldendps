package com.goldendps;

import com.goldendps.model.DefenceDrains;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HitChance;
import com.goldendps.model.NpcStats;
import com.goldendps.model.PlayerState;
import com.goldendps.model.TargetMatcher;
import com.goldendps.model.WeaponStyles;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.slayer.SlayerPluginService;

/** Your hit chance on the monster you attack, from your levels, prayers, gear and style. Client thread only. */
class TargetHitChance
{
	/** The hit chance on one monster. */
	static final class Result
	{
		final String targetKey;
		final String name;
		/** For your special attack. */
		final boolean spec;
		/** With its defence now. */
		final double now;
		/** With its full defence, before any special attack lowered it. */
		final double before;

		Result(String targetKey, String name, boolean spec, double now, double before)
		{
			this.targetKey = targetKey;
			this.name = name;
			this.spec = spec;
			this.now = now;
			this.before = before;
		}
	}

	private final Client client;
	private final ItemManager itemManager;
	private final SlayerPluginService slayer;
	private final NpcStats npcStats;
	private final WeaponStyles weaponStyles;
	private final DefenceDrains defence;
	private final FightTracker tracker;
	private final CombatEvents combat;

	TargetHitChance(Client client, ItemManager itemManager, SlayerPluginService slayer, NpcStats npcStats,
		WeaponStyles weaponStyles, DefenceDrains defence, FightTracker tracker, CombatEvents combat)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.slayer = slayer;
		this.npcStats = npcStats;
		this.weaponStyles = weaponStyles;
		this.defence = defence;
		this.tracker = tracker;
		this.combat = combat;
	}

	/** The hit chance on the monster you attack now, or null (no target, unknown monster or weapon, raid). */
	Result now()
	{
		Player me = client.getLocalPlayer();
		Actor target = me == null ? null : me.getInteracting();
		if (!(target instanceof NPC) || tracker.isInRaid())
		{
			return null;
		}
		NPC npc = (NPC) target;
		String name = Names.clean(npc.getName());
		NpcStats.Npc stats = npcStats.get(npc.getId(), name);
		PlayerState state = readPlayer();
		// RuneLite's Slayer plugin knows the monsters of your task (none when that plugin is off)
		state.onTask = slayer.getTargets().contains(npc);
		state.specialAttack = client.getVarpValue(VarPlayerID.SA_ATTACK) == 1;
		state.clickedSpell = combat.clickedSpell();
		state.autocastElement = combat.autocastElement();
		state.autocastDemonbane = combat.autocastDemonbane();
		state.markOfDarkness = combat.markOfDarkness();
		state.soulreaperStacks = combat.soulreaperStacks();
		state.inWilderness = client.getVarbitValue(VarbitID.INSIDE_WILDERNESS) == 1;
		state.standardSpellbook = client.getVarbitValue(VarbitID.SPELLBOOK) == 0;
		WeaponStyles.Style style = weaponStyles.style(state.weaponId, state.weaponName, state.styleSlot);
		if (stats == null || (style == null && state.clickedSpell == null))
		{
			return null;
		}
		String key = TargetMatcher.key(client.getWorld(), npc.getIndex());
		DefenceDrains.Target lowered = defence.get(key);
		int defenceNow = lowered == null ? stats.defence : lowered.getCurrent();
		int magicNow = lowered == null ? stats.magicLevel : lowered.getMagicCurrent();
		return new Result(key, name, state.specialAttack, HitChance.of(state, style, stats, defenceNow, magicNow),
			HitChance.of(state, style, stats, stats.defence, stats.magicLevel));
	}

	private PlayerState readPlayer()
	{
		PlayerState p = new PlayerState();
		p.attack = client.getBoostedSkillLevel(Skill.ATTACK);
		p.ranged = client.getBoostedSkillLevel(Skill.RANGED);
		p.magic = client.getBoostedSkillLevel(Skill.MAGIC);
		p.styleSlot = client.getVarpValue(VarPlayerID.COM_MODE);
		for (Prayer prayer : Prayer.values())
		{
			if (client.isPrayerActive(prayer))
			{
				p.prayers.add(prayer.name());
			}
		}
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item[] items = worn == null ? new Item[0] : worn.getItems();
		for (int slot = 0; slot < items.length; slot++)
		{
			int id = items[slot].getId();
			if (id <= 0)
			{
				continue;
			}
			String itemName = client.getItemDefinition(id).getName();
			p.worn.add(itemName);
			if (slot == EquipmentInventorySlot.WEAPON.getSlotIdx())
			{
				p.weaponId = id;
				p.weaponName = itemName;
			}
			addBonuses(p, itemManager.getItemStats(id));
		}
		return p;
	}

	private static void addBonuses(PlayerState p, ItemStats stats)
	{
		ItemEquipmentStats e = stats == null ? null : stats.getEquipment();
		if (e == null)
		{
			return;
		}
		p.stabBonus += e.getAstab();
		p.slashBonus += e.getAslash();
		p.crushBonus += e.getAcrush();
		p.magicBonus += e.getAmagic();
		p.rangedBonus += e.getArange();
	}
}
