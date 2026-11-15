package dev.skysurveyor.journal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.Supercraft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.ItemPrice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

// counts from recording 5 (research/09)
class OutsideMiningTest {
	private final List<JournalEntry> entries = new ArrayList<>();
	private final List<MiningEntry> mining = new ArrayList<>();
	private boolean inMines = true;
	private long now;
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> inMines, entries::add);
	private final OutsideMining outside = new OutsideMining(TextPatterns.defaults(), () -> inMines, journal::activeSession,
			mining::add);

	@Test
	void countsSacksWhileMining() {
		hit(0);
		hit(20_000);
		sacks(23_300, 30, item("Glacite", 5196), item("Flawed Jade Gemstone", 810), item("Rough Jade Gemstone", 5370));

		assertEquals(1, mining.size());
		MiningEntry entry = mining.getFirst();
		assertEquals(Map.of("Glacite", 5196L, "Flawed Jade Gemstone", 810L, "Rough Jade Gemstone", 5370L), entry.items());
		assertEquals(journal.activeSession(now).id(), entry.sessionId());
		assertEquals(23_300, entry.at());
	}

	@Test
	void pickaxeAbilityCountsAsMining() {
		hit(0);
		event(40_000, new PickaxeAbility("Pickobulus"));
		sacks(60_000, 30, item("Flawed Jade Gemstone", 1161));
		assertEquals(1, mining.size());
	}

	@Test
	void skipsSacksWithoutMiningInTheirWindow() {
		hit(0);
		// stash pickup or a craft a minute later
		sacks(60_000, 30, item("Glacite", 64));
		assertTrue(mining.isEmpty());
	}

	@Test
	void skipsSacksOutsideTheMines() {
		hit(0);
		inMines = false;
		sacks(20_000, 30, item("Flawed Jade Gemstone", 810));
		assertTrue(mining.isEmpty());
	}

	@Test
	void skipsSacksInsideAShaft() {
		hit(0);
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> {
			journal.onEvent(event, now);
			outside.onEvent(event, now);
		});
		now = 5_000;
		tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§8§711/15/26 §8m10§8§u§8§88A TUNG_1", "§b §7 §bGlacite M§b§p§b§bineshafts"));
		hit(10_000);
		sacks(30_000, 12, item("Enchanted Tungsten", 1), item("Rough Jade Gemstone", 80));
		assertTrue(mining.isEmpty());
	}

	@Test
	void supercraftResultsArentMined() {
		hit(0);
		event(5_000, new Supercraft(item("Fine Jasper Gemstone", 476)));
		sacks(20_000, 30, item("Fine Jasper Gemstone", 476), item("Flawed Jade Gemstone", 70));
		assertEquals(Map.of("Flawed Jade Gemstone", 70L), mining.getFirst().items());
	}

	// 1:59.3 pristine never reported, portal at 2:11.5. rough from the gems ratio so far
	@Test
	void estimatesTheTailBeforeAWorldChange() {
		hit(0);
		sacks(23_300, 30, item("Flawed Jade Gemstone", 810), item("Rough Jade Gemstone", 5370));
		hit(30_000);
		event(31_000, new Pristine(item("Flawed Jade Gemstone", 34)));
		event(32_000, new Pristine(item("Flawed Sapphire Gemstone", 321)));
		now = 34_000;
		outside.onWorldChange(now);

		assertEquals(2, mining.size());
		MiningEntry tail = mining.getLast();
		assertTrue(tail.estimated());
		// 34 * 5370 / 810 = 225.4
		Map<String, Long> expected = new LinkedHashMap<>();
		expected.put("Flawed Jade Gemstone", 34L);
		expected.put("Rough Jade Gemstone", 225L);
		expected.put("Flawed Sapphire Gemstone", 321L);
		assertEquals(expected, tail.items());
	}

	@Test
	void noTailWhenPristineWasReported() {
		hit(0);
		event(1_000, new Pristine(item("Flawed Jade Gemstone", 670)));
		sacks(23_300, 30, item("Flawed Jade Gemstone", 810));
		outside.onWorldChange(30_000);
		assertEquals(1, mining.size());
	}

	@Test
	void noTailLongAfterMining() {
		hit(0);
		event(1_000, new Pristine(item("Flawed Jade Gemstone", 670)));
		outside.onWorldChange(Journal.PAUSE_MILLIS + 2_000);
		assertTrue(mining.isEmpty());
	}

	@Test
	void fileRoundTrip(@TempDir Path dir) throws IOException {
		hit(0);
		sacks(23_300, 30, item("Glossy Gemstone", 1), item("Refined Mineral", 1));
		MiningEntry priced = mining.getFirst().withPrices(Map.of("GLOSSY_GEMSTONE", new ItemPrice(1_509_805.7, 1_579_563.2, null, null)));
		JsonLinesFile<MiningEntry> file = new JsonLinesFile<>(dir.resolve("mining.jsonl"), MiningEntry.class, e -> e.items() != null, Runnable::run);
		file.append(priced);
		assertEquals(List.of(priced), file.read());
	}

	// replay totals match the sacks msgs outside shafts by hand (research/09)
	@Test
	void recordingFive() throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-15_16-10-29.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		List<ShaftEvent> events = new ArrayList<>();
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), events::add);
		OutsideMining replayed = new OutsideMining(TextPatterns.defaults(), tracker::inMiningArea, journal::activeSession, mining::add);
		for (JsonObject record : RecordingReplay.read(recording)) {
			now = RecordingReplay.time(record);
			events.clear();
			RecordingReplay.feed(record, tracker, t -> {
				journal.onBlockHit(t);
				replayed.onBlockHit(t);
			});
			for (ShaftEvent event : events) {
				journal.onEvent(event, now);
				replayed.onEvent(event, now);
			}
			if (RecordingReplay.type(record).equals("world_change")) replayed.onWorldChange(now);
		}
		Map<String, Long> reported = new LinkedHashMap<>();
		Map<String, Long> tail = new LinkedHashMap<>();
		for (MiningEntry entry : mining) entry.items().forEach((item, count) -> (entry.estimated() ? tail : reported).merge(item, count, Long::sum));
		assertEquals(25_716L, reported.get("Flawed Jade Gemstone"));
		assertEquals(146_399L, reported.get("Rough Jade Gemstone"));
		assertEquals(5L, reported.get("Glossy Gemstone"));
		assertEquals(35L, reported.get("Refined Mineral"));
		assertEquals(55_749L, reported.get("Glacite"));
		// nothing from the shafts or the jasper supercrafts
		assertEquals(null, reported.get("Fine Jasper Gemstone"));
		assertEquals(null, reported.get("Rough Jasper Gemstone"));
		// pristine flawed after the last sacks msg before each portal
		assertEquals(1_497L, tail.get("Flawed Jade Gemstone"));
		assertEquals(1_312L, tail.get("Flawed Sapphire Gemstone"));
	}

	private void hit(long at) {
		now = at;
		journal.onBlockHit(at);
		outside.onBlockHit(at);
	}

	private void event(long at, ShaftEvent event) {
		now = at;
		journal.onEvent(event, at);
		outside.onEvent(event, at);
	}

	private void sacks(long at, int window, ItemCount... added) {
		event(at, new SacksChanged(window, List.of(added), List.of(), 0, 0));
	}

	private static ItemCount item(String name, long count) {
		return new ItemCount(name, count);
	}
}
