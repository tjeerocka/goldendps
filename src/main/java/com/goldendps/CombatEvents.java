package com.goldendps;

import com.goldendps.model.AttackSource;
import com.goldendps.model.CannonHits;
import com.goldendps.model.DefenceDrains;
import com.goldendps.model.Fight;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HitAttributor;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.SoulreaperStacks;
import com.goldendps.model.TargetMatcher;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.party.PartyService;

/**
 * Turns hitsplats into damage for the fight, and attacks (animations, spell casts, special
 * attacks) into the source of your hits and the owner of other players' hits. Client thread only.
 */
class CombatEvents
{
	private static final int EAT_ANIMATION = 829;

	private final Client client;
	private final PartyService partyService;
	private final GoldenDpsConfig config;
	private final FightTracker tracker;
	private final Bosses bosses;
	private final TargetMatcher matcher;
	private final LocalHealing healing;
	private final BarrowsTracker barrows;
	private final PartyManager party;
	private final PartyUpdates partyUpdates;
	private final DefenceDrains defence;

	private final HitAttributor attributor = new HitAttributor();
	private final AttackSource attackSource = new AttackSource();
	/** The flying graphics of the elemental strike, bolt, blast, wave and surge spells. */
	private static final Map<Integer, String> ELEMENT_OF_PROJECTILE = new HashMap<>();

	static
	{
		int[][] byElement = {
			{SpotanimID.WINDSTRIKE_TRAVEL, SpotanimID.WINDBOLT_TRAVEL, SpotanimID.WINDBLAST_TRAVEL,
				SpotanimID.WINDWAVE_TRAVEL, SpotanimID.WINDSURGE_TRAVEL},
			{SpotanimID.WATERSTRIKE_TRAVEL, SpotanimID.WATERBOLT_TRAVEL, SpotanimID.WATERBLAST_TRAVEL,
				SpotanimID.WATERWAVE_TRAVEL, SpotanimID.WATERSURGE_TRAVEL},
			{SpotanimID.EARTHSTRIKE_TRAVEL, SpotanimID.EARTHBOLT_TRAVEL, SpotanimID.EARTHBLAST_TRAVEL,
				SpotanimID.EARTHWAVE_TRAVEL, SpotanimID.EARTHSURGE_TRAVEL},
			{SpotanimID.FIRESTRIKE_TRAVEL, SpotanimID.FIREBOLT_TRAVEL, SpotanimID.FIREBLAST_TRAVEL,
				SpotanimID.FIREWAVE_TRAVEL, SpotanimID.FIRESURGE_TRAVEL}};
		String[] elements = {"air", "water", "earth", "fire"};
		for (int i = 0; i < elements.length; i++)
		{
			for (int id : byElement[i])
			{
				ELEMENT_OF_PROJECTILE.put(id, elements[i]);
			}
		}
	}

	private static final String DEMONBANE = "demonbane";

	/** DROPCANNON value while your cannon stands. */
	private static final int CANNON_SET_UP = 4;
	/** Client cycles (20 ms) per game tick. */
	private static final int CYCLES_PER_TICK = 30;

	private int lastSpecEnergy = -1;
	private final CannonHits cannon = new CannonHits();
	/** Your last autocast spell: its element, or "demonbane"; and the autocast spell you had then. */
	private String spellElement;
	private int spellElementAutocast;
	/** Mark of Darkness is on: demonbane spells hit demons better. */
	private boolean markOfDarkness;
	private final SoulreaperStacks soulreaper = new SoulreaperStacks();
	/** The spec orb at the last tick: it turns off when the spec is done. */
	private boolean specOrbOn;
	/** Projectiles already seen: a projectile moves many times. */
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());

	CombatEvents(Client client, PartyService partyService, GoldenDpsConfig config, FightTracker tracker, Bosses bosses,
		TargetMatcher matcher, LocalHealing healing, BarrowsTracker barrows, PartyManager party,
		PartyUpdates partyUpdates, DefenceDrains defence)
	{
		this.client = client;
		this.partyService = partyService;
		this.config = config;
		this.tracker = tracker;
		this.bosses = bosses;
		this.matcher = matcher;
		this.healing = healing;
		this.barrows = barrows;
		this.party = party;
		this.partyUpdates = partyUpdates;
		this.defence = defence;
	}

	void hitsplatApplied(Actor actor, Hitsplat hitsplat)
	{
		long now = System.currentTimeMillis();
		if (actor instanceof NPC)
		{
			npcHit((NPC) actor, hitsplat, now);
		}
		else if (actor == client.getLocalPlayer())
		{
			localPlayerHit(hitsplat, now);
		}
		else if (actor instanceof Player && config.estimateOthers() && isGroupMode())
		{
			otherPlayerHit((Player) actor, hitsplat, now);
		}
	}

	private void npcHit(NPC npc, Hitsplat hitsplat, long now)
	{
		int type = hitsplat.getHitsplatType();
		int amount = hitsplat.getAmount();
		String key = TargetMatcher.key(client.getWorld(), npc.getIndex());
		String name = Names.clean(npc.getName());
		boolean boss = bosses.isBoss(npc.getId(), name);
		WorldPoint pos = npc.getWorldLocation();
		if (pos != null)
		{
			matcher.seen(key, pos.getX(), pos.getY(), pos.getPlane());
		}

		if (hitsplat.isMine())
		{
			int tick = client.getTickCount();
			boolean miss = type == HitsplatID.BLOCK_ME || amount <= 0;
			boolean onYourTarget = client.getLocalPlayer() != null && client.getLocalPlayer().getInteracting() == npc;
			String source = cannon.isCannonHit(key, tick) ? CannonHits.CANNON
				: attackSource.sourceForHit(tick, healing.weaponName(), onYourTarget);
			// playerHit before seenHit, so the first hit of a fight is also seen
			tracker.playerHit(now, Names.localName(client), PlayerKind.LOCAL, key, npc.getId(), name, boss, amount, miss,
				source);
			tracker.seenHit(now, key, npc.getId(), name, boss, amount);
			if (!tracker.isInRaid())
			{
				defence.hit(tick, key, npc.getId(), name, Names.localName(client), source, amount, miss);
			}
			barrows.hit(now, name);
			if (boss)
			{
				party.bossHit();
			}
			if (!miss)
			{
				healing.youHit(npc, amount, tick);
			}
			GoldenDpsUpdate.Hit sent = new GoldenDpsUpdate.Hit(npc.getIndex(), npc.getId(), name, amount, miss, boss, pos);
			sent.source = source;
			partyUpdates.addHit(sent);
		}
		else if (hitsplat.isOthers() && config.estimateOthers() && isGroupMode())
		{
			// party members send their own hits; without this setting other players are left out entirely
			tracker.seenHit(now, key, npc.getId(), name, boss, amount);
			String who = attributor.assign(client.getTickCount(), key);
			if (who != null && !party.isReporter(who, now) && !who.equals(Names.localName(client)))
			{
				tracker.estimateHit(now, who, key, amount, type == HitsplatID.BLOCK_OTHER || amount <= 0);
			}
		}
		else if (isEffect(type))
		{
			tracker.effectHit(now, key, npc.getId(), name, boss, amount);
		}
	}

	private void localPlayerHit(Hitsplat hitsplat, long now)
	{
		int amount = hitsplat.getAmount();
		if (amount <= 0 || !isDamageOnPlayer(hitsplat))
		{
			return;
		}
		healing.damageOnYou(amount);
		if (tracker.getCurrent() != null)
		{
			tracker.damageTaken(now, Names.localName(client), PlayerKind.LOCAL, amount);
			partyUpdates.addDamageTaken(amount);
		}
	}

	/** Damage on players without GoldenDPS, seen from your client. */
	private void otherPlayerHit(Player player, Hitsplat hitsplat, long now)
	{
		String who = Names.clean(player.getName());
		if (who != null && hitsplat.getAmount() > 0 && isDamageOnPlayer(hitsplat) && !party.isReporter(who, now))
		{
			tracker.seenDamageTaken(now, who, hitsplat.getAmount());
		}
	}

	void animationChanged(Actor actor)
	{
		if (actor == null)
		{
			return;
		}
		int animation = actor.getAnimation();
		Actor target = actor.getInteracting();
		if (animation == -1 || animation == EAT_ANIMATION || !(target instanceof NPC))
		{
			return;
		}
		if (actor == client.getLocalPlayer())
		{
			int tick = client.getTickCount();
			String weapon = healing.weaponName();
			attackSource.attack(tick, weapon);
			boolean spec = specOrbOn || client.getVarpValue(VarPlayerID.SA_ATTACK) == 1;
			soulreaper.attack(tick, weapon, spec);
			if (spec && weapon != null && weapon.startsWith("Soulreaper axe"))
			{
				// its spec costs no energy, so the energy drop below does not see it
				attackSource.specUsed(tick, weapon);
			}
		}
		else if (actor instanceof Player && config.estimateOthers() && isGroupMode())
		{
			String who = Names.clean(actor.getName());
			if (who != null)
			{
				attributor.attack(client.getTickCount(), who, TargetMatcher.key(client.getWorld(), ((NPC) target).getIndex()));
			}
		}
	}

	/** A "Cast" menu click on a target, such as "Ice Barrage -> Vorkath". */
	void spellCastClicked(int tick, String target)
	{
		attackSource.castClicked(tick, target);
	}

	/** A drop in special attack energy means a special attack was used. Drained stats come back slowly. */
	void tick(int tick)
	{
		defence.restore(tick);
		specOrbOn = client.getVarpValue(VarPlayerID.SA_ATTACK) == 1;
		int spec = client.getVarpValue(VarPlayerID.SA_ENERGY);
		if (lastSpecEnergy >= 0 && spec < lastSpecEnergy)
		{
			attackSource.specUsed(tick, healing.weaponName());
		}
		lastSpecEnergy = spec;
	}

	void reset()
	{
		attributor.clear();
		attackSource.reset();
		lastSpecEnergy = -1;
		cannon.clear();
		seenProjectiles.clear();
		spellElement = null;
		markOfDarkness = false;
	}

	/** Mark of Darkness starts and ends with a game message. */
	void gameMessage(String message)
	{
		if (message.startsWith("You have placed a Mark of Darkness upon yourself"))
		{
			markOfDarkness = true;
		}
		else if (message.startsWith("Your Mark of Darkness has"))
		{
			markOfDarkness = false;
		}
	}

	boolean markOfDarkness()
	{
		return markOfDarkness;
	}

	int soulreaperStacks()
	{
		return soulreaper.stacks(client.getTickCount());
	}

	/** The spell you cast by clicking it, or null when you attack with your weapon or autocast. */
	String clickedSpell()
	{
		return attackSource.clickedSpell(client.getTickCount());
	}

	/** "fire", "water", "earth" or "air" while you autocast that element, else null. */
	String autocastElement()
	{
		String spell = autocastSpell();
		return DEMONBANE.equals(spell) ? null : spell;
	}

	/** You autocast a demonbane spell. */
	boolean autocastDemonbane()
	{
		return DEMONBANE.equals(autocastSpell());
	}

	private String autocastSpell()
	{
		int autocast = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
		return autocast != 0 && autocast == spellElementAutocast ? spellElement : null;
	}

	/** Demonbane spells fly no projectile: the cast graphic on you shows them. */
	void graphicChanged(Actor actor)
	{
		if (actor == client.getLocalPlayer() && (actor.hasSpotAnim(SpotanimID.INFERIOR_DEMONBANE_CAST_SPOTANIM)
			|| actor.hasSpotAnim(SpotanimID.SUPERIOR_DEMONBANE_CAST_SPOTANIM)
			|| actor.hasSpotAnim(SpotanimID.DARK_DEMONBANE_CAST_SPOTANIM)))
		{
			spellElement = DEMONBANE;
			spellElementAutocast = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
		}
	}

	/**
	 * A cannonball from your cannon: remember when it lands, so its hit counts as the cannon's.
	 * An elemental spell of yours: remember its element for the hit chance.
	 */
	void projectileMoved(Projectile p)
	{
		String element = ELEMENT_OF_PROJECTILE.get(p.getId());
		if (element != null && p.getSourceActor() == client.getLocalPlayer())
		{
			spellElement = element;
			spellElementAutocast = client.getVarbitValue(VarbitID.AUTOCAST_SPELL);
			return;
		}
		if (!isCannonball(p.getId()) || !seenProjectiles.add(p) || !(p.getTargetActor() instanceof NPC)
			|| client.getVarpValue(VarPlayerID.DROPCANNON) != CANNON_SET_UP)
		{
			return;
		}
		// other players' cannons fire the same balls; yours stands on the 3x3 tiles from this corner
		WorldPoint corner = WorldPoint.fromCoord(client.getVarpValue(VarPlayerID.OWNEDMCANNON));
		WorldPoint from = p.getSourcePoint();
		if (from == null || from.getPlane() != corner.getPlane() || from.getX() - corner.getX() < 0
			|| from.getX() - corner.getX() > 2 || from.getY() - corner.getY() < 0 || from.getY() - corner.getY() > 2)
		{
			return;
		}
		NPC target = (NPC) p.getTargetActor();
		int landTick = client.getTickCount() + (p.getRemainingCycles() + CYCLES_PER_TICK - 1) / CYCLES_PER_TICK;
		cannon.fired(TargetMatcher.key(client.getWorld(), target.getIndex()), landTick);
	}

	private static boolean isCannonball(int projectileId)
	{
		return projectileId == SpotanimID.CANNONBALL_TRAVEL || projectileId == SpotanimID.CANNONBALL_TRAVEL_GRANITE
			|| projectileId == SpotanimID.CANNONBALL_TRAVEL_LEAGUE
			|| projectileId == SpotanimID.CANNONBALL_TRAVEL_GRANITE_LEAGUE;
	}

	/**
	 * Other players count in an instance, and in a party while you fight a boss
	 * (or always in a party when "others only when together" is off).
	 */
	private boolean isGroupMode()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv != null && wv.isInstance())
		{
			return true;
		}
		if (!partyService.isInParty())
		{
			return false;
		}
		Fight current = tracker.getCurrent();
		return !config.othersOnlyTogether() || (current != null && current.hasBossTarget());
	}

	/** Damage over time; the game does not say who caused it. */
	private static boolean isEffect(int type)
	{
		return type == HitsplatID.POISON || type == HitsplatID.VENOM || type == HitsplatID.DISEASE
			|| type == HitsplatID.BURN || type == HitsplatID.BLEED;
	}

	private static boolean isDamageOnPlayer(Hitsplat hitsplat)
	{
		int type = hitsplat.getHitsplatType();
		return hitsplat.isMine() || hitsplat.isOthers() || isEffect(type) || type == HitsplatID.DOOM;
	}
}
