package com.goldendps.model;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Saves fight history as JSON, one file per account: {@code <dir>/<accountHash>.json}.
 * Writes to a temp file first and then moves it, so a crash never leaves half a file.
 */
public class HistoryStore
{
	public static final int VERSION = 1;

	/** What is in the file. */
	public static class SaveFile
	{
		public int version = VERSION;
		public long accountHash;
		public long savedAt;
		/** Newest first. */
		public List<Fight> fights = new ArrayList<>();
	}

	private final Gson gson;
	private final Path dir;

	public HistoryStore(Gson gson, Path dir)
	{
		this.gson = gson;
		this.dir = dir;
	}

	public Path fileFor(long accountHash)
	{
		return dir.resolve(accountHash + ".json");
	}

	public void save(long accountHash, List<Fight> fights, long now) throws IOException
	{
		SaveFile data = new SaveFile();
		data.accountHash = accountHash;
		data.savedAt = now;
		data.fights = new ArrayList<>(fights);

		Files.createDirectories(dir);
		Path target = fileFor(accountHash);
		Path tmp = dir.resolve(accountHash + ".json.tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))
		{
			gson.toJson(data, w);
		}
		try
		{
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Personal bests, in their own file so they stay when old fights are dropped. */
	public void saveRecords(long accountHash, BossRecords records) throws IOException
	{
		Files.createDirectories(dir);
		Path target = dir.resolve(accountHash + ".records.json");
		Path tmp = dir.resolve(accountHash + ".records.json.tmp");
		try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8))
		{
			gson.toJson(records, w);
		}
		try
		{
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (AtomicMoveNotSupportedException e)
		{
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	/** Saved personal bests, or null when there are none yet (or the file is broken). */
	public BossRecords loadRecords(long accountHash) throws IOException
	{
		Path file = dir.resolve(accountHash + ".records.json");
		if (!Files.exists(file))
		{
			return null;
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			BossRecords data = gson.fromJson(r, BossRecords.class);
			if (data == null || data.byBoss == null)
			{
				return null;
			}
			data.byBoss.values().removeIf(x -> x == null || x.boss == null);
			return data;
		}
		catch (JsonParseException e)
		{
			return null;
		}
	}

	/** Saved fights, newest first. Empty list if there is no file or it can't be read. */
	public List<Fight> load(long accountHash) throws IOException
	{
		Path file = fileFor(accountHash);
		if (!Files.exists(file))
		{
			return new ArrayList<>();
		}
		try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8))
		{
			SaveFile data = gson.fromJson(r, SaveFile.class);
			if (data == null || data.fights == null)
			{
				return new ArrayList<>();
			}
			List<Fight> out = new ArrayList<>();
			for (Fight f : data.fights)
			{
				if (f != null && f.id != null && f.players != null && f.targets != null)
				{
					out.add(f);
				}
			}
			return out;
		}
		catch (JsonParseException e)
		{
			// Keep the broken file for the user, start fresh
			Files.move(file, dir.resolve(accountHash + ".broken-" + System.currentTimeMillis() + ".json"),
				StandardCopyOption.REPLACE_EXISTING);
			return new ArrayList<>();
		}
	}
}
