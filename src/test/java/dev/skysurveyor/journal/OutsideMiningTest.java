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

	// synthetic: 140 glacite from before the shaft got compacted in it, the shaft counted its enchanted glacite
	@Test
	void shaftCompactionShortfallComesOffOutsideAdds() {
		hit(0);
		ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> {
			journal.onEvent(event, now);
			outside.onEvent(event, now);
		});
		now = 5_000;
		tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§8§711/15/26 §8m10§8§u§8§88A TUNG_1", "§b §7 §bGlacite M§b§p§b§bineshafts"));
		hit(10_000);
		now = 30_000;
		tracker.onChat(now, "§6[Sacks] §a+21§e items§e, §c-160§e items§e.§8 (Last 18s.)", List.of(
				"§aAdded items:\n§a  +1 §eEnchanted Glacite§8 (Enchanted Mining Sack)§a\n§a  +20 §eGlacite§8 (Mining Sack)§a"
						+ "\n\n§8This message can be disabled in the settings.",
				"§cRemoved items:\n§c  -160 §eGlacite§8 (Mining Sack)§c\n\n§8This message can be disabled in the settings."));
		now = 40_000;
		tracker.onWorldChange(now);
		hit(50_000);
		sacks(60_000, 30, item("Glacite", 200));
		assertEquals(Map.of("Glacite", 60L), mining.getLast().items());
	}

	// synthetic: 24 of the 412 flawed came from compacting rough, the tail ratio uses 2,000 rough to 388 flawed
	@Test
	void tailRatioLeavesOutCompactedFlawed() {
		hit(0);
		event(20_000, new SacksChanged(30, List.of(item("Flawed Jade Gemstone", 412), item("Rough Jade Gemstone", 2_000)),
				List.of(item("Rough Jade Gemstone", 1_920)), 0, 0));
		hit(25_000);
		event(26_000, new Pristine(item("Flawed Jade Gemstone", 97)));
		now = 30_000;
		outside.onWorldChange(now);
		// 97 * 2000 / 388 = 500
		assertEquals(500L, mining.getLast().items().get("Rough Jade Gemstone"));
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

	// recording 6 at 0:01.8 + 0:37.4: compacted glacite comes off, 27 short in the 1st msg comes off the 2nd
	@Test
	void compactionInSacksIsntCountedTwice() {
		hit(0);
		event(1_800, new SacksChanged(18, List.of(item("Enchanted Glacite", 32), item("Enchanted Hard Stone", 1), item("Glacite", 4_133),
				item("Hard Stone", 111), item("Refined Mineral", 2)), List.of(item("Glacite", 4_160), item("Hard Stone", 576)), 0, 0));
		hit(30_000);
		event(37_400, new SacksChanged(18, List.of(item("Enchanted Glacite", 21), item("Glacite", 3_408), item("Hard Stone", 329)),
				List.of(item("Glacite", 3_360)), 0, 0));

		assertEquals(Map.of("Enchanted Glacite", 32L, "Enchanted Hard Stone", 1L, "Refined Mineral", 2L), mining.get(0).items());
		// hard stone still 465 - 329 short
		assertEquals(Map.of("Enchanted Glacite", 21L, "Glacite", 21L), mining.get(1).items());
	}

	// recording 6 at 28:21.4, mined in its window here so it isnt skipped for that
	@Test
	void supercraftWithItsInputsRemovedStillCountsNothing() {
		hit(0);
		event(13_900, new Supercraft(item("Flawed Jade Gemstone", 2_814)));
		event(15_700, new Supercraft(item("Fine Jade Gemstone", 663)));
		event(21_400, new SacksChanged(7, List.of(item("Fine Jade Gemstone", 663), item("Flawed Jade Gemstone", 2_814)),
				List.of(item("Flawed Jade Gemstone", 53_040), item("Rough Jade Gemstone", 225_120)), 0, 0));
		hit(22_000);
		sacks(30_000, 8, item("Flawed Jade Gemstone", 151), item("Rough Jade Gemstone", 513));
		assertEquals(1, mining.size());
		assertEquals(Map.of("Flawed Jade Gemstone", 151L, "Rough Jade Gemstone", 513L), mining.getFirst().items());
	}

	// sum of the sacks msgs outside shafts by hand: glacite 79,666 in, 79,200 out, hard stone 8,761 in, 8,640 out
	// JADE_1 compacted 453 hard stone more than it got, those come off the hard stone after it
	@Test
	void recordingSix() throws IOException {
		Map<String, Long> reported = totals(replayed("2026-11-17_19-16-17.jsonl"), false);
		assertEquals(466L, reported.get("Glacite"));
		assertEquals(null, reported.get("Hard Stone"));
		assertEquals(525L, reported.get("Enchanted Glacite"));
		assertEquals(17L, reported.get("Enchanted Hard Stone"));
		// jade supercrafts after JADE_1 not mined
		assertEquals(null, reported.get("Fine Jade Gemstone"));
	}

	// replay totals match the sacks msgs outside shafts by hand (research/09)
	@Test
	void recordingFive() throws IOException {
		List<MiningEntry> replayed = replayed("2026-11-15_16-10-29.jsonl");
		Map<String, Long> reported = totals(replayed, false);
		Map<String, Long> tail = totals(replayed, true);
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

	private List<MiningEntry> replayed(String name) throws IOException {
		Path recording = RecordingReplay.RECORDINGS.resolve(name);
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
		return mining;
	}

	// estimated tails or reported
	private static Map<String, Long> totals(List<MiningEntry> mining, boolean estimated) {
		Map<String, Long> totals = new LinkedHashMap<>();
		for (MiningEntry entry : mining) {
			if (entry.estimated() == estimated) entry.items().forEach((item, count) -> totals.merge(item, count, Long::sum));
		}
		return totals;
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
