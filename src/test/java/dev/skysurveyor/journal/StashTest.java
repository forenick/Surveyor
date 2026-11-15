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
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class StashTest {
	// recording 5, 16:10:48
	private static final String REAL = "§r§f             §7You have §37,726,738 §7materials stashed away!";

	private boolean inMines = true;
	private final List<StashEntry> stashed = new ArrayList<>();
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> inMines, e -> {
	});
	private final Stash stash = new Stash(TextPatterns.defaults(), () -> inMines, journal::activeSession, stashed::add);

	private static String message(long count) {
		return REAL.replace("7,726,738", String.format("%,d", count));
	}

	private void hit(long at) {
		journal.onBlockHit(at);
		stash.onBlockHit(at);
	}

	@Test
	void realLineParses() {
		hit(0);
		stash.onChat(1_000, REAL);
		hit(30_000);
		stash.onChat(60_000, message(7_729_940));
		assertEquals(List.of(new StashEntry(StashEntry.VERSION, 0, 60_000, 3_202)), stashed);
		stash.onChat(61_000, "§r§f             §8(This totals 13 types of materials stashed!)");
		assertEquals(1, stashed.size());
	}

	@Test
	void firstMessageIsOnlyTheBaseline() {
		stash.onChat(0, message(100));
		hit(10_000);
		stash.onChat(20_000, message(500));
		assertTrue(stashed.isEmpty(), "no session at the 1st msg");
		hit(30_000);
		stash.onChat(40_000, message(800));
		assertEquals(300, Stash.grew(stashed, journal.activeSession(40_000).id()));
	}

	@Test
	void pickupIsNeverNegative() {
		hit(0);
		stash.onChat(1_000, message(5_000));
		hit(30_000);
		stash.onChat(60_000, message(10));
		hit(90_000);
		stash.onChat(120_000, message(70));
		assertEquals(List.of(60L), stashed.stream().map(StashEntry::grew).toList(), "counts from the new low");
	}

	@Test
	void onlyWhileMining() {
		hit(0);
		stash.onChat(1_000, message(100));
		stash.onChat(60_000, message(200));
		assertTrue(stashed.isEmpty(), "no mining since the last msg");
		inMines = false;
		hit(70_000);
		stash.onChat(120_000, message(300));
		assertTrue(stashed.isEmpty(), "block hits outside the mines");
	}

	@Test
	void newSessionStartsOver() {
		hit(0);
		stash.onChat(1_000, message(100));
		long later = Journal.SESSION_GAP_MILLIS + 60_000;
		hit(later);
		stash.onChat(later + 1_000, message(900));
		assertTrue(stashed.isEmpty(), "growth between sessions isnt this sessions");
	}

	@Test
	void fileRoundTrip(@TempDir Path dir) throws IOException {
		JsonLinesFile<StashEntry> file = new JsonLinesFile<>(dir.resolve("stash.jsonl"), StashEntry.class, e -> e.grew() > 0, Runnable::run);
		StashEntry entry = new StashEntry(StashEntry.VERSION, 1_794_754_944_760L, 1_794_755_452_390L, 3_202);
		file.append(entry);
		assertEquals(List.of(entry), file.read());
		assertEquals("{\"version\":1,\"sessionId\":1794754944760,\"at\":1794755452390,\"grew\":3202}",
				Files.readString(dir.resolve("stash.jsonl")).strip());
	}

	// stash 7,726,738 to 7,916,736 (money-tracker-design.md section 1)
	@Test
	void recordingFive() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-15_16-10-29.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		List<ShaftEvent> events = new ArrayList<>();
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), events::add);
		Journal replayJournal = new Journal(TextPatterns.defaults(), tracker::inMiningArea, e -> {
		});
		Stash replayed = new Stash(TextPatterns.defaults(), tracker::inMiningArea, replayJournal::activeSession, stashed::add);
		for (JsonObject record : RecordingReplay.read(recording)) {
			long now = RecordingReplay.time(record);
			events.clear();
			RecordingReplay.feed(record, tracker, t -> {
				replayJournal.onBlockHit(t);
				replayed.onBlockHit(t);
			});
			for (ShaftEvent event : events) {
				replayJournal.onEvent(event, now);
				replayed.onEvent(event, now);
			}
			if (RecordingReplay.type(record).equals("chat")) replayed.onChat(now, record.get("legacy").getAsString());
		}
		assertEquals(189_998, stashed.stream().mapToLong(StashEntry::grew).sum());
		assertEquals(1, stashed.stream().map(StashEntry::sessionId).distinct().count());
	}
}
