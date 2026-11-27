package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// made up gaps, rec 5 has none over 43s
class BreaksTest {
	private static final long MINUTE = 60_000;
	private final List<BreakEntry> breaks = new ArrayList<>();
	private boolean inMines = true;
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> inMines, entry -> {
	});
	private final Breaks tracker = new Breaks(() -> inMines, journal::activeSession, breaks::add);

	@Test
	void gapOverTwoMinutesIsABreak() {
		hit(0);
		hit(MINUTE);
		hit(6 * MINUTE);
		assertEquals(List.of(new BreakEntry(1, 0, MINUTE, 6 * MINUTE)), breaks);
		// 1st 2 min still count
		assertEquals(3 * MINUTE, Breaks.counted(breaks, 0, 6 * MINUTE));
	}

	@Test
	void shortGapsArentBreaks() {
		hit(0);
		hit(2 * MINUTE);
		hit(3 * MINUTE + 59_000);
		assertTrue(breaks.isEmpty());
	}

	@Test
	void pickaxeAbilityCountsAsMining() {
		hit(0);
		event(MINUTE, new PickaxeAbility("Pickobulus"));
		hit(2 * MINUTE + 30_000);
		assertTrue(breaks.isEmpty());
	}

	@Test
	void timeInAShaftAlwaysCounts() {
		hit(0);
		event(30_000, new ShaftEntered(null));
		// looting, campfire, afk: in a shaft, not mining
		event(12 * MINUTE, new ShaftLeft(null));
		hit(13 * MINUTE);
		assertTrue(breaks.isEmpty());
	}

	@Test
	void breakAfterAShaftStartsAtTheExit() {
		hit(0);
		event(30_000, new ShaftEntered(null));
		event(MINUTE, new ShaftLeft(null));
		hit(7 * MINUTE);
		assertEquals(List.of(new BreakEntry(1, 0, MINUTE, 7 * MINUTE)), breaks);
	}

	@Test
	void breakEndsAtTheNextShaftEntry() {
		hit(0);
		event(5 * MINUTE, new ShaftEntered(null));
		assertEquals(List.of(new BreakEntry(1, 0, 0, 5 * MINUTE)), breaks);
	}

	@Test
	void hitsOutsideTheMinesDontEndABreak() {
		hit(0);
		inMines = false;
		// hub, garden
		hit(3 * MINUTE);
		inMines = true;
		hit(8 * MINUTE);
		assertEquals(List.of(new BreakEntry(1, 0, 0, 8 * MINUTE)), breaks);
	}

	@Test
	void noBreakAcrossSessions() {
		hit(0);
		hit(Journal.SESSION_GAP_MILLIS + MINUTE);
		assertTrue(breaks.isEmpty());
		assertEquals(Journal.SESSION_GAP_MILLIS + MINUTE, journal.activeSession(Journal.SESSION_GAP_MILLIS + MINUTE).id());
	}

	@Test
	void currentBreakForLiveRates() {
		hit(0);
		hit(10 * MINUTE);
		assertNull(tracker.current(11 * MINUTE));
		assertNull(tracker.current(12 * MINUTE), "2 min isnt over 2 min");
		assertEquals(new BreakEntry(1, 0, 10 * MINUTE, 14 * MINUTE), tracker.current(14 * MINUTE));
		event(14 * MINUTE, new ShaftEntered(null));
		assertNull(tracker.current(20 * MINUTE), "in a shaft");
	}

	@Test
	void liveRateStopsFallingAfterTwoMinutes() {
		JournalEntry shaft = EntryBuilder.entry().sessionId(0).enteredAt(5 * MINUTE).durationMillis(MINUTE).build();
		SessionSummary session = new SessionSummary(0, List.of(shaft));
		hit(0);
		event(5 * MINUTE, new ShaftEntered(null));
		event(6 * MINUTE, new ShaftLeft(null));
		Double before = rate(session, 8 * MINUTE);
		Double later = rate(session, 14 * MINUTE);
		assertNotNull(before);
		assertEquals(before, later);
		assertEquals(1 * 60.0 / 8, later, 1e-9);
		// w/o breaks it kept falling
		assertEquals(1 * 60.0 / 14, session.perHour(1, 14 * MINUTE), 1e-9);
	}

	@Test
	void resumesAfterARestart() {
		Journal.Session session = journal.activeSession(0);
		assertNull(session);
		hit(0);
		Breaks restarted = new Breaks(() -> inMines, journal::activeSession, breaks::add);
		restarted.resume(0, 30_000);
		restarted.onBlockHit(6 * MINUTE);
		journal.onBlockHit(6 * MINUTE);
		assertEquals(List.of(new BreakEntry(1, 0, 30_000, 6 * MINUTE)), breaks);
	}

	@Test
	void sessionRatesLeaveBreaksOut() {
		JournalEntry first = EntryBuilder.entry().sessionId(0).enteredAt(10 * MINUTE).durationMillis(5 * MINUTE).build();
		JournalEntry second = EntryBuilder.entry().sessionId(0).enteredAt(50 * MINUTE).durationMillis(10 * MINUTE).build();
		// made up: 25 min selling, 23 of it a break
		BreakEntry selling = new BreakEntry(1, 0, 15 * MINUTE, 40 * MINUTE);
		SessionSummary session = JournalStats.sessions(List.of(first, second), List.of(), List.of(selling)).getFirst();
		assertEquals(60 * MINUTE, session.lengthMillis());
		assertEquals(23 * MINUTE, session.breakMillis());
		assertEquals(37 * MINUTE, session.rateMillis());
		assertEquals(2 * 60.0 / 37, session.shaftsPerHour(), 1e-9);
		assertEquals(37 * MINUTE, JournalStats.allTime(List.of(first, second), List.of(), List.of(selling)).timeMillis());
		// break after the last activity isnt in it
		BreakEntry after = new BreakEntry(1, 0, 60 * MINUTE, 70 * MINUTE);
		assertEquals(23 * MINUTE, session.withBreaks(List.of(selling, after)).breakMillis());
	}

	@Test
	void breaksOfOtherSessionsDontCount() {
		JournalEntry shaft = EntryBuilder.entry().sessionId(0).enteredAt(10 * MINUTE).durationMillis(5 * MINUTE).build();
		BreakEntry other = new BreakEntry(1, 99, MINUTE, 9 * MINUTE);
		assertEquals(0, JournalStats.sessions(List.of(shaft), List.of(), List.of(other)).getFirst().breakMillis());
	}

	@Test
	void fileRoundTrip(@TempDir Path dir) throws IOException {
		BreakEntry entry = new BreakEntry(BreakEntry.VERSION, 1_794_754_944_760L, 1_794_755_452_390L, 1_794_755_812_390L);
		JsonLinesFile<BreakEntry> file = new JsonLinesFile<>(dir.resolve("breaks.jsonl"), BreakEntry.class, e -> e.to() > e.from(), Runnable::run);
		file.append(entry);
		assertEquals(List.of(entry), file.read());
	}

	// rec 5: no gap over 43s, same rates
	@Test
	void recordingFiveHasNoBreaks() throws IOException {
		Replayed plain = replay(-1);
		assumeTrue(plain != null, "Recording not available");
		assertTrue(plain.breaks.isEmpty());
		SessionSummary without = JournalStats.sessions(plain.entries, plain.mining, List.of()).getFirst();
		SessionSummary with = JournalStats.sessions(plain.entries, plain.mining, plain.breaks).getFirst();
		assertEquals(without.rateMillis(), with.rateMillis());
		assertEquals(without.lengthMillis(), with.rateMillis());
		assertEquals(without.shaftsPerHour(), with.shaftsPerHour());
	}

	// same + made up 10 min pause after the 10th shaft
	@Test
	void recordingFiveWithAMadeUpBreak() throws IOException {
		Replayed plain = replay(-1);
		assumeTrue(plain != null, "Recording not available");
		Replayed paused = replay(10);
		assertEquals(1, paused.breaks.size());
		BreakEntry pause = paused.breaks.getFirst();
		long before = pause.to() - pause.from() - 10 * MINUTE;
		assertTrue(before >= 0 && before < Breaks.BREAK_MILLIS, "gap there w/o the pause " + before);
		SessionSummary original = JournalStats.sessions(plain.entries, plain.mining, List.of()).getFirst();
		SessionSummary session = JournalStats.sessions(paused.entries, paused.mining, paused.breaks).getFirst();
		assertEquals(original.shafts(), session.shafts());
		assertEquals(original.lengthMillis() + 10 * MINUTE, session.lengthMillis());
		// only the 1st 2 min of the whole gap count
		assertEquals(original.rateMillis() + Breaks.BREAK_MILLIS - before, session.rateMillis());
	}

	private record Replayed(List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> breaks) {
	}

	// pauseAfter -1 = no pause
	private static Replayed replay(int pauseAfter) throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-15_16-10-29.jsonl");
		if (!Files.exists(recording)) return null;
		List<JournalEntry> entries = new ArrayList<>();
		List<MiningEntry> mining = new ArrayList<>();
		List<BreakEntry> breaks = new ArrayList<>();
		List<ShaftEvent> events = new ArrayList<>();
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), events::add);
		Journal journal = new Journal(TextPatterns.defaults(), tracker::inMiningArea, entries::add);
		OutsideMining outside = new OutsideMining(TextPatterns.defaults(), tracker::inMiningArea, journal::activeSession, mining::add);
		Breaks replayed = new Breaks(tracker::inMiningArea, journal::activeSession, breaks::add);
		long offset = 0;
		for (JsonObject record : RecordingReplay.read(recording)) {
			if (offset == 0 && entries.size() == pauseAfter && RecordingReplay.type(record).equals("block_hit")) offset = 10 * MINUTE;
			long now = RecordingReplay.time(record) + offset;
			record.addProperty("t", now);
			events.clear();
			RecordingReplay.feed(record, tracker, t -> {
				journal.onBlockHit(t);
				outside.onBlockHit(t);
				replayed.onBlockHit(t);
			});
			for (ShaftEvent event : events) {
				journal.onEvent(event, now);
				outside.onEvent(event, now);
				replayed.onEvent(event, now);
			}
			if (RecordingReplay.type(record).equals("world_change")) outside.onWorldChange(now);
		}
		return new Replayed(entries, mining, breaks);
	}

	private Double rate(SessionSummary session, long now) {
		return session.withBreaks(tracker.current(now) == null ? List.of() : List.of(tracker.current(now))).perHour(1, now);
	}

	private void hit(long at) {
		journal.onBlockHit(at);
		tracker.onBlockHit(at);
	}

	// no real shaft, session stays up w/o it
	private void event(long at, ShaftEvent event) {
		if (!(event instanceof ShaftLeft)) journal.onEvent(event, at);
		tracker.onEvent(event, at);
	}
}
