package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PartyLeaderTest
{
	@Test
	public void aloneInANewPartyYouLead()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		assertFalse("wait for an answer first", l.tick(2000, Collections.singletonList(50L)));
		assertFalse(l.isLeader());
		assertTrue(l.tick(4000, Collections.singletonList(50L)));
		assertTrue(l.isLeader());
	}

	@Test
	public void joiningAPartyTheLeaderTellsYou()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		l.announce(20, 20);
		assertEquals(20, l.getLeader());
		assertFalse(l.isLeader());
		// a later claim from someone who is not the leader is ignored
		l.announce(30, 30);
		assertEquals(20, l.getLeader());
		// the leader can hand it over
		l.announce(20, 50);
		assertTrue(l.isLeader());
	}

	@Test
	public void nobodyAnswersLowestGoldenDpsIdLeads()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		l.sawPlugin(40);
		// 10 is in the party but has no GoldenDPS: cannot lead
		assertTrue(l.tick(5000, Arrays.asList(10L, 40L, 50L)));
		assertEquals(40, l.getLeader());
	}

	@Test
	public void leaderLeavesNextOneIsPicked()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		l.announce(20, 20);
		l.sawPlugin(30);
		l.memberLeft(20, Arrays.asList(30L, 50L));
		assertEquals(30, l.getLeader());
	}

	@Test
	public void onlyTheLeaderCanKickYou()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		l.announce(20, 20);
		assertFalse("not the leader", l.acceptKick(30, 50));
		assertFalse("kick for someone else", l.acceptKick(20, 60));
		assertTrue(l.acceptKick(20, 50));
	}

	@Test
	public void theLeaderCannotKickThemself()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		l.tick(5000, Collections.singletonList(50L));
		assertFalse(l.acceptKick(50, 50));
	}

	@Test
	public void leavingForgetsEverything()
	{
		PartyLeader l = new PartyLeader();
		l.joined(50, 1000);
		l.tick(5000, Collections.singletonList(50L));
		l.left();
		assertFalse(l.isJoined());
		assertFalse(l.isLeader());
		assertFalse(l.hasPlugin(50));
	}
}
