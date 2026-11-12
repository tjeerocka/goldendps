package com.goldendps;

import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HistoryStore;
import com.goldendps.model.MonsterStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

/**
 * Loads and saves the fight history, personal bests and monster stats of the logged-in account.
 * State is changed on the client thread; files are written on the executor from copies.
 */
@Slf4j
class FightStorage
{
	private static final long SAVE_EVERY_MS = 30_000;

	private final FightTracker tracker;
	private final HistoryStore store;
	private final ExecutorService executor;
	private final ClientThread clientThread;
	private final Runnable onLoaded;

	private long account = -1;
	private boolean dirty;
	private long lastSaveMs;
	private BossRecords records = new BossRecords();
	private MonsterStats monsters = new MonsterStats();
	/** Personal bests and monster stats are loaded. */
	private boolean statsLoaded;
	/** Fights that ended and monsters that died while the stats were loading. */
	private final List<Runnable> pendingStats = new ArrayList<>();
	private final Map<String, BossRecords.Compare> killCompares = new HashMap<>();

	FightStorage(FightTracker tracker, HistoryStore store, ExecutorService executor, ClientThread clientThread,
		Runnable onLoaded)
	{
		this.tracker = tracker;
		this.store = store;
		this.executor = executor;
		this.clientThread = clientThread;
		this.onLoaded = onLoaded;
	}

	/** Switches to this account and loads its data (once per account). */
	void loadAccount(long hash)
	{
		if (hash == -1 || hash == account)
		{
			return;
		}
		if (account != -1)
		{
			save();
		}
		tracker.endNow(System.currentTimeMillis(), "Account changed");
		tracker.clearHistory();
		account = hash;
		records = new BossRecords();
		monsters = new MonsterStats();
		statsLoaded = false;
		pendingStats.clear();
		killCompares.clear();
		executor.execute(() ->
		{
			try
			{
				List<Fight> loaded = store.load(hash);
				BossRecords saved = store.loadRecords(hash);
				MonsterStats savedMonsters = store.loadMonsters(hash);
				clientThread.invokeLater(() -> loaded(hash, loaded, saved, savedMonsters));
			}
			catch (Exception ex)
			{
				log.warn("Could not load fight history", ex);
			}
		});
	}

	private void loaded(long hash, List<Fight> loaded, BossRecords saved, MonsterStats savedMonsters)
	{
		if (account != hash)
		{
			return;
		}
		if (saved == null)
		{
			saved = new BossRecords();
			saved.seed(loaded);
		}
		records = saved;
		monsters = savedMonsters;
		statsLoaded = true;
		pendingStats.forEach(Runnable::run);
		pendingStats.clear();
		saveStats();

		// fights that ended while loading are newer than the loaded ones
		List<Fight> all = new ArrayList<>(tracker.getHistory());
		all.addAll(loaded);
		tracker.setHistory(all);
		onLoaded.run();
	}

	void unload()
	{
		account = -1;
	}

	void fightEnded(Fight fight)
	{
		dirty = true;
		save();
		if (account == -1)
		{
			return;
		}
		addToStats(() -> addRecord(fight));
	}

	/** A monster died in this fight: it shows in the monster stats right away. */
	void monsterDied(Fight fight, String targetKey)
	{
		if (account != -1)
		{
			addToStats(() -> monsters.addKill(fight, targetKey));
		}
	}

	/** Runs now, or after loading when the stats are not loaded yet; then saves them. */
	private void addToStats(Runnable change)
	{
		if (!statsLoaded)
		{
			pendingStats.add(change);
			return;
		}
		change.run();
		saveStats();
	}

	private void addRecord(Fight fight)
	{
		BossRecords.Kill kill = BossRecords.record(fight);
		if (kill != null)
		{
			kill.fightId = fight.id;
			killCompares.put(fight.id, records.add(kill));
		}
	}

	/** The game told the real time of a fight that already ended: its personal best gets that time. */
	void gameTimeChanged(Fight fight)
	{
		dirty = true;
		if (account != -1)
		{
			addToStats(() -> retimeRecord(fight));
		}
	}

	private void retimeRecord(Fight fight)
	{
		BossRecords.Compare compare = killCompares.get(fight.id);
		BossRecords.Kill kill = BossRecords.record(fight);
		if (compare != null && kill != null)
		{
			records.retime(compare, kill.timeMs);
		}
	}

	/** Saves a running fight now and then, so a crash loses little. */
	void tick(long now)
	{
		if (tracker.getCurrent() != null)
		{
			dirty = true;
		}
		if (dirty && now - lastSaveMs >= SAVE_EVERY_MS)
		{
			save();
		}
	}

	void save()
	{
		if (account == -1)
		{
			return;
		}
		long acc = account;
		List<Fight> snapshot = snapshot();
		dirty = false;
		lastSaveMs = System.currentTimeMillis();
		executor.execute(() -> write(acc, snapshot));
	}

	/** Final save on client shutdown; returns the write to wait for, or null. */
	Future<?> saveNow()
	{
		if (account == -1)
		{
			return null;
		}
		long acc = account;
		List<Fight> snapshot = snapshot();
		return executor.submit(() -> write(acc, snapshot));
	}

	BossRecords.Record record(String boss, boolean party)
	{
		return records.get(boss, party);
	}

	BossRecords.Compare compare(String fightId)
	{
		return killCompares.get(fightId);
	}

	/** Every monster, raid and Barrows run with its stats and personal bests, last kill first. */
	List<MonsterStats.Entry> monsterEntries()
	{
		return monsters.entries(records.list());
	}

	/** Deletes the stats and personal bests of one monster, raid or Barrows run. */
	void resetMonster(String name)
	{
		monsters.remove(name);
		records.remove(name, false);
		records.remove(name, true);
		saveStats();
	}

	/** Deletes the stats and personal bests of everything. */
	void resetAllMonsters()
	{
		monsters = new MonsterStats();
		records = new BossRecords();
		killCompares.clear();
		saveStats();
	}

	private void saveStats()
	{
		if (account == -1 || !statsLoaded)
		{
			return;
		}
		long acc = account;
		BossRecords recordsCopy = records.copy();
		MonsterStats monstersCopy = monsters.copy();
		executor.execute(() ->
		{
			try
			{
				store.saveRecords(acc, recordsCopy);
				store.saveMonsters(acc, monstersCopy);
			}
			catch (Exception ex)
			{
				log.warn("Could not save personal bests and monster stats", ex);
			}
		});
	}

	private List<Fight> snapshot()
	{
		List<Fight> out = new ArrayList<>();
		Fight current = tracker.getCurrent();
		if (current != null && current.knownDamage() > 0)
		{
			Fight unfinished = current.copy();
			unfinished.endMs = Math.max(unfinished.startMs, unfinished.lastActivityMs);
			unfinished.endReason = "Not finished (saved)";
			out.add(unfinished);
		}
		for (Fight f : tracker.getHistory())
		{
			out.add(f.copy());
		}
		return out;
	}

	private void write(long acc, List<Fight> fights)
	{
		try
		{
			store.save(acc, fights, System.currentTimeMillis());
		}
		catch (Exception ex)
		{
			log.warn("Could not save fight history", ex);
		}
	}
}
