package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// what LiveJournal does once each file loaded
class ResumeTest {
	private static final long MINUTE = 60_000;
	private static final long GAP = Journal.SESSION_GAP_MILLIS;

	private final List<BreakEntry> breaks = new ArrayList<>();
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> true, e -> {
	});
	private final Breaks tracker = new Breaks(() -> true, journal::activeSession, breaks::add);

	// session 0, shaft ends at 10 min
	private final List<JournalEntry> entries = List.of(EntryBuilder.entry().sessionId(0).enteredAt(9 * MINUTE).durationMillis(MINUTE).build());

	@Test
	void sessionGoesOnFromTheLastShaft() {
		Resume.shafts(journal, tracker, entries, List.of(), breaks);
		assertEquals(0, journal.activeSession(10 * MINUTE + GAP).id());
		assertNull(journal.activeSession(10 * MINUTE + GAP + 1));
	}

	@Test
	void miningAfterTheLastShaftKeepsItLonger() {
		Resume.shafts(journal, tracker, entries, List.of(mining(0, 20 * MINUTE)), breaks);
		assertEquals(0, journal.activeSession(20 * MINUTE + GAP).id());
	}

	@Test
	void eitherFileCanLoadFirst() {
		List<MiningEntry> mining = List.of(mining(0, 20 * MINUTE));
		Resume.activity(journal, tracker, List.of(), mining, breaks);
		assertNull(journal.session(), "no shafts yet, nothing to resume");
		Resume.shafts(journal, tracker, entries, mining, breaks);
		assertEquals(0, journal.activeSession(20 * MINUTE + GAP).id());

		Journal other = new Journal(TextPatterns.defaults(), () -> true, e -> {
		});
		Resume.shafts(other, tracker, entries, List.of(), breaks);
		Resume.activity(other, tracker, entries, mining, breaks);
		assertEquals(0, other.activeSession(20 * MINUTE + GAP).id());
	}

	@Test
	void onlyTheLastLineOfEachFileCounts() {
		List<MiningEntry> mining = List.of(mining(0, 20 * MINUTE), mining(5, 30 * MINUTE));
		Resume.shafts(journal, tracker, entries, mining, breaks);
		assertNull(journal.activeSession(20 * MINUTE + GAP), "newest mining is another session");
		assertEquals(-1, Resume.lastActivity(5, entries, List.of(), breaks));
		assertEquals(10 * MINUTE, Resume.lastActivity(0, entries, List.of(mining(0, 5 * MINUTE)), breaks), "older mining doesnt move it back");
	}

	// review 2.8, current rule: outside mining w/o a shaft in its session isnt resumed
	@Test
	void miningWithoutAShaftIsntResumed() {
		Resume.shafts(journal, tracker, List.of(), List.of(mining(0, 20 * MINUTE)), breaks);
		assertNull(journal.session());
	}

	@Test
	void breakRunsFromTheLastSavedActivity() {
		Resume.shafts(journal, tracker, entries, List.of(mining(0, 20 * MINUTE)), breaks);
		journal.onBlockHit(25 * MINUTE);
		tracker.onBlockHit(25 * MINUTE);
		assertEquals(List.of(new BreakEntry(BreakEntry.VERSION, 0, 20 * MINUTE, 25 * MINUTE)), breaks);
	}

	// break 1 saved, then a quit before the next mining line. break 2 starts at break 1s end, not the last mining
	@Test
	void savedBreakMovesTheLastActivity() {
		breaks.add(new BreakEntry(BreakEntry.VERSION, 0, 20 * MINUTE, 27 * MINUTE));
		assertEquals(27 * MINUTE, Resume.lastActivity(0, entries, List.of(), breaks));
		assertEquals(-1, Resume.lastActivity(0, List.of(), List.of(), breaks), "a break alone doesnt resume");
		Resume.shafts(journal, tracker, entries, List.of(mining(0, 20 * MINUTE)), breaks);
		journal.onBlockHit(30 * MINUTE);
		tracker.onBlockHit(30 * MINUTE);
		assertEquals(new BreakEntry(BreakEntry.VERSION, 0, 27 * MINUTE, 30 * MINUTE), breaks.getLast());
	}

	@Test
	void nothingSaved() {
		Resume.shafts(journal, tracker, List.of(), List.of(), breaks);
		assertNull(journal.session());
		tracker.onBlockHit(MINUTE);
		assertTrue(breaks.isEmpty());
	}

	private static MiningEntry mining(long sessionId, long at) {
		return new MiningEntry(MiningEntry.VERSION, sessionId, at, Map.of("Glacite", 64L), false, null);
	}
}
