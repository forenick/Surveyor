package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.screen.JournalRefresh.View;

import static org.junit.jupiter.api.Assertions.assertEquals;

// made up sessions, ids = start minute
class JournalRefreshTest {
	private static final long MINUTE = 60_000;

	private static JournalEntry entry(long session, int shaft) {
		return EntryBuilder.entry().sessionId(session * MINUTE).enteredAt(session * MINUTE + shaft * MINUTE).build();
	}

	// sessions oldest first, each a shaft count
	private static List<JournalEntry> entries(int... shafts) {
		List<JournalEntry> list = new ArrayList<>();
		for (int s = 0; s < shafts.length; s++) {
			for (int i = 0; i < shafts[s]; i++) list.add(entry(s * 100L, i));
		}
		return list;
	}

	private static List<SessionSummary> sessions(List<JournalEntry> entries) {
		return JournalStats.sessions(entries);
	}

	private static List<JournalEntry> plus(List<JournalEntry> entries, JournalEntry... more) {
		List<JournalEntry> list = new ArrayList<>(entries);
		list.addAll(List.of(more));
		return list;
	}

	// 1st session ever, no shaft yet
	@Test
	void startedWithNoShaftsAtAll() {
		assertEquals(new View(0, -1, false, false, true), JournalRefresh.opened(List.of(), 5 * MINUTE));
		assertEquals(new View(0, -1, false, false, false), JournalRefresh.opened(List.of(), null));
		View view = new View(0, -1, false, false, false);
		assertEquals(new View(0, -1, false, false, true), JournalRefresh.after(List.of(), List.of(), view, 5 * MINUTE, true));
		assertEquals(new View(0, -1, false, false, true), JournalRefresh.after(List.of(), List.of(), view, 5 * MINUTE, false));
	}

	@Test
	void openedSessionFoundByIdWhenANewOneStarts() {
		List<JournalEntry> old = entries(3, 2);
		List<SessionSummary> before = sessions(old);
		// viewing the older one (index 1), 2nd shaft
		View view = new View(1, 1, true, false, false);
		List<SessionSummary> after = sessions(plus(old, entry(200, 0)));
		View next = JournalRefresh.after(before, after, view, 200 * MINUTE, false);
		assertEquals(new View(2, 1, true, true, false), next);
	}

	@Test
	void newestShaftFollowsTheGrowingSession() {
		List<JournalEntry> old = entries(2, 3);
		List<SessionSummary> before = sessions(old);
		View view = new View(0, 2, false, true, false);
		List<SessionSummary> after = sessions(plus(old, entry(100, 3)));
		assertEquals(new View(0, 3, false, true, false), JournalRefresh.after(before, after, view, 100 * MINUTE, true));
	}

	@Test
	void olderShaftStaysPicked() {
		List<JournalEntry> old = entries(2, 3);
		List<SessionSummary> before = sessions(old);
		View view = new View(0, 0, false, true, false);
		List<SessionSummary> after = sessions(plus(old, entry(100, 3)));
		assertEquals(new View(0, 0, false, true, false), JournalRefresh.after(before, after, view, 100 * MINUTE, true));
	}

	@Test
	void sessionTabGoesNoneThenStartedThenLive() {
		List<JournalEntry> old = entries(2);
		List<SessionSummary> before = sessions(old);
		View none = JournalRefresh.opened(before, null);
		assertEquals(new View(0, 1, false, false, false), none);
		// block hit starts a session, no shaft yet
		View started = JournalRefresh.after(before, before, none, 100 * MINUTE, true);
		assertEquals(new View(0, 1, false, false, true), started);
		List<SessionSummary> after = sessions(plus(old, entry(100, 0)));
		assertEquals(new View(0, 0, false, true, false), JournalRefresh.after(before, after, started, 100 * MINUTE, true));
	}

	@Test
	void endedSessionStaysOpenOnTheSessionTab() {
		List<SessionSummary> before = sessions(entries(2, 3));
		View live = new View(0, 1, false, true, false);
		assertEquals(new View(0, 1, true, false, false), JournalRefresh.after(before, before, live, null, true));
	}

	@Test
	void fileLoadingLateKeepsTheTab() {
		List<SessionSummary> empty = List.of();
		View opened = JournalRefresh.opened(empty, null);
		assertEquals(new View(0, -1, false, false, false), opened);
		List<SessionSummary> loaded = sessions(entries(2, 4));
		// overview, not the session tab: stays there, newest picked for later
		assertEquals(new View(0, 3, false, false, false), JournalRefresh.after(empty, loaded, opened, null, false));
		// session tab, the loaded newest session is the one going on
		assertEquals(new View(0, 3, false, true, false), JournalRefresh.after(empty, loaded, opened, 100 * MINUTE, true));
	}

	@Test
	void olderSessionsLoadedBehindKeepTheIndex() {
		List<JournalEntry> live = new ArrayList<>(List.of(entry(500, 0), entry(500, 1)));
		List<SessionSummary> before = sessions(live);
		View view = new View(0, 0, true, false, false);
		List<JournalEntry> loaded = new ArrayList<>(entries(3, 2));
		loaded.addAll(live);
		assertEquals(new View(0, 0, true, false, false), JournalRefresh.after(before, sessions(loaded), view, null, false));
	}
}
