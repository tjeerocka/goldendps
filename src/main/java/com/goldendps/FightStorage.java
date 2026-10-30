package com.goldendps;

import com.goldendps.model.BossRecords;
import com.goldendps.model.Fight;
import com.goldendps.model.FightTracker;
import com.goldendps.model.HistoryStore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.callback.ClientThread;

/**
 * Loads and saves the fight history and personal bests of the logged-in account.
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
	private boolean recordsLoaded;
	private final List<BossRecords.Kill> pendingKills = new ArrayList<>();
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
	void loadAccount(long hash, boolean includeRegen)
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
		recordsLoaded = false;
		pendingKills.clear();
		killCompares.clear();
		executor.execute(() ->
		{
			try
			{
				List<Fight> loaded = store.load(hash);
				BossRecords saved = store.loadRecords(hash);
				clientThread.invokeLater(() -> loaded(hash, loaded, saved, includeRegen));
			}
			catch (Exception ex)
			{
				log.warn("Could not load fight history", ex);
			}
		});
	}

	private void loaded(long hash, List<Fight> loaded, BossRecords saved, boolean includeRegen)
	{
		if (account != hash)
		{
			return;
		}
		if (saved == null)
		{
			saved = new BossRecords();
			saved.seed(loaded, includeRegen);
		}
		records = saved;
		recordsLoaded = true;
		for (BossRecords.Kill k : pendingKills)
		{
			killCompares.put(k.fightId, records.add(k));
		}
		pendingKills.clear();
		saveRecords();

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

	void fightEnded(Fight fight, boolean includeRegen)
	{
		dirty = true;
		save();
		BossRecords.Kill kill = BossRecords.record(fight, includeRegen);
		if (kill == null || account == -1)
		{
			return;
		}
		kill.fightId = fight.id;
		if (!recordsLoaded)
		{
			pendingKills.add(kill);
			return;
		}
		killCompares.put(fight.id, records.add(kill));
		saveRecords();
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

	List<BossRecords.Record> personalBests()
	{
		return records.list();
	}

	void resetRecord(String boss, boolean party)
	{
		records.remove(boss, party);
		saveRecords();
	}

	private void saveRecords()
	{
		if (account == -1 || !recordsLoaded)
		{
			return;
		}
		long acc = account;
		BossRecords copy = records.copy();
		executor.execute(() ->
		{
			try
			{
				store.saveRecords(acc, copy);
			}
			catch (Exception ex)
			{
				log.warn("Could not save personal bests", ex);
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
