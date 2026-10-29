package com.goldendps;

import com.goldendps.model.RaidParty;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

/** Reads the raid party you are in from the game and turns it into a shared party name. */
class RaidPartyDetector
{
	/** The raid party you are in. */
	static final class Detected
	{
		final String raid;
		/** Null while the party name cannot be made yet (for example when you are alone). */
		final String partyName;

		Detected(String raid, String partyName)
		{
			this.raid = raid;
			this.partyName = partyName;
		}
	}

	/** A raid whose members all see the member names. */
	private static final class NamedRaid
	{
		final String name;
		final int statusVarbit;
		final int firstNameVarc;
		final int lastNameVarc;

		NamedRaid(String name, int statusVarbit, int firstNameVarc, int lastNameVarc)
		{
			this.name = name;
			this.statusVarbit = statusVarbit;
			this.firstNameVarc = firstNameVarc;
			this.lastNameVarc = lastNameVarc;
		}
	}

	private static final NamedRaid[] NAMED_RAIDS = {
		new NamedRaid("ToB", VarbitID.TOB_CLIENT_PARTYSTATUS, VarClientID.TOB_CLIENT_NAME0, VarClientID.TOB_CLIENT_NAME4),
		new NamedRaid("ToA", VarbitID.TOA_CLIENT_PARTYSTATUS, VarClientID.TOA_CLIENT_NAME0, VarClientID.TOA_CLIENT_NAME7),
		new NamedRaid("Archive", VarbitID.TFA_CLIENT_PARTYSTATUS, VarClientID.TFA_CLIENT_NAME0, VarClientID.TFA_CLIENT_NAME7),
	};

	private final Client client;

	@Inject
	RaidPartyDetector(Client client)
	{
		this.client = client;
	}

	/** Client thread only. */
	Detected current()
	{
		for (NamedRaid r : NAMED_RAIDS)
		{
			if (client.getVarbitValue(r.statusVarbit) > 0)
			{
				List<String> names = new ArrayList<>();
				for (int v = r.firstNameVarc; v <= r.lastNameVarc; v++)
				{
					names.add(client.getVarcStrValue(v));
				}
				return new Detected(r.name, RaidParty.partyName(r.name, client.getWorld(), names));
			}
		}
		// CoX has no member list, but every member gets the same raid party id
		return new Detected("CoX", RaidParty.coxPartyName(client.getWorld(),
			client.getVarpValue(VarPlayerID.RAIDS_PARTY_GROUPHOLDER), client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE)));
	}
}
