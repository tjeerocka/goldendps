package com.goldendps;

import com.goldendps.model.Fight;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HealType;
import com.goldendps.model.HealthWatcher;
import com.goldendps.model.PlayerKind;
import com.goldendps.model.TargetMatcher;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;

/**
 * The {@link GoldenDpsUpdate} of every tick: collects what you did, sends it to the party, and
 * counts the updates that other members send. Client thread only.
 */
class PartyUpdates
{
	/** Other clients send the source as free text; anything this long is not a weapon or spell name. */
	private static final int MAX_SOURCE_LENGTH = 64;

	private final Client client;
	private final PartyService partyService;
	private final PartyManager party;
	private final GoldenDpsConfig config;
	private final FightTracker tracker;
	private final Bosses bosses;
	private final TargetMatcher matcher;

	private GoldenDpsUpdate outgoing = new GoldenDpsUpdate();

	PartyUpdates(Client client, PartyService partyService, PartyManager party, GoldenDpsConfig config,
		FightTracker tracker, Bosses bosses, TargetMatcher matcher)
	{
		this.client = client;
		this.partyService = partyService;
		this.party = party;
		this.config = config;
		this.tracker = tracker;
		this.bosses = bosses;
		this.matcher = matcher;
	}

	void addHit(GoldenDpsUpdate.Hit hit)
	{
		outgoing.hits.add(hit);
	}

	void addDamageTaken(int amount)
	{
		outgoing.taken += amount;
	}

	void addHeal(HealthWatcher.Heal heal)
	{
		switch (heal.type)
		{
			case FOOD:
				outgoing.food += heal.amount;
				outgoing.foodOverheal += heal.overheal;
				break;
			case LIFESTEAL:
				outgoing.lifesteal += heal.amount;
				outgoing.lifestealOverheal += heal.overheal;
				break;
			case REGEN:
				outgoing.regen += heal.amount;
				break;
			case HEAL_OTHER:
				outgoing.healOther += heal.amount;
				break;
			default:
				break;
		}
	}

	/** Forgets what was collected but not sent yet. */
	void clear()
	{
		outgoing = new GoldenDpsUpdate();
	}

	/** Sends this tick's update (when there is something to share) and starts the next one. */
	void send()
	{
		GoldenDpsUpdate update = outgoing;
		outgoing = new GoldenDpsUpdate();
		if (update.isEmpty() || !config.shareWithParty() || !party.inParty())
		{
			return;
		}
		update.world = client.getWorld();
		update.name = Names.localName(client);
		partyService.send(update);
	}

	/** Adds a party member's update to the fight. */
	void receive(GoldenDpsUpdate update)
	{
		PartyMember local = partyService.getLocalMember();
		if (local != null && update.getMemberId() == local.getMemberId())
		{
			return;
		}
		party.pluginSeen(update.getMemberId());
		String name = senderName(update);
		if (name == null || name.equals(Names.localName(client)))
		{
			return;
		}

		long now = System.currentTimeMillis();
		party.reporterSeen(name, now);
		boolean together = !config.othersOnlyTogether() || party.isInstanceMate(name);
		boolean counted = together;
		if (update.hits != null)
		{
			for (GoldenDpsUpdate.Hit hit : update.hits)
			{
				boolean boss = hit.boss || bosses.isBoss(hit.npcId, hit.npcName);
				// away from you, only hits on a boss you can see count
				if (!together && !(boss && npcInScene(update.world, hit) != null))
				{
					continue;
				}
				counted = true;
				String source = hit.source == null || hit.source.length() > MAX_SOURCE_LENGTH ? null : hit.source;
				tracker.playerHit(now, name, PlayerKind.PARTY, partyTargetKey(update.world, hit, name), hit.npcId,
					hit.npcName, boss, hit.amount, hit.miss, source);
			}
		}
		Fight current = tracker.getCurrent();
		if (!counted && (current == null || !current.players.containsKey(name)))
		{
			return;
		}
		tracker.damageTaken(now, name, PlayerKind.PARTY, update.taken);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.FOOD, update.food, update.foodOverheal);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.LIFESTEAL, update.lifesteal, update.lifestealOverheal);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.REGEN, update.regen);
		tracker.heal(now, name, PlayerKind.PARTY, HealType.HEAL_OTHER, update.healOther);
	}

	/** The name in the update, or the member's party display name when the update has none. */
	private String senderName(GoldenDpsUpdate update)
	{
		String name = Names.clean(update.name);
		if (name != null)
		{
			return name;
		}
		PartyMember member = partyService.getMemberById(update.getMemberId());
		return member == null ? null : Names.clean(member.getDisplayName());
	}

	/** The NPC in your scene that a party member hit, or null when you cannot see it. */
	private NPC npcInScene(int world, GoldenDpsUpdate.Hit hit)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (world != client.getWorld() || wv == null)
		{
			return null;
		}
		NPC npc = wv.npcs().byIndex(hit.npcIndex);
		return npc != null && npc.getId() == hit.npcId ? npc : null;
	}

	/** Your key for the NPC when it is the same one you see, otherwise a key of its own. */
	private String partyTargetKey(int world, GoldenDpsUpdate.Hit hit, String sender)
	{
		NPC npc = npcInScene(world, hit);
		WorldPoint pos = npc == null ? null : npc.getWorldLocation();
		if (pos != null)
		{
			matcher.seen(TargetMatcher.key(world, hit.npcIndex), pos.getX(), pos.getY(), pos.getPlane());
		}
		return matcher.partyKey(client.getWorld(), world, hit.npcIndex, hit.x, hit.y, hit.plane, sender);
	}
}
