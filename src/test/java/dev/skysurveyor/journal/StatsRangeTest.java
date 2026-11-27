package dev.skysurveyor.journal;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.Patches;
import dev.skysurveyor.data.Patches.Patch;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

// made up sessions, 1 per day
class StatsRangeTest {
	private static final long DAY = 24 * 3_600_000L;

	private static JournalEntry entry(long session, long entered) {
		return EntryBuilder.entry().sessionId(session).enteredAt(entered).build();
	}

	private static List<SessionSummary> tenDays() {
		List<JournalEntry> entries = new ArrayList<>();
		for (int day = 0; day < 10; day++) {
			entries.add(entry(day * DAY, day * DAY));
			entries.add(entry(day * DAY, day * DAY + 120_000));
		}
		return JournalStats.sessions(entries);
	}

	@Test
	void picksWholeSessions() {
		List<SessionSummary> sessions = tenDays();
		long now = 9 * DAY + 3_600_000;
		assertSame(sessions, StatsRange.ALL_TIME.sessions(sessions, now));
		// days 3 to 9 started in the last 7 days
		assertEquals(7, StatsRange.LAST_7_DAYS.sessions(sessions, now).size());
		assertEquals(3 * DAY, StatsRange.LAST_7_DAYS.sessions(sessions, now).getLast().start());
		List<SessionSummary> recent = StatsRange.LAST_5_SESSIONS.sessions(sessions, now);
		assertEquals(5, recent.size());
		assertSame(sessions.getFirst(), recent.getFirst());
	}

	@Test
	void entriesOldestFirst() {
		List<SessionSummary> recent = StatsRange.LAST_5_SESSIONS.sessions(tenDays(), 0);
		List<JournalEntry> entries = StatsRange.flat(recent, SessionSummary::entries);
		assertEquals(10, entries.size());
		assertEquals(5 * DAY, entries.getFirst().enteredAt());
		assertEquals(9 * DAY + 120_000, entries.getLast().enteredAt());
	}

	// config.json keeps these, never rename
	@Test
	void savedNamesStay() {
		assertEquals(StatsRange.ALL_TIME, StatsRange.ofSaved("all_time"));
		assertEquals(StatsRange.LAST_7_DAYS, StatsRange.ofSaved("last_7_days"));
		assertEquals(StatsRange.LAST_5_SESSIONS, StatsRange.ofSaved("last_5_sessions"));
		assertEquals(StatsRange.SINCE_PATCH, StatsRange.ofSaved("since_patch"));
		assertEquals(StatsRange.SINCE_PATCH, StatsRange.ofSaved("SINCE_PATCH"));
		assertNull(StatsRange.ofSaved("Since patch 0.27.3"), "never the display name");
		assertNull(StatsRange.ofSaved("last_30_days"));
		assertNull(StatsRange.ofSaved(null));
		for (StatsRange range : StatsRange.values()) {
			assertEquals("\"" + range.name().toLowerCase(Locale.ROOT) + "\"", new Gson().toJson(range), "gson writes what ofSaved reads");
		}
	}

	@Test
	void patchFileParses() {
		Patch patch = Patches.defaults().newest();
		assertEquals("0.27.3", patch.name());
		// thread post time, 2026-11-17 10:42:53 -05:00
		assertEquals(Instant.parse("2026-11-17T15:42:53Z").toEpochMilli(), patch.startMillis());
		// "patch", else it reads like a mod version
		assertEquals("Since patch 0.27.3", StatsRange.SINCE_PATCH.displayName());
		assertEquals("Last 5 sessions", StatsRange.LAST_5_SESSIONS.displayName());
		assertEquals("All-time", StatsRange.ALL_TIME.displayName());
	}

	// cut at the start of day 5: before, on and after
	@Test
	void sinceThePatch() {
		Patches patches = Patches.parse(List.of("# comment", "0.27.3 1970-01-06"));
		assertEquals(5 * DAY, patches.newest().startMillis());
		List<SessionSummary> since = StatsRange.SINCE_PATCH.sessions(tenDays(), 0, patches);
		assertEquals(5, since.size());
		assertEquals(5 * DAY, since.getLast().start(), "a session starting right at the cut counts");
		assertEquals(9 * DAY, since.getFirst().start());

		Patches exact = Patches.parse(List.of("0.27.3 1970-01-06T00:00:00.001Z"));
		assertEquals(4, StatsRange.SINCE_PATCH.sessions(tenDays(), 0, exact).size(), "1 ms after the session start");
	}

	@Test
	void newerPatchLineWins() {
		Patches patches = Patches.parse(List.of("0.28.0 1970-01-09", "0.27.3 1970-01-06"));
		assertEquals("0.28.0", patches.newest().name());
		assertEquals("Since patch 0.28.0", StatsRange.SINCE_PATCH.displayName(patches));
		assertEquals(2, StatsRange.SINCE_PATCH.sessions(tenDays(), 0, patches).size());
	}

	@Test
	void nothingAfterThePatch() {
		Patches patches = Patches.parse(List.of("0.27.3 1970-02-01"));
		assertEquals(List.of(), StatsRange.SINCE_PATCH.sessions(tenDays(), 0, patches));
	}
}
