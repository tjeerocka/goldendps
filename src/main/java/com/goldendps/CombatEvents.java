package com.goldendps;

import com.goldendps.model.AttackSource;
import com.goldendps.model.Fight;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HitAttributor;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.TargetMatcher;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
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

	private final HitAttributor attributor = new HitAttributor();
	private final AttackSource attackSource = new AttackSource();
	private int lastSpecEnergy = -1;

	CombatEvents(Client client, PartyService partyService, GoldenDpsConfig config, FightTracker tracker, Bosses bosses, TargetMatcher matcher,
		LocalHealing healing, BarrowsTracker barrows, PartyManager party, PartyUpdates partyUpdates)
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
		else if (actor instanceof Player && config.nearbyDamageTaken() && isGroupMode())
		{
			otherPlayerHit((Player) actor, hitsplat, now);
		}
	}

	private void npcHit(NPC npc, Hitsplat hitsplat, long now)
	{
		int type = hitsplat.getHitsplatType();
		int amount = hitsplat.getAmount();
		String key = TargetMatcher.key(client.getWorld(), npc.getIndex());
		String name = npc.getName();
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
			String source = attackSource.sourceForHit(tick, healing.weaponName());
			// playerHit before seenHit, so the first hit of a fight is also seen
			tracker.playerHit(now, Names.localName(client), PlayerKind.LOCAL, key, npc.getId(), name, boss, amount, miss,
				source);
			tracker.seenHit(now, key, npc.getId(), name, boss, amount);
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
		else if (hitsplat.isOthers() && isGroupMode())
		{
			tracker.seenHit(now, key, npc.getId(), name, boss, amount);
			if (config.estimateOthers())
			{
				String who = attributor.assign(client.getTickCount(), key);
				if (who != null && !party.isReporter(who, now) && !who.equals(Names.localName(client)))
				{
					tracker.estimateHit(now, who, key, amount, type == HitsplatID.BLOCK_OTHER || amount <= 0);
				}
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
			attackSource.attack(client.getTickCount(), healing.weaponName());
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

	/** A drop in special attack energy means a special attack was used. */
	void tick(int tick)
	{
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
