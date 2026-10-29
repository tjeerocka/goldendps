package com.goldendps;

import com.goldendps.model.PartyLeader;
import com.goldendps.model.PartyNotices;
import com.goldendps.model.RaidParty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;

/**
 * Everything about the RuneLite party: joining and leaving, the GoldenDPS leader and kick,
 * chat notices, the automatic raid party, and which members are in your instance.
 * All methods run on the client thread.
 */
@Singleton
class PartyManager
{
	/** A member counts as running GoldenDPS for this long after their last update. */
	private static final long REPORTER_MS = 120_000;
	/** Kicked members are hidden at once; the party server may list them a little longer. */
	private static final long KICK_HIDE_MS = 120_000;
	/** Nameless, logged-out members older than this are stale connections. */
	private static final long STALE_MEMBER_MS = 15_000;

	private final Client client;
	private final PartyService partyService;
	private final GoldenDpsConfig config;
	private final ChatNotifier chat;
	private final RaidPartyDetector raidPartyDetector;

	private final PartyLeader leader = new PartyLeader();
	private final RaidParty raidParty = new RaidParty();
	private final PartyNotices notices = new PartyNotices();
	private final Map<String, Long> reporters = new HashMap<>();
	private final Map<Long, Long> kicked = new HashMap<>();
	private final Map<Long, Long> firstSeen = new HashMap<>();
	private final Set<String> instanceMates = new HashSet<>();
	private long localMemberId;

	@Inject
	PartyManager(Client client, PartyService partyService, GoldenDpsConfig config, ChatNotifier chat,
		RaidPartyDetector raidPartyDetector)
	{
		this.client = client;
		this.partyService = partyService;
		this.config = config;
		this.chat = chat;
		this.raidPartyDetector = raidPartyDetector;
	}

	boolean inParty()
	{
		return partyService.isInParty() && partyService.getLocalMember() != null;
	}

	void join(String name)
	{
		if (partyService.isInParty())
		{
			return;
		}
		changeParty(name);
		chat.info("joined party \"" + name + "\".");
	}

	/** A random name for a new party, or null when you are in a party already. */
	String randomName()
	{
		return partyService.isInParty() ? null : partyService.generatePassphrase().replace('-', ' ');
	}

	void leave()
	{
		if (partyService.isInParty())
		{
			changeParty(null);
			chat.info("left the party.");
		}
	}

	void kick(long memberId, String name)
	{
		if (!leader.isLeader() || !partyService.isInParty())
		{
			chat.info("only the party leader can kick.");
			return;
		}
		partyService.send(new GoldenDpsPartyMessage(GoldenDpsPartyMessage.KICK, leader.getLeader(), memberId));
		kicked.put(memberId, System.currentTimeMillis());
		notices.kicked(memberId);
		chat.kicked("removed " + name + " from the party.");
	}

	private void changeParty(String passphrase)
	{
		partyService.changeParty(passphrase);
		leader.left();
	}

	/** A GoldenDPS message arrived from this member. */
	void pluginSeen(long memberId)
	{
		leader.sawPlugin(memberId);
	}

	/** A GoldenDPS update arrived from this player. */
	void reporterSeen(String name, long now)
	{
		reporters.put(name, now);
	}

	boolean isReporter(String name, long now)
	{
		Long last = reporters.get(name);
		return last != null && now - last <= REPORTER_MS;
	}

	/** The member was seen in your instance since you entered it. */
	boolean isInstanceMate(String name)
	{
		WorldView wv = client.getTopLevelWorldView();
		return wv != null && wv.isInstance() && instanceMates.contains(name);
	}

	void onMessage(GoldenDpsPartyMessage m)
	{
		PartyMember local = partyService.getLocalMember();
		if (m.type == null || local == null || m.getMemberId() == local.getMemberId())
		{
			return;
		}
		switch (m.type)
		{
			case GoldenDpsPartyMessage.HI:
				leader.announce(m.getMemberId(), m.leader);
				if (leader.isLeader())
				{
					send(GoldenDpsPartyMessage.LEADER);
				}
				break;
			case GoldenDpsPartyMessage.LEADER:
				leader.announce(m.getMemberId(), m.leader);
				break;
			case GoldenDpsPartyMessage.KICK:
				onKick(m);
				break;
			default:
				break;
		}
	}

	private void onKick(GoldenDpsPartyMessage m)
	{
		leader.sawPlugin(m.getMemberId());
		String by = memberName(m.getMemberId());
		String bySuffix = by != null ? " by " + by + "." : ".";
		if (leader.acceptKick(m.getMemberId(), m.target))
		{
			changeParty(null);
			chat.kicked("you were removed from the party" + bySuffix);
		}
		else if (m.leader == m.getMemberId() && m.leader == leader.getLeader())
		{
			notices.kicked(m.target);
			String who = memberName(m.target);
			if (who != null)
			{
				chat.kicked(who + " was removed from the party" + bySuffix);
			}
		}
	}

	void onMemberLeft(long memberId)
	{
		leader.memberLeft(memberId, memberIds());
	}

	/** You logged out: tell again who is in the party after the next login. */
	void loggedOut()
	{
		notices.reset();
	}

	void tick(long now)
	{
		updateInstanceMates();
		autoRaidParty();
		updateMembership(now);
	}

	private void updateMembership(long now)
	{
		PartyMember local = partyService.isInParty() ? partyService.getLocalMember() : null;
		if (local == null)
		{
			if (leader.isJoined())
			{
				leader.left();
			}
			localMemberId = 0;
			notices.reset();
			return;
		}
		if (local.getMemberId() != localMemberId)
		{
			// RuneLite gives you a new member id in every party
			localMemberId = local.getMemberId();
			leader.joined(localMemberId, now);
			send(GoldenDpsPartyMessage.HI);
		}
		if (leader.tick(now, memberIds()) && leader.isLeader())
		{
			send(GoldenDpsPartyMessage.LEADER);
		}

		Map<Long, String> named = new HashMap<>();
		for (PartyMember m : partyService.getMembers())
		{
			String n = Names.clean(m.getDisplayName());
			if (n != null && m.getMemberId() != localMemberId && !kicked.containsKey(m.getMemberId()))
			{
				named.put(m.getMemberId(), n);
			}
		}
		for (PartyNotices.Notice n : notices.tick(now, localMemberId, named))
		{
			if (n.joined)
			{
				chat.joined(n.name + " joined the party.");
			}
			else
			{
				chat.left(n.name + " left the party.");
			}
		}
		List<String> others = notices.welcome(now);
		if (others != null)
		{
			chat.joined(PartyNotices.welcomeText(partyService.getPartyPassphrase(), others));
		}
	}

	private void updateInstanceMates()
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null || !wv.isInstance() || !partyService.isInParty())
		{
			instanceMates.clear();
			return;
		}
		Set<String> party = new HashSet<>();
		for (PartyMember m : partyService.getMembers())
		{
			String n = Names.clean(m.getDisplayName());
			if (n != null)
			{
				party.add(n);
			}
		}
		for (Player p : wv.players())
		{
			String n = p == null ? null : Names.clean(p.getName());
			if (n != null && party.contains(n))
			{
				instanceMates.add(n);
			}
		}
	}

	private void autoRaidParty()
	{
		if (!config.autoRaidParty())
		{
			return;
		}
		RaidPartyDetector.Detected raid = raidPartyDetector.current();
		String current = partyService.isInParty() ? partyService.getPartyPassphrase() : null;
		RaidParty.Action action = raidParty.tick(raid.partyName, current);
		if (action.join != null)
		{
			changeParty(action.join);
			chat.info("joined the " + raid.raid + " party automatically (everyone in your raid with GoldenDPS).");
		}
		else if (action.leave)
		{
			changeParty(null);
			chat.info("left the raid party.");
		}
	}

	GoldenDpsPartyPanel.View view(String localName)
	{
		GoldenDpsPartyPanel.View v = new GoldenDpsPartyPanel.View();
		v.inParty = partyService.isInParty();
		if (!v.inParty)
		{
			return v;
		}
		v.code = partyService.getPartyPassphrase();
		v.youLead = leader.isLeader();
		v.auto = RaidParty.isAuto(v.code);

		long now = System.currentTimeMillis();
		kicked.values().removeIf(t -> now - t > KICK_HIDE_MS);
		firstSeen.keySet().retainAll(memberIds());
		PartyMember local = partyService.getLocalMember();
		for (PartyMember m : partyService.getMembers())
		{
			if (kicked.containsKey(m.getMemberId()))
			{
				continue;
			}
			GoldenDpsPartyPanel.Member row = new GoldenDpsPartyPanel.Member();
			row.id = m.getMemberId();
			row.you = local != null && m.getMemberId() == local.getMemberId();
			String name = Names.clean(m.getDisplayName());
			long seen = firstSeen.computeIfAbsent(m.getMemberId(), id -> now);
			if (!row.you && name == null && !m.isLoggedIn() && now - seen > STALE_MEMBER_MS)
			{
				continue;
			}
			row.name = name != null ? name : row.you ? localName : "(joining...)";
			row.online = m.isLoggedIn();
			row.leader = m.getMemberId() == leader.getLeader();
			row.hasPlugin = row.you || leader.hasPlugin(m.getMemberId()) || (name != null && isReporter(name, now));
			v.members.add(row);
		}
		v.members.sort((a, b) -> a.leader != b.leader ? (a.leader ? -1 : 1)
			: a.you != b.you ? (a.you ? -1 : 1) : a.name.compareToIgnoreCase(b.name));
		return v;
	}

	private String memberName(long memberId)
	{
		PartyMember m = partyService.getMemberById(memberId);
		return m == null ? null : Names.clean(m.getDisplayName());
	}

	private void send(String type)
	{
		if (inParty())
		{
			partyService.send(new GoldenDpsPartyMessage(type, leader.getLeader(), 0));
		}
	}

	private List<Long> memberIds()
	{
		List<Long> ids = new ArrayList<>();
		for (PartyMember m : partyService.getMembers())
		{
			ids.add(m.getMemberId());
		}
		return ids;
	}
}
