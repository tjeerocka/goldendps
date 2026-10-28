package com.goldendps.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import com.google.gson.Gson;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class HistoryStoreTest
{
	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Fight sampleFight()
	{
		FightTracker t = new FightTracker();
		t.playerHit(0, "Me", PlayerKind.LOCAL, "301:5", 8059, "Vorkath", true, 44, false);
		t.seenHit(0, "301:5", 8059, "Vorkath", true, 44);
		t.playerHit(600, "Friend", PlayerKind.PARTY, "301:5", 8059, "Vorkath", true, 30, false);
		t.seenHit(600, "301:5", 8059, "Vorkath", true, 30);
		t.seenHit(1200, "301:5", 8059, "Vorkath", true, 12);
		t.heal(1200, "Me", PlayerKind.LOCAL, HealType.FOOD, 22);
		t.damageTaken(1200, "Me", PlayerKind.LOCAL, 9);
		t.npcDied(3000, "301:5", true);
		return t.getHistory().get(0);
	}

	@Test
	public void saveAndLoadGivesTheSameNumbers() throws Exception
	{
		Path dir = folder.getRoot().toPath().resolve("goldendps");
		HistoryStore store = new HistoryStore(new Gson(), dir);
		store.save(12345L, Collections.singletonList(sampleFight()), 1000);

		assertTrue(Files.exists(dir.resolve("12345.json")));
		assertFalse("temp file is gone", Files.exists(dir.resolve("12345.json.tmp")));

		List<Fight> loaded = store.load(12345L);
		assertEquals(1, loaded.size());
		FightReport r = FightReport.of(loaded.get(0), false, false, 0);
		assertEquals(44, r.row("Me").damage);
		assertEquals(30, r.row("Friend").damage);
		assertEquals(12, r.row(FightReport.UNKNOWN_NAME).damage);
		assertEquals(22, r.row("Me").healing);
		assertEquals(9, r.row("Me").damageTaken);
		assertEquals(PlayerKind.LOCAL, r.row("Me").kind);
		assertEquals(3000, loaded.get(0).durationMs(0));
		assertEquals("Vorkath", loaded.get(0).title());
	}

	@Test
	public void accountsHaveTheirOwnFile() throws Exception
	{
		HistoryStore store = new HistoryStore(new Gson(), folder.getRoot().toPath());
		store.save(1L, Collections.singletonList(sampleFight()), 0);
		assertEquals(1, store.load(1L).size());
		assertTrue(store.load(2L).isEmpty());
	}

	@Test
	public void brokenFileIsKeptAndHistoryStartsEmpty() throws Exception
	{
		Path dir = folder.getRoot().toPath();
		Files.write(dir.resolve("7.json"), "{ not json".getBytes(StandardCharsets.UTF_8));
		HistoryStore store = new HistoryStore(new Gson(), dir);

		assertTrue(store.load(7L).isEmpty());
		try (Stream<Path> files = Files.list(dir))
		{
			assertTrue(files.anyMatch(p -> p.getFileName().toString().startsWith("7.broken-")));
		}
	}

	@Test
	public void trackerKeepsLoadedHistory() throws Exception
	{
		HistoryStore store = new HistoryStore(new Gson(), folder.getRoot().toPath());
		store.save(3L, Collections.singletonList(sampleFight()), 0);

		FightTracker t = new FightTracker();
		t.setHistory(store.load(3L));
		assertEquals(1, t.getHistory().size());
		assertEquals("Vorkath", t.getDisplayFight().title());
		// old fights are from another session: not in this session's total
		assertEquals(null, t.getSessionTotal(0));
	}
}
