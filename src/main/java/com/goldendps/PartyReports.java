package com.goldendps;

import com.goldendps.model.AutoReports;
import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightReport;
import com.goldendps.model.KillCounts;
import com.goldendps.model.MeterMode;
import com.goldendps.model.ReportFormatter;
import java.util.List;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyChatMessage;

/**
 * Sends the top players of a fight to the RuneLite party chat, by hand or (for the leader)
 * after every boss kill. Plugins may not write to the game chat, so the party chat is the only
 * way to share a report. Client thread only.
 */
class PartyReports
{
	private final GoldenDpsConfig config;
	private final PartyService partyService;
	private final PartyManager party;
	private final ChatNotifier chat;
	private final FightStorage storage;
	private final KillCounts killCounts;
	private final AutoReports autoReports = new AutoReports();

	PartyReports(GoldenDpsConfig config, PartyService partyService, PartyManager party, ChatNotifier chat,
		FightStorage storage, KillCounts killCounts)
	{
		this.config = config;
		this.partyService = partyService;
		this.party = party;
		this.chat = chat;
		this.storage = storage;
		this.killCounts = killCounts;
	}

	void fightEnded(Fight fight, int tick)
	{
		if (config.autoBossReport())
		{
			autoReports.fightEnded(fight, tick, config.countRegen());
		}
	}

	/** The leader reports boss kills and Barrows runs to the party by itself. */
	void sendDue(int tick)
	{
		for (Fight fight : autoReports.due(tick))
		{
			if (config.autoBossReport() && party.isLeader())
			{
				send(fight, ReportKind.BOSS, false, System.currentTimeMillis());
			}
		}
	}

	void clear()
	{
		autoReports.clear();
	}

	/** @param session the fight is the session total */
	void send(Fight fight, ReportKind kind, boolean session, long now)
	{
		if (fight == null)
		{
			chat.info("nothing to report yet.");
			return;
		}
		if (kind == ReportKind.BOSS && !fight.hasBossTarget())
		{
			chat.info("no boss in this fight.");
			return;
		}
		if (!party.inParty())
		{
			chat.info("join a party to report to it.");
			return;
		}
		for (String line : lines(fight, kind, session, now))
		{
			partyService.send(new PartyChatMessage(line));
		}
	}

	private List<String> lines(Fight fight, ReportKind kind, boolean session, long now)
	{
		boolean bossOnly = kind == ReportKind.BOSS;
		// a finished raid or Barrows run is reported as a whole, with its full time:
		// "Chambers of Xeric complete: 31:30 - total 52,000 (27.5/s) - KC 21"
		boolean completed = bossOnly && fight.isCompleteRaid();
		FightReport report = FightReport.of(fight, bossOnly && !completed, config.countRegen(), now);
		String title = session ? "Session" : completed ? "" : report.getTitle();
		String label = completed ? fight.raid + (fight.run ? " run complete:" : " complete:") : kind.label;
		MeterMode mode = kind == ReportKind.HEALING ? MeterMode.HEALING : MeterMode.DAMAGE;
		List<String> lines = ReportFormatter.lines(report, mode, label, title, ReportFormatter.DEFAULT_TOP);
		if (bossOnly && !session)
		{
			int kc = killCounts.near(completed ? fight.raid : report.getTitle(), fight.endMs > 0 ? fight.endMs : now);
			String pb = BossRecords.meterTag(null, storage.compare(fight.id));
			lines.set(0, lines.get(0) + (kc > 0 ? " - KC " + kc : "") + (pb != null ? " - " + pb : ""));
		}
		return lines;
	}
}
