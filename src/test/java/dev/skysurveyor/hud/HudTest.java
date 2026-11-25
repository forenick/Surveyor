package dev.skysurveyor.hud;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TabListParser;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.effects.Consumable;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudLayout.Anchor;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudValue.Kind;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.Breaks;
import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalFile;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.MetalYield;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class HudTest {
	// ids saved in hud.json + tags, see HudBoxText TAG
	@Test
	void everyValueHasAUsableIdAndExamples() {
		Set<String> ids = new HashSet<>();
		for (HudValue value : HudValue.values()) {
			assertTrue(ids.add(value.id()), "id twice: " + value.id());
			assertTrue(value.id().matches("[a-z_]+"), "not a tag id: " + value.id());
			if (!value.hasNumber()) continue;
			List<Range> ranges = new ArrayList<>(value.ranges());
			if (ranges.isEmpty()) ranges.add(null);
			for (Range range : ranges) {
				Object example = HudData.EXAMPLE.get(value, range);
				String where = value.id() + " " + range;
				assertTrue(example != null, "no example for " + where);
				Class<?> type = value.kind() == Kind.COINS || value.kind() == Kind.DECIMAL ? Double.class : Long.class;
				assertEquals(type, example.getClass(), "example type of " + where);
			}
		}
	}

	@Test
	void formatsEveryKind() {
		assertEquals("1:42", HudFormat.format(Kind.TIME, 0, 102_900L));
		assertEquals("1m 42s", HudFormat.format(Kind.TIME, 1, 102_000L));
		assertEquals("42s", HudFormat.format(Kind.TIME, 1, 42_000L));
		assertEquals("102s", HudFormat.format(Kind.TIME, 2, 102_000L));
		assertEquals("42m", HudFormat.format(Kind.TIME, 3, 2_532_000L));
		assertEquals("1h 0m", HudFormat.format(Kind.TIME, 3, 3_600_000L));
		assertEquals("3:15", HudFormat.format(Kind.TIME, 3, 195_000L));
		assertEquals(3, new HudLayout.Line(HudValue.PRISTINE_POTATO).format);
		assertEquals("43,436", HudFormat.format(Kind.INTEGER, 0, 43_436L));
		assertEquals("43.4k", HudFormat.format(Kind.INTEGER, 1, 43_436L));
		assertEquals("1.2M", HudFormat.format(Kind.INTEGER, 1, 1_204_310L));
		assertEquals("999", HudFormat.format(Kind.INTEGER, 1, 999L));
		assertEquals("999.9k", HudFormat.format(Kind.INTEGER, 1, 999_949L));
		assertEquals("1.0M", HudFormat.format(Kind.INTEGER, 1, 999_950L), "not 1000.0k");
		assertEquals("43436", HudFormat.format(Kind.INTEGER, 2, 43_436L));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 0, 19.42));
		assertEquals("19", HudFormat.format(Kind.DECIMAL, 1, 19.42));
		assertEquals("19.42", HudFormat.format(Kind.DECIMAL, 2, 19.42));
		assertEquals("-", HudFormat.format(Kind.DECIMAL, 0, null));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 9, 19.42), "an unknown format falls back to the first");
	}

	@Test
	void defaultBoxesMatchTheMockup() {
		HudData example = HudData.EXAMPLE;
		List<Box> boxes = HudLayout.defaults().boxes;
		assertEquals(List.of("§e§lSession", "§7Shafts: §f6", "§7Shafts/h: §f19.4", "§7Shaft powder: §b43,436"),
				HudBoxText.lines(boxes.get(0), example, false));
		assertEquals(List.of("§7Spawn: §f§l1:42", "§7Avg: §f§l1:25", "§7Efficiency: §f§l69%"), HudBoxText.lines(boxes.get(1), example, false),
				"no colour rules on the defaults");
		assertEquals(List.of("§a§l§a§lJade§a§l Shaft", "§7Time inside: §f2:14", "§7Corpses: §f2/4 §9Lapis§f, §6Umber§f",
				"§7Campfires: §61", "§7Coins: §62.4M", "§7Efficiency: §f69%"), HudBoxText.lines(boxes.get(2), example, false), "title in the shafts colour");
		assertEquals(List.of("§e§lSession profit", "§7Total: §614.8M", "§7Per hour: §646.8M", "§7Per shaft: §62.5M",
				"§7Time: §f19:02", "§9Fine Jasper Gemstone §8x931", "§fGlossy Gemstone §8x13", "§9Fine Jade Gemstone §8x378",
				"§fRefined Mineral §8x35", "§9Fine Amber Gemstone §8x83", "§77 more", "§fTungsten Key §8x3"), HudBoxText.lines(boxes.get(3), example, false), "/h once, drops last (nick, 2026-11-16)");
		HudData noShaft = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertEquals("§e§lShaft", HudBoxText.lines(boxes.get(2), noShaft, false).getFirst(), "yellow w/o a shaft");
	}

	@Test
	void hintOnlyOnFirstStartTillTheEditorOpens() throws Exception {
		HudLayout first = HudLayout.firstStart();
		assertTrue(first.boxes.stream().allMatch(Box::hint));
		assertFalse(HudLayout.defaults().toJson().contains("hint"), "reset all + presets get none");
		HudLayout saved = HudLayout.fromJson(first.toJson());
		assertTrue(saved.boxes.getFirst().hint());
		assertTrue(saved.dropHints());
		assertFalse(saved.dropHints());
		assertEquals(HudLayout.defaults().toJson(), saved.toJson(), "dropped = plain defaults, no hint key");
	}

	@Test
	void missingValuesShowADashOrTheExample() {
		Box spawn = HudLayout.defaults().boxes.get(1);
		HudData empty = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertEquals(List.of("§7Spawn: §f§l-", "§7Avg: §f§l-", "§7Efficiency: §f§l-"), HudBoxText.lines(spawn, empty, false));
		assertEquals(List.of("§7Spawn: §f§l1:42", "§7Avg: §f§l1:25", "§7Efficiency: §f§l69%"), HudBoxText.lines(spawn, empty, true));
		assertEquals("Shaft", HudBoxText.tags("{shaft_type} Shaft", empty, false).strip(), "a missing tag is left out");
		Box session = HudLayout.defaults().boxes.get(0);
		assertEquals("§7Shafts: §f0", HudBoxText.lines(session, empty, false).get(1), "the HUD shows the real zero");
		assertEquals(List.of("§e§lSession", "§7Shafts: §f6", "§7Shafts/h: §f19.4", "§7Shaft powder: §b43,436"),
				HudBoxText.lines(session, empty, true), "the editor previews a whole example session, not 0 shafts at 19.4/h");
	}

	@Test
	void layoutSurvivesTheFile() {
		HudLayout layout = HudLayout.defaults();
		layout.boxes.get(0).labelColor = "b";
		layout.boxes.get(0).lines.get(2).shown = true;
		HudLayout read = HudLayout.fromJson(layout.toJson());
		assertEquals(layout.toJson(), read.toJson());
	}

	@Test
	void repairsAHandEditedFile() {
		HudLayout read = HudLayout.fromJson("""
				{"boxes": [{"id": "mine", "labelColor": "z", "scale": 9, "anchorX": "SIDEWAYS",
				  "lines": [{"value": "nope"}, {"value": "spawn_timer", "format": -5}]}, null]}""");
		Box box = read.boxes.getFirst();
		assertEquals(1, read.boxes.size());
		assertEquals("7", box.labelColor);
		assertEquals(2, box.scale);
		assertEquals(Anchor.START, box.anchorX, "an unknown anchor becomes the default");
		assertTrue(box.showInShaft && box.showInMines && !box.showElsewhere, "shown in the mines and shafts by default");
		assertEquals(1, box.lines.size());
		assertEquals("Spawn: ", box.lines.getFirst().prefix);
		assertEquals(0, box.lines.getFirst().format);
		assertTrue(HudLayout.fromJson("{}").boxes.isEmpty(), "deleting every box is allowed");
		assertThrows(JsonParseException.class, () -> HudLayout.fromJson("[]"));
		assertThrows(JsonParseException.class, () -> HudLayout.fromJson("{\"boxes\": null}"));
	}

	@Test
	void convertsVersion1Positions() {
		HudLayout read = HudLayout.fromJson("""
				{"version": 1, "boxes": [{"id": "a", "x": 1.0, "y": 0.0}, {"id": "b", "x": 0.5, "y": 0.58},
				  {"id": "c", "x": 0.0, "y": 1.0}, {"id": "d", "x": 0.25, "y": 0.9}]}""");
		List<Box> boxes = read.boxes;
		assertEquals(List.of(Anchor.END, Anchor.START), List.of(boxes.get(0).anchorX, boxes.get(0).anchorY));
		assertEquals(List.of(0, 0), List.of(boxes.get(0).offsetX, boxes.get(0).offsetY), "edges convert exactly");
		assertEquals(List.of(Anchor.MIDDLE, Anchor.MIDDLE), List.of(boxes.get(1).anchorX, boxes.get(1).anchorY));
		assertEquals(0, boxes.get(1).offsetX);
		assertEquals(21, boxes.get(1).offsetY, "8% of the 262 usable pixels below the middle");
		assertEquals(List.of(Anchor.START, Anchor.END), List.of(boxes.get(2).anchorX, boxes.get(2).anchorY));
		assertEquals(List.of(118, 26), List.of(boxes.get(3).offsetX, boxes.get(3).offsetY));
		assertEquals(HudLayout.VERSION, read.version);
		assertFalse(read.toJson().contains("\"x\""), "the old fields aren't written back");
	}

	@Test
	void anchorsPlaceBoxesFromTheirEdge() {
		assertEquals(4, Anchor.START.start(0, 60, 480));
		assertEquals(416, Anchor.END.start(0, 60, 480), "right edge on the margin");
		assertEquals(210, Anchor.MIDDLE.start(0, 60, 480));
		assertEquals(4, Anchor.START.start(-50, 60, 480), "kept inside the margin");
		for (Anchor anchor : Anchor.values()) {
			assertEquals(100, anchor.start(anchor.offset(100, 60, 480), 60, 480), anchor + " round trip");
		}
		assertEquals(416 + 60, Anchor.END.start(0, 60, 480) + 60);
		assertEquals(386 + 90, Anchor.END.start(0, 90, 480) + 90);
		assertEquals(Anchor.START, Anchor.forPosition(10, 60, 480));
		assertEquals(Anchor.MIDDLE, Anchor.forPosition(200, 60, 480));
		assertEquals(Anchor.END, Anchor.forPosition(400, 60, 480));
	}

	@Test
	void repairsMissingAndDuplicateIds() {
		HudLayout read = HudLayout.fromJson("{\"boxes\": [{\"id\": \"box1\"}, {}, {\"id\": \"box1\"}, {\"id\": \" \"}]}");
		assertEquals(List.of("box1", "box2", "box3", "box4"), read.boxes.stream().map(b -> b.id).toList());
	}

	@Test
	void visibility() {
		Box box = new Box();
		assertTrue(box.visible(true, false));
		assertTrue(box.visible(false, true));
		assertFalse(box.visible(false, false));
		box.showInMines = false;
		assertFalse(box.visible(true, false));
		assertTrue(box.visible(true, true), "a shaft counts as a shaft, not as the mines");
		box.showElsewhere = true;
		assertTrue(box.visible(false, false));
	}

	@Test
	void convertsVersion2Visibility() {
		HudLayout read = HudLayout.fromJson("""
				{"version": 2, "boxes": [{"id": "a", "visibility": "ALWAYS"}, {"id": "b", "visibility": "MINES"},
				  {"id": "c", "visibility": "IN_SHAFT"}, {"id": "d", "visibility": "OUTSIDE_SHAFT"}]}""");
		List<List<Boolean>> places = read.boxes.stream()
				.map(b -> List.of(b.showInShaft, b.showInMines, b.showElsewhere)).toList();
		assertEquals(List.of(List.of(true, true, true), List.of(true, true, false), List.of(true, false, false),
				List.of(false, true, false)), places);
		assertFalse(read.toJson().contains("visibility"), "the old field isn't written back");
	}

	@Test
	void movesLinesToAGap() {
		Box box = HudLayout.defaults().boxes.get(2);
		// 4 lines, as when this was written
		box.lines.subList(4, box.lines.size()).clear();
		List<String> start = box.lines.stream().map(l -> l.value).toList();
		box.moveLine(0, 2);
		assertEquals(List.of(start.get(1), start.get(0), start.get(2), start.get(3)), box.lines.stream().map(l -> l.value).toList());
		box.moveLine(3, 0);
		assertEquals(List.of(start.get(3), start.get(1), start.get(0), start.get(2)), box.lines.stream().map(l -> l.value).toList());
		box.moveLine(1, 4);
		assertEquals(start.get(1), box.lines.getLast().value, "the gap below the last line");
		List<String> before = box.lines.stream().map(l -> l.value).toList();
		box.moveLine(1, 1);
		box.moveLine(1, 2);
		box.moveLine(0, 9);
		assertEquals(before, box.lines.stream().map(l -> l.value).toList(), "dropping on itself or out of range does nothing");
	}

	@Test
	void undoAndRedo() {
		HudHistory history = new HudHistory("a");
		assertFalse(history.canUndo());
		assertFalse(history.record("a"), "no change, no step");
		assertTrue(history.record("b"));
		history.record("c");
		assertEquals("b", history.undo());
		assertEquals("a", history.undo());
		assertNull(history.undo());
		assertEquals("b", history.redo());
		history.record("d");
		assertFalse(history.canRedo(), "a new change clears redo");
		assertEquals("b", history.undo());
		for (int i = 0; i < HudHistory.LIMIT + 10; i++) history.record("x" + i);
		int steps = 0;
		while (history.undo() != null) steps++;
		assertEquals(HudHistory.LIMIT, steps);
	}

	// recording 6 AQUA_2: loot msg 9:53.3, widget shows up 9:53.5 w/ lapis not looted, never flips before leaving
	@Test
	void liveShaftCountsALootBeforeTheTabListFlips() throws Exception {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-17_19-16-17.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		TextPatterns patterns = TextPatterns.defaults();
		List<HudData> seen = new ArrayList<>();
		List<List<Corpse>> tab = new ArrayList<>();
		ShaftTracker[] tracker = new ShaftTracker[1];
		tracker[0] = new ShaftTracker(patterns, event -> {
			Shaft shaft = tracker[0].current();
			if (shaft == null || shaft.id() == null || !shaft.id().id().equals("AQUA_2")) return;
			if (event instanceof ShaftEvent.CorpseLooted || event instanceof ShaftEvent.CorpsesChanged) {
				tab.add(shaft.corpses());
				seen.add(HudFixtures.live(SessionTotals.NONE, Totals.NONE, Totals.of(shaft, patterns), null, shaft, 0, ShaftNames.defaults()));
			}
		});
		RecordingReplay.replay(recording, tracker[0], t -> {
		}, t -> {
		});
		assertEquals(2, seen.size());
		assertEquals(List.of(), tab.get(0), "no widget yet at the loot msg");
		assertEquals(1L, seen.get(0).get(HudValue.CORPSES_LOOTED, Range.SHAFT, "lapis"));
		assertEquals("1/1 " + HudData.paint('9', "Lapis"), seen.get(0).get(HudValue.SHAFT_CORPSES, HudValue.SHAFT_CORPSES.defaultRange()));
		assertEquals(new Corpse("Lapis", false), tab.get(1).getFirst());
		assertEquals(1L, seen.get(1).get(HudValue.CORPSES_LOOTED, Range.SHAFT, "lapis"));
		assertEquals("1/4 " + HudData.paint('9', "Lapis") + ", " + HudData.paint('6', "Umber") + ", " + HudData.paint('7', "Tungsten"),
				seen.get(1).get(HudValue.SHAFT_CORPSES, HudValue.SHAFT_CORPSES.defaultRange()));
	}

	@Test
	void corpseSummary() {
		assertNull(HudData.corpses(CorpseCounts.of(List.of(), List.of())));
		assertEquals("2/4 " + HudData.paint('9', "Lapis") + ", " + HudData.paint('6', "Umber"), HudData.corpses(CorpseCounts.of(List.of(
				new Corpse("Umber", false), new Corpse("Lapis", true), new Corpse("Lapis", true), new Corpse("Umber", false)), List.of())));
	}

	// nicks real session 2026-11-11
	@Test
	void sessionValuesFromTheRealJournal() throws Exception {
		List<JournalEntry> entries = realJournal();
		HudData data = live(entries, 0);
		assertEquals(6L, data.get(HudValue.SESSION_SHAFTS, HudValue.SESSION_SHAFTS.defaultRange()));
		assertEquals(43_436L, data.get(HudValue.SESSION_POWDER, HudValue.SESSION_POWDER.defaultRange()));
		assertEquals("19.4", HudFormat.format(Kind.DECIMAL, 0, data.get(HudValue.SHAFTS_PER_HOUR, HudValue.SHAFTS_PER_HOUR.defaultRange())));
		// (8,889+55,940+38,186+193,848+283,594+185,948) / 6 ms
		assertEquals("2:07", HudFormat.format(Kind.TIME, 0, data.get(HudValue.AVERAGE_SPAWN, HudValue.AVERAGE_SPAWN.defaultRange())));
		assertNull(data.get(HudValue.SHAFT_TIME, HudValue.SHAFT_TIME.defaultRange()));
		// rate counts time since last shaft too, 2x time = half rate
		long start = entries.getFirst().sessionId();
		long length = new JournalStats.SessionSummary(start, entries).lengthMillis();
		HudData later = live(entries, start + 2 * length);
		assertEquals("9.7", HudFormat.format(Kind.DECIMAL, 0, later.get(HudValue.SHAFTS_PER_HOUR, HudValue.SHAFTS_PER_HOUR.defaultRange())));
	}

	@Test
	void timeRanges() throws Exception {
		List<JournalEntry> entries = realJournal();
		long start = entries.getFirst().sessionId();
		long end = new JournalStats.SessionSummary(start, entries).end();
		HudData data = live(entries, end);
		assertEquals(6L, data.get(HudValue.SESSION_SHAFTS, Range.ALL_TIME));
		assertEquals(43_436L, data.get(HudValue.SESSION_POWDER, Range.ALL_TIME));
		JournalEntry last = entries.getLast();
		assertEquals(JournalStats.powder(last), data.get(HudValue.SESSION_POWDER, Range.SHAFT), "the last shaft outside one");
		double hours = 6 / (Double) data.get(HudValue.SHAFTS_PER_HOUR, HudValue.SHAFTS_PER_HOUR.defaultRange());
		assertEquals(Math.round(43_436 / hours), data.get(HudValue.SESSION_POWDER, Range.PER_HOUR));
		assertEquals((Double) data.get(HudValue.SESSION_CORPSE_FINE, HudValue.SESSION_CORPSE_FINE.defaultRange()) / hours,
				(Double) data.get(HudValue.SESSION_CORPSE_FINE, Range.PER_HOUR), 1e-9);
		assertEquals(data.get(HudValue.SESSION_SHAFTS, HudValue.SESSION_SHAFTS.defaultRange()), data.get(HudValue.SESSION_SHAFTS, Range.PER_HOUR),
				"shafts have no per hour range (shafts per hour is its own value), so the default shows");

		Box box = new Box();
		Line perHour = new Line(HudValue.SESSION_POWDER);
		perHour.range = Range.PER_HOUR;
		perHour.format = 1;
		box.lines.add(perHour);
		Line campfires = new Line(HudValue.SHAFT_CAMPFIRES);
		box.lines.add(campfires);
		perHour.valueColor = null;
		campfires.valueColor = null;
		assertNull(campfires.range, "values that aren't counted over shafts have no range");
		assertEquals(List.of("§7Shaft powder: §f137.2k/h", "§7Campfires: §f1"), HudBoxText.lines(box, HudData.EXAMPLE, false));
		HudData empty = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertEquals(List.of("§7Shaft powder: §f.§\uE000..§\uE001", "§7Campfires: §f-"), HudBoxText.lines(box, empty, false),
				"a rate thats not ready yet shows waiting dots, no /h");
		HudData later = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 1_200, ShaftNames.defaults());
		assertEquals("§7Shaft powder: §f...", HudBoxText.lines(box, later, false).getFirst(), "the dots count up over time");
		assertEquals(List.of(".§\uE000..§\uE001", "..§\uE000.§\uE001", "...", ".§\uE000..§\uE001"),
				List.of(HudBoxText.waiting(0), HudBoxText.waiting(600), HudBoxText.waiting(1_200), HudBoxText.waiting(1_800)));
		assertEquals("§7Shaft powder: §f.", HudBoxText.line(box, perHour, empty, false), "labels get no hidden dots");
		assertEquals(List.of("§7Shaft powder: §f§l.", "§f§l..", "§f§l gems"),
				HudBoxText.pieces("§7Shaft powder: §f§l." + HudBoxText.HIDE_START + ".." + HudBoxText.HIDE_END + " gems"),
				"hidden dots keep the colour + bold, so they measure the same as drawn");
		perHour.range = Range.ALL_TIME;
		assertEquals("§7Shaft powder: §f0", HudBoxText.lines(box, empty, false).getFirst(), "the HUD shows the real zero");
		assertEquals("§7Shaft powder: §f1.2M", HudBoxText.lines(box, empty, true).getFirst(), "the editor shows the example");
	}

	@Test
	void linesGetARangeWhenRead() {
		HudLayout read = HudLayout.fromJson("""
				{"version": 3, "boxes": [{"id": "a", "lines": [{"value": "session_powder"},
				  {"value": "session_shafts", "range": "SHAFT"}, {"value": "session_corpse_fine", "range": "ALL_TIME"},
				  {"value": "spawn_timer", "range": "SESSION"}, {"value": "session_powder", "range": "SOMETIME"}]}]}""");
		assertNull(read.boxes.getFirst().lines.getFirst().range, "no range stays none, so the file stays the same");
		List<Range> ranges = read.boxes.getFirst().lines.stream().map(Line::range).toList();
		assertEquals(java.util.Arrays.asList(Range.SESSION, Range.SESSION, Range.ALL_TIME, null, Range.SESSION), ranges,
				"old lines were session values; a range the value doesn't have, or none at all, falls back");
		assertEquals(read.toJson(), HudLayout.fromJson(read.toJson()).toJson());
	}

	private static List<JournalEntry> realJournal() throws Exception {
		Path path = Path.of(HudTest.class.getResource("/journal-2026-11-11.jsonl").toURI());
		JournalFile file = new JournalFile(path, Runnable::run);
		List<JournalEntry> entries = file.load().join();
		return entries;
	}

	@Test
	void longTimesGetHours() {
		assertEquals("1:16:14", HudFormat.format(Kind.TIME, 0, 4_574_000L));
		assertEquals("1h 16m", HudFormat.format(Kind.TIME, 1, 4_574_000L));
		assertEquals("4574s", HudFormat.format(Kind.TIME, 2, 4_574_000L));
		assertEquals("59:59", HudFormat.format(Kind.TIME, 0, 3_599_000L), "under an hour stays the same");
	}

	@Test
	void typedValues() throws Exception {
		List<JournalEntry> entries = realJournal();
		long end = new JournalStats.SessionSummary(entries.getFirst().sessionId(), entries).end();
		HudData data = live(entries, end);
		long lapis = new JournalStats.SessionSummary(entries.getFirst().sessionId(), entries).looted("Lapis");
		assertEquals(lapis, data.get(HudValue.CORPSES_LOOTED, Range.SESSION, "lapis"));
		assertEquals(lapis, data.get(HudValue.CORPSES_LOOTED, Range.ALL_TIME, "lapis"));
		assertEquals(lapis, data.get(HudValue.CORPSES_LOOTED, Range.SESSION), "lapis is the default");
		long lastUmber = CorpseCounts.looted(entries.getLast(), "Umber");
		assertEquals(lastUmber, data.get(HudValue.CORPSES_LOOTED, Range.SHAFT, "umber"), "the last shaft outside one");

		data.extra(new HudData.Extra(Map.of("jasper", 3L, "umber", 0L), 60_000L, 5_000L, 4L, Map.of()));
		Box box = new Box();
		Line dry = new Line(HudValue.DRYSTREAK);
		box.lines.add(dry);
		assertEquals("§7Jasper drystreak: §d3", HudBoxText.lines(box, data, false).getFirst(), "jasper in its own colour");
		dry.setTarget("umber");
		assertEquals("§7Umber drystreak: §60", HudBoxText.lines(box, data, false).getFirst(), "label + colour follow the type");
		dry.prefix = "Umb: ";
		dry.setTarget("jade");
		assertEquals("§7Umb: §a-", HudBoxText.lines(box, data, false).getFirst(), "typed label stays, no number = -");
		assertEquals("Umb: &a{dry:jade}", HudBoxText.basicText(box, dry), "advanced starts w/ the type");
		dry.setTarget("jasper");
		assertNull(dry.target, "the default type isnt saved");
		assertEquals(5_000L, data.get(HudValue.SINCE_LAST_SHAFT, null));
		assertEquals(60_000L, data.get(HudValue.SESSION_TIME, null));
		assertEquals(4L, data.get(HudValue.SHAFTS_TODAY, null));
	}

	private static HudData live(List<JournalEntry> entries, long now) {
		TextPatterns patterns = TextPatterns.defaults();
		return HudFixtures.live(SessionTotals.of(entries, patterns), Totals.of(entries, patterns),
				Totals.of(List.of(entries.getLast()), patterns), null, null, now, ShaftNames.defaults());
	}

	// made-up 20 min break after the last shaft, the live rates stop at 2 min of it
	@Test
	void liveRatesLeaveBreaksOut() throws Exception {
		List<JournalEntry> entries = realJournal();
		long end = entries.getLast().enteredAt() + entries.getLast().durationMillis();
		long start = entries.getFirst().sessionId();
		long now = end + 20 * 60_000;
		List<BreakEntry> breaks = List.of(new BreakEntry(BreakEntry.VERSION, start, end, now));
		long breakMillis = Breaks.counted(breaks, start, now);
		assertEquals(18 * 60_000, breakMillis);
		TextPatterns patterns = TextPatterns.defaults();
		HudData data = HudFixtures.live(SessionTotals.of(entries, patterns).withBreaks(breaks), Totals.of(entries, patterns), null, null, null, now,
				ShaftNames.defaults()).money(new HudData.Money(null, 12_000_000.0, null, 0, 3_000_000.0, null, start, breakMillis, null));
		long counted = now - start - breakMillis;
		assertEquals(15_000_000.0 * 3_600_000 / counted, (double) data.get(HudValue.COINS, Range.PER_HOUR), 1e-6);
		assertEquals(entries.size() * 3_600_000.0 / counted, (double) data.get(HudValue.SHAFTS_PER_HOUR, null), 1e-9);

		// session w/o shafts yet: 30 min, 10 of it not counted
		HudData noShafts = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, start + 30 * 60_000, ShaftNames.defaults())
				.money(new HudData.Money(null, null, null, 0, 3_000_000.0, 3_000_000.0, start, 10 * 60_000, null));
		assertEquals(9_000_000.0, (double) noShafts.get(HudValue.COINS, Range.PER_HOUR), 1e-6);
	}

	@Test
	void freeText() {
		Box box = HudLayout.defaults().boxes.get(1);
		Line spawn = box.lines.getFirst();
		assertEquals("Spawn: &v{spawn}", HudBoxText.basicText(box, spawn));
		spawn.advanced = true;
		spawn.text = "Spawn &8» &e{spawn} &7(avg {avg_spawn:seconds}) {nope} {spawn:all_time} &lB&aG &rx";
		assertEquals("§7Spawn §8» §e1:42 §7(avg 85s) {nope} {spawn:all_time} §lB§aG §r§7x",
				HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"plain text starts in the label colour; unknown tags and options stay as typed; a colour ends bold");
		Line powder = new Line(HudValue.SESSION_POWDER);
		powder.range = Range.ALL_TIME;
		powder.format = 1;
		assertEquals("Shaft powder: &b{shaft_powder:all_time:short}", HudBoxText.basicText(new Box(), powder),
				"a line's own colour is written as a code");
		powder.advanced = true;
		powder.text = "{shaft_powder:per_hour} / {shaft_powder:shaft:plain}";
		Box one = new Box();
		one.lines.add(powder);
		assertEquals("§7137,166/h / 7120", HudBoxText.lines(one, HudData.EXAMPLE, false).getFirst());
		powder.text = "{session_powder:per_hour} / {session_powder:shaft:plain}";
		assertEquals("§7137,166/h / 7120", HudBoxText.lines(one, HudData.EXAMPLE, false).getFirst(), "old tag names still read");
		assertEquals("§a§lJade§e§l 1.2M", HudBoxText.tags("{shaft_type} {shaft_powder:all_time:short}", HudData.EXAMPLE, false),
				"titles take options too");
	}

	@Test
	void lineColours() {
		Box box = HudLayout.defaults().boxes.get(1);
		Line spawn = box.lines.getFirst();
		spawn.labelColor = "b";
		spawn.valueColor = "a";
		spawn.prefix = "Next: ";
		assertEquals("§bNext: §a§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		spawn.colorRules = List.of("{spawn} > 1:00 &c");
		assertEquals("§bNext: §a§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "Easy lines have no rules");
		spawn.valueColor = null;
		assertEquals("§bNext: §f§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "no own colour = box colour");
		spawn.shown = false;
		assertEquals("§bNext: §f§l1:42", HudBoxText.line(box, spawn, HudData.EXAMPLE, false), "hidden lines still render for the editor");
	}

	@Test
	void basicTextLooksTheSame() {
		for (Box box : HudLayout.defaults().boxes) {
			box.lines.getFirst().labelColor = "6";
			// typed labels w/ & and { stay as typed
			box.lines.getLast().prefix = "R&D &&{spawn_timer} {x: ";
			for (Line line : box.lines) {
				String basic = HudBoxText.line(box, line, HudData.EXAMPLE, false);
				line.text = HudBoxText.basicText(box, line);
				line.advanced = true;
				assertEquals(visible(basic), visible(HudBoxText.line(box, line, HudData.EXAMPLE, false)),
						"free text starts out looking like the line: " + line.text);
			}
		}
	}

	// each char w/ its colour + bold, so §7§6a == §6a
	static String visible(String text) {
		StringBuilder out = new StringBuilder();
		char color = 'f';
		boolean bold = false;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '§' && i + 1 < text.length()) {
				char code = text.charAt(++i);
				if (code == 'l') bold = true;
				else if (code != 'r') {
					color = code;
					bold = false;
				}
				continue;
			}
			out.append(c).append(color).append(bold ? 'B' : '.');
		}
		return out.toString();
	}

	@Test
	void freeTextFollowsTheBox() {
		Box box = new Box();
		Line powder = new Line(HudValue.SESSION_POWDER);
		powder.valueColor = null;
		powder.text = HudBoxText.basicText(box, powder);
		powder.advanced = true;
		box.lines.add(powder);
		box.labelColor = "e";
		box.valueColor = "b";
		box.valueBold = true;
		assertEquals("§eShaft powder: §b§l43,436", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"&v and the start follow the box theme later on");
		powder.text = "&aX &rY &vZ";
		assertEquals("§e§aX §r§eY §b§lZ", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		powder.text = "R&&D &{spawn_timer} &a!";
		assertEquals("§eR&D {spawn_timer} §a!", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
	}

	@Test
	void tagProblems() {
		assertEquals(List.of("{gems_hr}: not a value: gems_hr", "{shaft_powder:weekly}: shaft_powder has no :weekly",
				"{coins + time_inside}: can't add coins and a time"),
				HudBoxText.problems("{spawn} {gems_hr} {shaft_powder:weekly} {shaft_powder:per_hour:short} {coins + time_inside}"));
		assertEquals(List.of(), HudBoxText.problems("no tags here"));
		assertEquals(1, HudBoxText.problems("&{gems_hr} &&{nope}").size(), "&{ is a plain {, && a plain &");
		assertEquals(List.of("{coin}: not a value: coin. Did you mean coins?"), HudBoxText.problems("{coin}"));
	}

	@Test
	void oldRulesShowAsAdvancedAndLookTheSame() {
		Box box = HudLayout.defaults().boxes.get(1);
		Line spawn = box.lines.getFirst();
		assertFalse(spawn.advancedMode());
		assertEquals("§7Spawn: §f§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		spawn.readyRule = true;
		assertTrue(spawn.advancedMode(), "an old ticked ready rule");
		assertEquals(List.of("{spawn} > {avg_spawn} &c"), spawn.advancedRules());
		assertEquals(visible("§7Spawn: §c§l1:42"), visible(HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst()),
				"1:42 is above the 1:25 average");
		HudLayout.ColorRule fast = new HudLayout.ColorRule();
		fast.when = HudLayout.ColorRule.When.UNDER;
		fast.value = 120;
		fast.color = "a";
		spawn.rules = new java.util.ArrayList<>(List.of(fast));
		assertEquals(List.of("{spawn} < 2:00 &a", "{spawn} > {avg_spawn} &c"), spawn.advancedRules(), "own rules first");
		assertEquals(visible("§7Spawn: §a§l1:42"), visible(HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst()));
		assertEquals("Spawn: &v{spawn}", spawn.advancedText(box), "starts as the line looked");

		spawn.settle(box);
		assertTrue(spawn.advanced);
		assertFalse(spawn.readyRule);
		assertNull(spawn.rules);
		assertEquals(List.of("{spawn} < 2:00 &a", "{spawn} > {avg_spawn} &c"), spawn.colorRules, "1st edit writes them out");
		assertEquals("Spawn: &v{spawn}", spawn.text);

		spawn.text = "&8» &e&l{spawn} &7avg {avg_spawn} {spawn:seconds}";
		assertEquals("§7§8» §e§l§a§l1:42§e§l §7avg 1:25 §a102s§7", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"the rule colours its value in any format, the rest keeps its colour");
		spawn.advanced = false;
		assertEquals("§7Spawn: §f§l1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "Easy again, rules kept but off");
		assertTrue(spawn.colorRules.size() == 2 && spawn.text != null);
	}

	@Test
	void turningAdvancedOnStartsFromThePreset() {
		Box box = HudLayout.presets().get(2).box().get();
		box.lines.add(new Line(HudValue.SHAFT_COLD));
		Line cold = box.lines.getLast();
		assertFalse(cold.readyRule, "presets and new lines have no rules on");
		cold.settle(box);
		cold.advanced = true;
		assertEquals(List.of("{cold} <= -80 &c", "{cold} <= -50 &e"), cold.colorRules);
		assertEquals("Cold: &b{cold}", cold.text);
		Line coins = new Line(HudValue.COINS);
		coins.settle(new Box());
		assertEquals(List.of(), coins.colorRules, "coins have no preset");
		cold.text = "x {cold}";
		cold.colorRules = new java.util.ArrayList<>(List.of("{cold} < 0 &a"));
		cold.advanced = false;
		cold.settle(box);
		assertEquals("x {cold}", cold.text, "kept text stays");
		assertEquals(List.of("{cold} < 0 &a"), cold.colorRules, "kept rules stay");
		cold.resetAdvanced(box);
		assertEquals("Cold: &b{cold}", cold.text);
		assertEquals(List.of("{cold} <= -80 &c", "{cold} <= -50 &e"), cold.colorRules);
	}

	@Test
	void ruleAgainstAnotherValue() {
		Box box = new Box();
		Line spawn = new Line(HudValue.SPAWN_TIMER);
		box.lines.add(spawn);
		spawn.advanced = true;
		spawn.text = "{spawn}";
		spawn.colorRules = List.of("{spawn} > {avg_spawn} &6");
		assertEquals("§7§61:42§7", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "example 1:42 is over the 1:25 average");
		spawn.colorRules = List.of("{spawn} < {avg_spawn} &6");
		assertEquals("§71:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		spawn.colorRules = List.of("{spawn} > {avg_spawn} &6");
		HudData noAverage = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, 102_000L, null, 0, ShaftNames.defaults());
		assertEquals("§71:42", HudBoxText.lines(box, noAverage, false).getFirst(), "no average yet, rule skipped");
		assertTrue(HudBoxText.isLimitTag(" {session_powder:all_time} ", HudValue.SESSION_POWDER));
		assertTrue(HudBoxText.isLimitTag("{shafts_per_hour}", HudValue.SESSION_SHAFTS), "counts vs decimals is fine");
		assertFalse(HudBoxText.isLimitTag("{shaft_type}", HudValue.SESSION_POWDER), "text values cant be compared");
		assertFalse(HudBoxText.isLimitTag("{session_powder}", HudValue.SHAFT_COLD), "cold vs powder makes no sense");
		assertFalse(HudBoxText.isLimitTag("{average_spawn}", HudValue.FPS), "secs vs a count neither");
		assertFalse(HudBoxText.isLimitTag("{nope}", HudValue.FPS));
		assertFalse(HudBoxText.isLimitTag("90", HudValue.FPS));
	}

	@Test
	void coldThresholds() {
		List<HudRules.Rule> rules = HudRules.parse(HudRules.preset(HudValue.SHAFT_COLD));
		for (int[] c : new int[][] {{-49, 0}, {-50, 'e'}, {-79, 'e'}, {-80, 'c'}, {-99, 'c'}}) {
			HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
					.cold(c[0]);
			assertEquals(c[1] == 0 ? null : String.valueOf((char) c[1]), HudRules.colors(rules, HudBoxText.values(data, false)).get("cold"),
					"cold " + c[0]);
		}
	}

	@Test
	void thresholdsAsTyped() {
		assertEquals(90.0, HudFormat.parseThreshold(Kind.TIME, "1:30"));
		assertEquals(90.0, HudFormat.parseThreshold(Kind.TIME, " 90 "));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "1:3"));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "1:75"));
		assertEquals(-50.0, HudFormat.parseThreshold(Kind.TEXT, "-50"));
		assertEquals(43436.0, HudFormat.parseThreshold(Kind.INTEGER, "43,436"));
		assertNull(HudFormat.parseThreshold(Kind.DECIMAL, "abc"));
		assertEquals("1:30", HudFormat.thresholdText(Kind.TIME, 90));
		assertEquals("-50", HudFormat.thresholdText(Kind.TEXT, -50));
		assertEquals("2.5", HudFormat.thresholdText(Kind.DECIMAL, 2.5));
	}

	@Test
	void colourSettingsAreRepaired() {
		HudLayout read = HudLayout.fromJson("""
				{"boxes": [{"id": "a", "lines": [
				  {"value": "spawn_timer", "readyRule": true, "rules": [null, {"when": "SIDEWAYS", "value": 90, "color": "z", "than": "{shaft_type}"}]},
				  {"value": "shaft_corpses", "readyRule": true, "rules": [{"value": 1}]},
				  {"value": "shaft_cold", "advanced": true}]}]}""");
		List<Line> lines = read.boxes.getFirst().lines;
		assertTrue(lines.get(0).readyRule);
		assertEquals(1, lines.get(0).rules.size());
		assertEquals(HudLayout.ColorRule.When.OVER, lines.get(0).rules.getFirst().when);
		assertEquals("c", lines.get(0).rules.getFirst().color);
		assertNull(lines.get(0).rules.getFirst().than, "a text value cant be a limit, back to the number");
		assertFalse(lines.get(1).readyRule, "corpses have no ready-made rule");
		assertNull(lines.get(1).rules, "corpses aren't a number");
		assertFalse(lines.get(2).advanced, "free text without text falls back to the basic line");
		assertEquals(read.toJson(), HudLayout.fromJson(read.toJson()).toJson());
	}

	// reviewers probe threw StackOverflowError on every refresh
	@Test
	void hostileTagsShowAsTypedAndTheBoxStillDraws() {
		String chain = "{coins" + "+1".repeat(2000) + "}";
		String deep = "{" + "(".repeat(2000) + "coins" + ")".repeat(2000) + "}";
		Box box = HudLayout.defaults().boxes.get(1);
		List<String> before = HudBoxText.lines(box, HudData.EXAMPLE, true);
		Line bad = new Line(HudValue.COINS);
		bad.advanced = true;
		bad.text = "Bad " + chain + " " + deep;
		bad.colorRules = new ArrayList<>(List.of(chain + " > 1 &c", deep + " < 1 &a"));
		box.lines.add(1, bad);
		List<String> lines = HudBoxText.lines(box, HudData.EXAMPLE, true);
		assertEquals(List.of(before.get(0), "§7" + bad.text, before.get(1), before.get(2)), lines, "like any tag mistake: as typed");
		assertEquals(List.of(chain + ": too long", deep + ": too long"), HudBoxText.problems(bad.text));
		assertEquals(List.of("too long", "too long"), HudRules.parse(bad.colorRules).stream().map(HudRules.Rule::error).toList());
	}

	@Test
	void hostileRulesLoad() {
		String chain = "{coins" + "+1".repeat(2000) + "}";
		String deep = "{" + "(".repeat(2000) + "coins" + ")".repeat(2000) + "}";
		HudLayout read = HudLayout.fromJson("""
				{"boxes": [{"id": "a", "lines": [
				  {"value": "spawn_timer", "rules": [{"value": 90, "than": "%s"}, {"value": 90, "than": "%s"}]},
				  {"value": "coins", "advanced": true, "text": "%s", "colorRules": ["%s > 1 &c"]}]}]}""".formatted(chain, deep, chain, deep));
		List<Line> lines = read.boxes.getFirst().lines;
		assertNull(lines.get(0).rules.get(0).than, "not a limit, back to the number like any bad one");
		assertNull(lines.get(0).rules.get(1).than);
		assertEquals(2, HudBoxText.lines(read.boxes.getFirst(), HudData.EXAMPLE, false).size());
	}

	@Test
	void capsCutOnlyWhatsFarTooLong() {
		HudLayout layout = new HudLayout();
		for (int i = 0; i < HudLayout.MAX_BOXES + 5; i++) layout.boxes.add(new Box());
		Box first = layout.boxes.getFirst();
		first.title = "t".repeat(HudLayout.MAX_TEXT + 1);
		layout.boxes.get(1).title = "t".repeat(HudLayout.MAX_TEXT);
		for (int i = 0; i < HudLayout.MAX_LINES + 3; i++) first.lines.add(new Line(HudValue.COINS));
		Line line = first.lines.getFirst();
		line.prefix = "p".repeat(5000);
		line.advanced = true;
		line.text = "{coins}" + "x".repeat(5000);
		line.colorRules = new ArrayList<>();
		line.colorRules.add("{coins} > 1 &c" + " ".repeat(5000));
		for (int i = 0; i < HudLayout.MAX_RULES + 1; i++) line.colorRules.add("{coins} > 1 &c");

		HudLayout read = HudLayout.fromJson(layout.toJson());
		assertEquals(5 + 1 + 3 + 1 + 1 + 2 + 1, read.cutParts());
		assertEquals(HudLayout.MAX_BOXES, read.boxes.size());
		Box box = read.boxes.getFirst();
		assertEquals(HudLayout.MAX_TEXT, box.title.length());
		assertEquals(HudLayout.MAX_TEXT, read.boxes.get(1).title.length(), "at the cap = kept");
		assertEquals(HudLayout.MAX_LINES, box.lines.size());
		Line cut = box.lines.getFirst();
		assertEquals(HudLayout.MAX_TEXT, cut.prefix.length());
		assertTrue(cut.text.startsWith("{coins}xxx"));
		assertEquals(HudLayout.MAX_TEXT, cut.text.length());
		assertEquals(HudLayout.MAX_RULES, cut.colorRules.size());
		assertEquals(HudLayout.MAX_TEXT, cut.colorRules.getFirst().length());
		assertEquals(0, HudLayout.fromJson(read.toJson()).cutParts(), "cut once");
	}

	@Test
	void oldFreeTextRuleFollowsTheRangeInItsText() {
		Line powder = new Line(HudValue.SESSION_POWDER);
		powder.valueColor = null;
		HudLayout.ColorRule big = new HudLayout.ColorRule();
		big.value = 1_000_000;
		powder.rules = new java.util.ArrayList<>(List.of(big));
		Box box = new Box();
		box.lines.add(powder);
		assertEquals("§7Shaft powder: §f43,436", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "the session is under 1M");
		powder.advanced = true;
		powder.text = "Powder {session_powder:all_time} ({session_powder})";
		assertEquals(Range.ALL_TIME, HudBoxText.ruleRange(powder));
		assertEquals(List.of("{shaft_powder:all_time} > 1000000 &c"), powder.advancedRules());
		assertEquals("§7Powder §c1,204,310§7 (43,436)", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(),
				"the first tag of the line's value sets the range the rule looks at and colours");
		powder.text = "Powder {shaft_type}";
		assertEquals(Range.SESSION, HudBoxText.ruleRange(powder), "no tag of its own: the line's range");
	}

	@Test
	void timeRulesCompareTheShownSeconds() {
		List<HudRules.Rule> rules = HudRules.parse(List.of("{spawn} > 1:30 &c"));
		HudData at90 = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, 90_900L, null, 0, ShaftNames.defaults());
		assertNull(HudRules.colors(rules, HudBoxText.values(at90, false)).get("spawn"), "1:30.9 shows as 1:30, which isn't over 1:30");
		HudData at91 = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, 91_000L, null, 0, ShaftNames.defaults());
		assertEquals("c", HudRules.colors(rules, HudBoxText.values(at91, false)).get("spawn"));
	}

	@Test
	void pityShowsBothNumbers() {
		HudData none = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.pity(new TabListParser.Pity(1_498, 2_000));
		assertEquals("1,498/2,000", HudBoxText.text(HudValue.PITY, null, 0, data, false));
		assertEquals("1.5k/2.0k", HudBoxText.text(HudValue.PITY, null, 1, data, false), "short format on both");
		assertEquals("-", HudBoxText.text(HudValue.PITY, null, 0, none, false), "no widget");
		assertEquals("1,498/2,000", HudBoxText.text(HudValue.PITY, null, 0, none, true), "editor example");
		assertEquals(1498.0, data.number(HudValue.PITY, null, false), "maths use the 1st number");

		Line line = new Line(HudValue.PITY);
		Box box = new Box();
		box.lines.add(line);
		assertEquals("§7Pity: §e1,498/2,000", HudBoxText.lines(box, data, false).getFirst());
		assertEquals("§7Pity: §e-", HudBoxText.lines(box, none, false).getFirst());
		line.advanced = true;
		line.text = "Shaft &v{pity} &r({pity:short})";
		assertEquals("§7Shaft §f1,498/2,000 §r§7(1.5k/2.0k)", HudBoxText.lines(box, data, false).getFirst());

		List<HudRules.Rule> rules = HudRules.parse(List.of("{pity} >= 1500 &c"));
		assertNull(HudRules.colors(rules, HudBoxText.values(data, false)).get("pity"));
		HudData at1500 = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.pity(new TabListParser.Pity(1_500, 2_000));
		assertEquals("c", HudRules.colors(rules, HudBoxText.values(at1500, false)).get("pity"));
	}

	private static HudData pityData(long now, long max) {
		return HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.pity(new TabListParser.Pity(now, max));
	}

	private static Box pityBox(Line line) {
		Box box = new Box();
		box.showTitle = false;
		box.lines.add(line);
		return box;
	}

	@Test
	void showAsIsKeptAndOnlyWrittenWhenSet() {
		String old = HudLayout.defaults().toJson();
		assertFalse(old.contains("showAs"), "null isn't written");
		HudLayout layout = HudLayout.fromJson(old);
		Line pity = new Line(HudValue.PITY);
		pity.showAs(HudLayout.ShowAs.BOTH);
		layout.boxes.getFirst().lines.add(pity);
		Line odd = new Line(HudValue.FPS);
		odd.showAs = "sparkline";
		layout.boxes.getFirst().lines.add(odd);
		String json = layout.toJson();
		assertEquals(json, HudLayout.fromJson(json).toJson(), "round trip, unknown kept");
		Line read = HudLayout.fromJson(json).boxes.getFirst().lines.get(layout.boxes.getFirst().lines.size() - 2);
		assertEquals(HudLayout.ShowAs.BOTH, read.showAs());
		assertEquals(HudLayout.ShowAs.TEXT, odd.showAs(), "unknown draws as text");
		odd.showAs = "graph";
		assertEquals(HudLayout.ShowAs.TEXT, odd.showAs(), "fps has no graph");
		pity.showAs(HudLayout.ShowAs.TEXT);
		assertNull(pity.showAs, "text = not written");
	}

	@Test
	void pityRows() {
		HudData data = pityData(1_498, 2_000);
		Line line = new Line(HudValue.PITY);
		Box box = pityBox(line);
		assertEquals(List.of(HudRow.text("§7Pity: §e1,498/2,000")), HudBoxText.rows(box, data, false), "text = as before");
		int yellow = 0xFFFFFF55;
		line.showAs(HudLayout.ShowAs.BOTH);
		assertEquals(List.of(new HudRow("§7Pity: §e1,498/2,000", new HudGraph.Bar(0.749, yellow))), HudBoxText.rows(box, data, false));
		assertEquals(List.of("§7Pity: §e1,498/2,000"), HudBoxText.lines(box, data, false), "lines = the rows' text");
		line.showAs(HudLayout.ShowAs.GRAPH);
		assertEquals(List.of(new HudRow("§7Pity", new HudGraph.Bar(0.749, yellow))), HudBoxText.rows(box, data, false));
		line.prefix = "";
		assertEquals(List.of(new HudRow(null, new HudGraph.Bar(0.749, yellow))), HudBoxText.rows(box, data, false), "no label = bar only");
		assertEquals(List.of(), HudBoxText.lines(box, data, false));
		line.prefix = "Pity: ";
		line.valueColor = null;
		box.valueColor = "a";
		assertEquals(0xFF55FF55, ((HudGraph.Bar) HudBoxText.rows(box, data, false).getFirst().graph()).argb(), "box colour");

		line.advanced = true;
		line.text = "Shaft {pity}";
		assertEquals(List.of(new HudRow(null, new HudGraph.Bar(0.749, 0xFF55FF55))), HudBoxText.rows(box, data, false), "advanced bar hides the text");
		line.showAs(HudLayout.ShowAs.BOTH);
		assertEquals("§7Shaft 1,498/2,000", HudBoxText.rows(box, data, false).getFirst().text());

		HudData none = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		line.advanced = false;
		line.showAs(HudLayout.ShowAs.GRAPH);
		assertEquals(List.of(HudRow.text("§7Pity: §a-")), HudBoxText.rows(box, none, false), "no data: no bar, the text stays");
		assertEquals(0.749, ((HudGraph.Bar) HudBoxText.rows(box, none, true).getFirst().graph()).fill(), "editor example bar");
	}

	@Test
	void pityBarFill() {
		Line line = new Line(HudValue.PITY);
		line.showAs(HudLayout.ShowAs.BOTH);
		Box box = pityBox(line);
		HudGraph.Bar empty = (HudGraph.Bar) HudBoxText.rows(box, pityData(0, 2_000), false).getFirst().graph();
		assertEquals(0, empty.fill());
		assertEquals(0, empty.filled(60));
		HudGraph.Bar part = (HudGraph.Bar) HudBoxText.rows(box, pityData(1_498, 2_000), false).getFirst().graph();
		assertEquals(45, part.filled(60), "74.9% of 60 px");
		HudGraph.Bar over = (HudGraph.Bar) HudBoxText.rows(box, pityData(2_400, 2_000), false).getFirst().graph();
		assertEquals(1, over.fill(), "clamped");
		assertNull(HudBoxText.rows(box, pityData(5, 0), false).getFirst().graph(), "no max, no bar");
	}

	@Test
	void rowGeometry() {
		HudRow text = HudRow.text("a");
		HudRow both = new HudRow("a", new HudGraph.Bar(0.5, -1));
		HudRow bar = new HudRow(null, new HudGraph.Bar(0.5, -1));
		assertEquals(9 + 1 + 9, HudRow.height(List.of(text, text), 9, 1), "text rows as before");
		assertEquals(9 + 1 + 9 + 1 + 3, HudRow.height(List.of(text, both), 9, 1), "1 px above, no gap under the last");
		assertEquals(9 + 1 + 3 + 2 + 9, HudRow.height(List.of(both, text), 9, 1), "2 px under a bar");
		assertEquals(9 + 1 + 1 + 3, HudRow.height(List.of(text, bar), 9, 1));
		assertEquals(9 + 1 + 16, HudRow.height(List.of(new HudRow("a", new HudGraph.Chart(List.of(), null))), 9, 1), "chart 16 px");
		assertEquals(0, HudRow.minWidth(List.of(text)));
		assertEquals(60, HudRow.minWidth(List.of(text, bar)));
	}

	private static JournalEntry timed(String id, Long search) {
		return EntryBuilder.entry().shaftId(id).searchMillis(search).build();
	}

	@Test
	void lastSpawnsSkipUntimedAndKeepTheNewest12() {
		List<JournalEntry> entries = new ArrayList<>();
		for (int i = 1; i <= 14; i++) entries.add(timed("JADE_1", i * 1_000L));
		entries.add(timed("JASP_1", null));
		List<HudData.Spawn> spawns = HudData.lastSpawns(entries);
		assertEquals(12, spawns.size());
		assertEquals(new HudData.Spawn(3_000, "JADE"), spawns.getFirst(), "oldest left, 2 oldest dropped");
		assertEquals(new HudData.Spawn(14_000, "JADE"), spawns.getLast(), "untimed one skipped");
		assertEquals(List.of(new HudData.Spawn(5_000, "TOPA")), HudData.lastSpawns(List.of(timed("TOPA_1", 5_000L))));
		assertEquals(List.of(), HudData.lastSpawns(List.of(timed("TOPA_1", null))));
	}

	@Test
	void spawnChartScale() {
		ShaftNames names = ShaftNames.defaults();
		List<HudData.Spawn> spawns = List.of(new HudData.Spawn(40_000, "JADE"), new HudData.Spawn(80_000, "JASP"),
				new HudData.Spawn(0, "ONYX"));
		HudGraph.Chart chart = HudBoxText.spawnChart(spawns, 60_000.0, names);
		assertEquals(List.of(new HudGraph.Column(0.5, 0xFF000000 | names.rgb("JADE")), new HudGraph.Column(1, 0xFF000000 | names.rgb("JASP")),
				new HudGraph.Column(0, 0xFF000000 | names.rgb("ONYX"))), chart.columns(), "longest = full height");
		assertEquals(0.75, chart.line());
		assertEquals(8, chart.columnHeight(0));
		assertEquals(16, chart.columnHeight(1));
		assertEquals(1, chart.columnHeight(2), "never under 1 px");
		assertEquals(4, chart.lineY(), "12 px up from the bottom");
		assertEquals(14, chart.width(), "3 columns of 4 + 2 gaps");
		HudGraph.Chart over = HudBoxText.spawnChart(spawns, 160_000.0, names);
		assertEquals(0.25, over.columns().get(0).height(), "avg over the longest sets the scale");
		assertEquals(0, over.lineY());
		assertNull(HudBoxText.spawnChart(spawns, null, names).line(), "no average, no line");
		assertNull(HudBoxText.spawnChart(List.of(), 60_000.0, names), "no shafts, no chart");
		assertEquals(1, HudBoxText.spawnChart(List.of(new HudData.Spawn(30_000, "JADE")), null, names).columns().size());
		assertEquals(59, HudBoxText.spawnChart(HudData.EXAMPLE.recentSpawns(false), null, names).width(), "12 columns");
	}

	@Test
	void averageSpawnChartRows() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.recentSpawns(List.of(new HudData.Spawn(40_000, "JADE"), new HudData.Spawn(80_000, "JASP")));
		data.set(HudValue.AVERAGE_SPAWN, Range.SESSION, 60_000L);
		Line line = new Line(HudValue.AVERAGE_SPAWN);
		line.prefix = "Avg: ";
		line.valueColor = null;
		Box box = pityBox(line);
		line.showAs(HudLayout.ShowAs.BOTH);
		HudRow row = HudBoxText.rows(box, data, false).getFirst();
		assertEquals("§7Avg: §f1:00", row.text());
		assertEquals(0.75, ((HudGraph.Chart) row.graph()).line());
		line.showAs(HudLayout.ShowAs.GRAPH);
		assertEquals("§7Avg", HudBoxText.rows(box, data, false).getFirst().text());
		line.setRange(Range.SHAFT);
		assertEquals(HudLayout.ShowAs.TEXT, line.showAs(), "this shaft has no chart");
		assertEquals("graph", line.showAs, "kept for the other periods");
		assertNull(HudBoxText.rows(box, data, false).getFirst().graph());
		line.setRange(Range.ALL_TIME);
		assertNull(((HudGraph.Chart) HudBoxText.rows(box, data, false).getFirst().graph()).line(), "no all-time avg here");
		HudData none = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertNull(HudBoxText.rows(box, none, false).getFirst().graph(), "no shafts, no chart");
		assertEquals(12, ((HudGraph.Chart) HudBoxText.rows(box, none, true).getFirst().graph()).columns().size(), "editor example");
	}

	@Test
	void efficiencyLineRows() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.efficiency(66L).efficiencyTrack(List.of(0.5, 0.8, 0.66), 0.7);
		Line line = new Line(HudValue.EFFICIENCY);
		line.valueColor = null;
		Box box = pityBox(line);
		line.showAs(HudLayout.ShowAs.BOTH);
		HudRow row = HudBoxText.rows(box, data, false).getFirst();
		assertEquals("§7Efficiency: §f66%", row.text());
		HudGraph.Line chart = (HudGraph.Line) row.graph();
		assertEquals(List.of(0.5, 0.8, 0.66), chart.points());
		assertEquals(0.7, chart.usual());
		line.showAs(HudLayout.ShowAs.GRAPH);
		assertEquals("§7Efficiency", HudBoxText.rows(box, data, false).getFirst().text());
		HudData one = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.efficiency(66L).efficiencyTrack(List.of(0.66), null);
		assertNull(HudBoxText.rows(box, one, false).getFirst().graph(), "one point is no line yet");
		assertEquals(11, ((HudGraph.Line) HudBoxText.rows(box, one, true).getFirst().graph()).points().size(), "editor example");
	}

	@Test
	void efficiencyLineMaths() {
		HudGraph.Line line = HudGraph.Line.of(List.of(0.0, 1.0), 0.5, 0xFFFFFFFF);
		assertEquals(15, line.yAt(0), "lowest point at the bottom row");
		assertEquals(0, line.yAt(HudGraph.Line.WIDTH - 1), "highest at the top row");
		assertEquals(7, line.usualY(), "middle = row 15 - 7.5 rounded up");
		assertEquals(HudGraph.Line.WIDTH, line.minWidth());
		HudGraph.Line steady = HudGraph.Line.of(List.of(0.68, 0.70), null, 0);
		assertEquals(0.59, steady.low(), 1e-9, "at least 20 points tall, around the points");
		assertEquals(0.79, steady.high(), 1e-9);
		HudGraph.Line real = HudGraph.Line.of(List.of(0.74, 0.81, 0.52), 0.68, 0);
		assertEquals(0.52, real.low(), 1e-9, "wide enough on its own");
		assertEquals(0, real.y(0.81));
		HudGraph.Line one = HudGraph.Line.of(List.of(0.7), null, 0);
		assertEquals(one.yAt(0), one.yAt(30), "one point is flat");
	}

	@Test
	void coldIsANumber() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults()).cold(-12);
		assertEquals("-12❄", HudBoxText.text(HudValue.SHAFT_COLD, null, 0, data, false));
		assertEquals(-12.0, data.number(HudValue.SHAFT_COLD, null, false));
		assertNull(data.number(HudValue.SHAFT_TYPE, null, true), "text has no number");
	}

	@Test
	void thresholdCommasAndMinus() {
		assertEquals(2.5, HudFormat.parseThreshold(Kind.DECIMAL, "2,5"), "a lone comma is a decimal point");
		assertEquals(1500.0, HudFormat.parseThreshold(Kind.INTEGER, "1,500"), "groups of three are thousands");
		assertEquals(1_204_310.0, HudFormat.parseThreshold(Kind.INTEGER, "1,204,310"));
		assertNull(HudFormat.parseThreshold(Kind.INTEGER, "1,2,3"));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "1-2:30"));
		assertEquals(-30.0, HudFormat.parseThreshold(Kind.TIME, "-0:30"));
		assertNull(HudFormat.parseThreshold(Kind.DECIMAL, "1e5"));
		assertNull(HudFormat.parseThreshold(Kind.DECIMAL, "NaN"));
		assertNull(HudFormat.parseThreshold(Kind.TIME, "99999999999999999999:00"));
		assertEquals(0.5, HudFormat.parseThreshold(Kind.DECIMAL, ".5"));
	}

	@Test
	void outsideCoinsAddToSessionHourAndAllTime() throws Exception {
		List<JournalEntry> entries = realJournal();
		long end = entries.getLast().enteredAt() + entries.getLast().durationMillis();
		long start = entries.getFirst().sessionId();
		HudData data = live(entries, end).money(new HudData.Money(-250_000.0, 12_000_000.0, 40_000_000.0, 20, 3_000_000.0,
				9_000_000.0, start, 0, null));
		SessionSummary session = new SessionSummary(start, entries);
		assertEquals(-250_000.0, data.get(HudValue.COINS, Range.SHAFT));
		assertEquals(15_000_000.0, data.get(HudValue.COINS, Range.SESSION));
		assertEquals(49_000_000.0, data.get(HudValue.COINS, Range.ALL_TIME));
		assertEquals(session.perHour(15_000_000, end), data.get(HudValue.COINS, Range.PER_HOUR));
		assertEquals(3_000_000.0, data.get(HudValue.OUTSIDE_COINS, Range.SESSION));
		assertEquals(session.perHour(3_000_000, end), data.get(HudValue.OUTSIDE_COINS, Range.PER_HOUR));
		assertEquals(9_000_000.0, data.get(HudValue.OUTSIDE_COINS, Range.ALL_TIME));
		// shafts only
		assertEquals(12_000_000.0 / entries.size(), data.get(HudValue.COINS_PER_SHAFT, Range.SESSION));
		assertEquals(2_000_000.0, data.get(HudValue.COINS_PER_SHAFT, Range.ALL_TIME));

		// in a shaft: its live coins count in session, per hour + all-time, not per shaft
		HudData inside = live(entries, end).money(new HudData.Money(2_000_000.0, 12_000_000.0, 40_000_000.0, 20, 3_000_000.0,
				9_000_000.0, start, 0, 2_000_000.0));
		assertEquals(17_000_000.0, inside.get(HudValue.COINS, Range.SESSION));
		assertEquals(session.perHour(17_000_000, end), inside.get(HudValue.COINS, Range.PER_HOUR));
		assertEquals(51_000_000.0, inside.get(HudValue.COINS, Range.ALL_TIME));
		assertEquals(12_000_000.0 / entries.size(), inside.get(HudValue.COINS_PER_SHAFT, Range.SESSION));
		assertEquals(2_000_000.0, inside.get(HudValue.COINS_PER_SHAFT, Range.ALL_TIME));

		// session w/o shafts yet, mined for 30 min
		HudData noShafts = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, start + 30 * 60_000, ShaftNames.defaults()).money(new HudData.Money(null, null, null, 0, 3_000_000.0, 3_000_000.0,
				start, 0, null));
		assertEquals(3_000_000.0, noShafts.get(HudValue.COINS, Range.SESSION));
		assertEquals(6_000_000.0, noShafts.get(HudValue.COINS, Range.PER_HOUR));
		assertEquals(3_000_000.0, noShafts.getOrExample(HudValue.COINS, Range.SESSION), "real value, not the editor example");
		Box box = new Box();
		box.lines.add(new Line(HudValue.OUTSIDE_COINS));
		assertEquals("§7Outside coins: §63.0M", HudBoxText.lines(box, noShafts, false).getFirst());
	}

	@Test
	void coinValues() throws Exception {
		List<JournalEntry> entries = realJournal();
		long end = entries.getLast().enteredAt() + entries.getLast().durationMillis();
		HudData data = live(entries, end).money(new HudData.Money(-250_000.0, 12_000_000.0, 40_000_000.0, 20, null, null, null, 0, null));
		SessionSummary session = new SessionSummary(entries.getFirst().sessionId(), entries);
		assertEquals(-250_000.0, data.get(HudValue.COINS, Range.SHAFT));
		assertEquals(12_000_000.0, data.get(HudValue.COINS, HudValue.COINS.defaultRange()));
		assertEquals(session.perHour(12_000_000, end), data.get(HudValue.COINS, Range.PER_HOUR));
		assertEquals(12_000_000.0 / entries.size(), data.get(HudValue.COINS_PER_SHAFT, HudValue.COINS_PER_SHAFT.defaultRange()));
		assertEquals(2_000_000.0, data.get(HudValue.COINS_PER_SHAFT, Range.ALL_TIME));

		HudData noPrices = live(entries, end).money(new HudData.Money(null, null, null, 20, null, null, null, 0, null));
		assertNull(noPrices.get(HudValue.COINS, HudValue.COINS.defaultRange()));
		assertNull(noPrices.get(HudValue.COINS_PER_SHAFT, HudValue.COINS_PER_SHAFT.defaultRange()));
		Box box = new Box();
		box.lines.add(new Line(HudValue.COINS));
		assertEquals("§7Coins: §6-", HudBoxText.lines(box, noPrices, false).getFirst(), "no prices yet");
		assertEquals("§7Coins: §612.0M", HudBoxText.lines(box, data, false).getFirst());
		box.lines.getFirst().format = 1;
		assertEquals("§7Coins: §612,000,000", HudBoxText.lines(box, data, false).getFirst());
		box.lines.getFirst().range = Range.SHAFT;
		box.lines.getFirst().format = 0;
		assertEquals("§7Coins: §6-250.0k", HudBoxText.lines(box, data, false).getFirst());
		assertEquals("12.0M", HudBoxText.tags("{coins}", data, false));
		assertEquals("12000000", HudBoxText.tags("{coins:plain}", data, false));
	}

	@Test
	void coinThresholds() {
		assertEquals(List.of("0.0M", "#,##0", "0"), HudFormat.options(Kind.COINS));
		assertEquals(1_500_000.0, HudFormat.parseThreshold(Kind.COINS, "1.5M"));
		assertEquals(1_500_000.0, HudFormat.parseThreshold(Kind.COINS, "1,5m"));
		assertEquals(250_000.0, HudFormat.parseThreshold(Kind.COINS, "250k"));
		assertEquals(2_000_000_000.0, HudFormat.parseThreshold(Kind.COINS, "2b"));
		assertEquals(-500_000.0, HudFormat.parseThreshold(Kind.COINS, "-500k"));
		assertEquals(1_204_310.0, HudFormat.parseThreshold(Kind.COINS, "1,204,310"));
		assertNull(HudFormat.parseThreshold(Kind.INTEGER, "1.5M"), "only coins take k, M and B");
		assertNull(HudFormat.parseThreshold(Kind.COINS, "1.5x"));
		assertEquals("2.4M", HudFormat.thresholdText(Kind.COINS, 2_400_000));
		assertEquals("250k", HudFormat.thresholdText(Kind.COINS, 250_000));
		assertEquals("-3M", HudFormat.thresholdText(Kind.COINS, -3_000_000));
		assertEquals("2431000", HudFormat.thresholdText(Kind.COINS, 2_431_000), "not rounded away");
		assertEquals("950", HudFormat.thresholdText(Kind.COINS, 950));
		assertTrue(HudColors.comparable(HudValue.COINS, HudValue.COINS_PER_SHAFT));
		assertFalse(HudColors.comparable(HudValue.COINS, HudValue.SESSION_POWDER), "coins only vs coins");
	}

	@Test
	void totalsAddUp() {
		assertEquals(new Totals(3, 30, 1.5, 0, 0, 480, Map.of(), 0),
				new Totals(1, 10, 0.5, 0, 0, 160, Map.of(), 0).plus(new Totals(2, 20, 1, 0, 0, 320, Map.of(), 0)));
	}

	@Test
	void minedMetalInTheTierSetting() {
		List<JournalEntry> entries = List.of(
				EntryBuilder.entry().shaftId("TUNG_1").metal(new MetalYield("Tungsten", 48_211, 2, 0)).build(),
				EntryBuilder.entry().enteredAt(600_000).build(),
				EntryBuilder.entry().shaftId("UMBE_1").enteredAt(1_200_000).metal(new MetalYield("Umber", 1_000, 0, 0)).build());
		TextPatterns patterns = TextPatterns.defaults();
		SessionTotals session = SessionTotals.of(entries, patterns);
		Totals all = Totals.of(entries, patterns);
		Totals last = Totals.of(List.of(entries.getLast()), patterns);
		HudData enchanted = HudData.live(session, all, last, null, null, 1_260_000, ShaftNames.defaults(), MetalYield.ENCHANTED);
		assertEquals(49_531 / 160.0, (Double) enchanted.get(HudValue.MINED_METAL, Range.SESSION), 1e-9, "both metals, gem shaft adds 0");
		assertEquals(49_531 / 160.0, (Double) enchanted.get(HudValue.MINED_METAL, Range.ALL_TIME), 1e-9);
		assertEquals(1_000 / 160.0, (Double) enchanted.get(HudValue.MINED_METAL, Range.SHAFT), 1e-9, "the last shaft outside one");
		HudData raw = HudData.live(session, all, last, null, null, 1_260_000, ShaftNames.defaults(), MetalYield.RAW);
		assertEquals(49_531.0, (Double) raw.get(HudValue.MINED_METAL, Range.SESSION), 1e-9);
		assertEquals(49_531 / 25_600.0, (Double) HudData.live(session, all, last, null, null, 1_260_000, ShaftNames.defaults(),
				MetalYield.REFINED).get(HudValue.MINED_METAL, Range.ALL_TIME), 1e-9);
		assertEquals("mined_metal", HudValue.MINED_METAL.tag());
		assertNotNull(HudData.EXAMPLE.get(HudValue.MINED_METAL, Range.PER_HOUR));

		// saved lines load back the same, no .old backup
		HudLayout layout = HudLayout.defaults();
		for (Range range : HudValue.MINED_METAL.ranges()) {
			Line line = new Line(HudValue.MINED_METAL);
			line.range = range;
			layout.boxes.getFirst().lines.add(line);
		}
		String json = layout.toJson();
		assertEquals(json, HudLayout.fromJson(json).toJson());
	}

	// recording 3
	@Test
	void shaftTotalsMatchTheSavedEntry() throws Exception {
		Path recording = RecordingReplay.RECORDINGS.resolve("2026-11-11_14-13-53.jsonl");
		assumeTrue(Files.exists(recording), "Recording not available: " + recording);
		TextPatterns patterns = TextPatterns.defaults();
		List<Totals> inside = new java.util.ArrayList<>();
		for (ShaftEvent event : RecordingReplay.replay(recording)) {
			if (!(event instanceof ShaftEvent.ShaftLeft left)) continue;
			JournalEntry entry = JournalEntry.of(left.shaft(), 0, null, patterns);
			inside.add(Totals.of(left.shaft(), patterns));
			assertEquals(Totals.of(List.of(entry), patterns), inside.getLast());
		}
		assertEquals(2, inside.size());
		assertEquals(5759 + 4791 + 1510, inside.getFirst().powder(), "the Aquamarine shaft: corpses plus the exit bonus");
	}

	@Test
	void gameValuesShowWithUnits() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.game(new HudData.Game(144, 42L, 19.94, "21:42", "0.13.0", "26.2"));
		Box box = new Box();
		for (HudValue value : List.of(HudValue.FPS, HudValue.PING, HudValue.TPS, HudValue.CLOCK, HudValue.MOD_VERSION,
				HudValue.MINECRAFT_VERSION)) {
			box.lines.add(new Line(value));
		}
		assertEquals(List.of("§7FPS: §f144", "§7Ping: §f42ms", "§7TPS: §f19.9", "§7Clock: §f21:42", "§7Surveyor §f0.13.0",
				"§7Minecraft §f26.2"), HudBoxText.lines(box, data, false));
		HudData unknown = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.game(new HudData.Game(60, null, null, "21:42", null, "26.2"));
		assertEquals("§7Ping: §f-", HudBoxText.line(box, box.lines.get(1), unknown, false), "no ms after a missing ping");
		assertEquals("§7Ping: §f42ms", HudBoxText.line(box, box.lines.get(1), unknown, true), "editor example");
		assertEquals("Ping 42ms", HudBoxText.tags("Ping {ping}", data, false), "tags carry the unit too");
	}

	@Test
	void tickRateFromServerPings() {
		TickRate rate = new TickRate();
		rate.reset(0);
		assertNull(rate.tps(0));
		long t = 0;
		int id = 0;
		// warm-up pings ignored
		for (; t < TickRate.WARMUP_MILLIS; t += 50) rate.onPing(id++, t);
		assertNull(rate.tps(t), "nothing after warm-up yet");
		for (int i = 0; i <= TickRate.MIN_GAPS; i++, t += 50) {
			rate.onPing(id++, t);
			rate.onPing(id - 1, t + 10);
		}
		assertEquals(20.0, rate.tps(t - 50), 1e-9, "duplicate ids skipped");
		for (int i = 0; i < 200; i++, t += 100) rate.onPing(id++, t);
		assertEquals(10.0, rate.tps(t - 100), 1e-9, "last 100 gaps only");
		assertEquals(0.0, rate.tps(t - 100 + TickRate.STALE_MILLIS + 1), "stalled server");
		rate.reset(t);
		assertNull(rate.tps(t), "server switch clears it");
	}

	@Test
	void pingFromBlockAcks() {
		PingTimer ping = new PingTimer();
		assertNull(ping.ping(0), "no mining yet");
		ping.onSent(1, 1_000);
		ping.onSent(2, 1_010);
		ping.onSent(3, 1_020);
		ping.onAck(3, 1_100);
		assertEquals(80L, ping.ping(1_100), "only the acked sequence is timed, 1 + 2 waited longer");
		ping.onAck(2, 1_200);
		assertEquals(80L, ping.ping(1_200), "old ack ignored");
		for (int i = 4; i < 4 + PingTimer.SAMPLES; i++) {
			ping.onSent(i, i * 1_000L);
			ping.onAck(i, i * 1_000L + (i == 5 ? 900 : 50));
		}
		assertEquals(50L, ping.ping(20_000), "median, one slow ack doesnt move it");
		long last = (3 + PingTimer.SAMPLES) * 1_000L + 50;
		assertNull(ping.ping(last + PingTimer.STALE_MILLIS + 1), "stopped mining a while ago");
		ping.onSent(100, 200_000);
		ping.reset();
		ping.onAck(100, 200_050);
		assertNull(ping.ping(200_050), "server switch drops whats in flight");
		ping.onSent(1, 300_000);
		ping.onAck(1, 300_030);
		assertEquals(30L, ping.ping(300_030), "old samples dropped after a long break");
	}

	@Test
	void newLinesGetTheirOwnColourAndLabel() {
		assertEquals("6", new Line(HudValue.COINS).valueColor);
		assertEquals("b", new Line(HudValue.SHAFT_COLD).valueColor);
		assertNull(new Line(HudValue.FPS).valueColor, "follows the box");
		assertNull(new Line().valueColor, "read lines keep what the file says");
		assertEquals("f", new Box().valueColor, "boxes stay white");

		Line coins = new Line(HudValue.COINS);
		coins.setRange(Range.ALL_TIME);
		assertEquals("Coins all-time: ", coins.prefix, "an untyped label follows the range");
		coins.setRange(Range.PER_HOUR);
		assertEquals("Coins/h: ", coins.prefix, "/h in the label");
		assertEquals("Coins/shaft all-time: ", HudValue.COINS_PER_SHAFT.defaultPrefix(Range.ALL_TIME));
		assertFalse(HudValue.FPS.hasFormatChoice(), "144 is 144 in every format");
		assertTrue(HudValue.COINS.hasFormatChoice());
		coins.prefix = "Money: ";
		coins.setRange(Range.ALL_TIME);
		assertEquals("Money: ", coins.prefix, "a typed label stays");
		// saved by 0.22 + alpha.1-2, before the renames
		Line powder = new Line(HudValue.SESSION_POWDER, "Powder: ", true, 0);
		powder.setRange(Range.ALL_TIME);
		assertEquals("Shaft powder all-time: ", powder.prefix, "an old default label follows too");
		Line perShaft = new Line(HudValue.COINS_PER_SHAFT, "All-time avg/shaft: ", true, 0);
		perShaft.setRange(Range.SESSION);
		assertEquals("Coins/shaft: ", perShaft.prefix);
	}

	@Test
	void shaftLinesSayLastShaftOutside() {
		Box money = new Box();
		Line shaftCoins = new Line(HudValue.COINS);
		shaftCoins.setRange(Range.SHAFT);
		shaftCoins.prefix = "This shaft: ";
		money.lines.add(new Line(HudValue.SPAWN_TIMER));
		money.lines.add(shaftCoins);
		money.lines.add(new Line(HudValue.COINS, "Session: ", true, 0));
		HudData outside = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
				.money(new HudData.Money(2_431_000.0, null, null, 0, null, null, null, 0, null));
		assertEquals("§7Last shaft: §62.4M", HudBoxText.lines(money, outside, false).get(1));
		assertEquals("§7This shaft: §62.4M", HudBoxText.lines(money, HudData.EXAMPLE, false).get(1));
		assertEquals("§7Session: §6-", HudBoxText.lines(money, outside, false).get(2), "other ranges keep their label");
		Line free = new Line(HudValue.COINS);
		free.advanced = true;
		free.text = "This shaft {coins:shaft}";
		Box box = new Box();
		box.lines.add(free);
		assertEquals("§7Last shaft 2.4M", HudBoxText.lines(box, outside, false).getFirst());
		free.text = "This shaft {coins}";
		assertEquals("§7This shaft -", HudBoxText.lines(box, outside, false).getFirst(), "only w/ a shaft tag");
	}

	@Test
	void shaftTypesHaveAHudColour() {
		ShaftNames names = ShaftNames.defaults();
		assertEquals('a', names.hudColor("JADE"));
		assertEquals(0x4CC552, names.rgb("JADE"), "the stats colour still reads");
		assertNull(names.hudColor("NOPE"));
	}

	@Test
	void shaftValuesShowTheLastShaftOutside() throws Exception {
		List<JournalEntry> entries = realJournal();
		JournalEntry last = entries.getLast();
		HudData data = live(entries, last.enteredAt() + last.durationMillis()).lastShaft(last, ShaftNames.defaults());
		assertEquals(last.durationMillis(), data.get(HudValue.SHAFT_TIME, null));
		assertEquals((long) last.campfiresAt().size(), data.get(HudValue.SHAFT_CAMPFIRES, null));
		assertEquals(last.searchMillis(), data.get(HudValue.AVERAGE_SPAWN, Range.SHAFT));
		assertNotNull(data.get(HudValue.AVERAGE_SPAWN, Range.ALL_TIME));

		Box box = new Box();
		Line time = new Line(HudValue.SHAFT_TIME);
		box.lines.add(time);
		assertNotEquals("§7Time inside: §f-", HudBoxText.lines(box, data, false).getFirst());
		time.dashOutside = true;
		assertEquals("§7Time inside: §f-", HudBoxText.lines(box, data, false).getFirst(), "the line asked for -");
		assertEquals("§7Time inside: §f2:14", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst(), "inside, - never shows");
		assertTrue(HudValue.COINS.followsShaft(Range.SHAFT));
		assertFalse(HudValue.COINS.followsShaft(Range.SESSION));
	}

	@Test
	void averageSpawnRanges() {
		Line spawn = new Line(HudValue.AVERAGE_SPAWN);
		assertEquals(Range.SESSION, spawn.range);
		spawn.setRange(Range.SHAFT);
		Box box = new Box();
		box.lines.add(spawn);
		assertEquals("§7Spawn time this shaft: §f1:42", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		HudData outside = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		assertEquals("§7Spawn time last shaft: §f-", HudBoxText.lines(box, outside, false).getFirst());
		spawn.setRange(Range.ALL_TIME);
		assertEquals("§7Avg spawn all-time: §f1:31", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		assertEquals(Long.valueOf(15), new Totals(2, 0, 0, 30, 2, 0, Map.of(), 0).averageSpawn());
		assertNull(Totals.NONE.averageSpawn());
	}

	@Test
	void titleColourAndNoRulesOnNewLines() {
		Box box = HudLayout.defaults().boxes.get(0);
		assertEquals("§e§lSession", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		box.titleColor = "b";
		box.titleBold = false;
		assertEquals("§bSession", HudBoxText.lines(box, HudData.EXAMPLE, false).getFirst());
		String json = HudLayout.defaults().toJson();
		assertFalse(json(HudLayout.defaults().boxes.get(0)).contains("titleColor") || json.contains("dashOutside"), "unset = left out of the file");
		assertEquals(List.of("shaft"), HudLayout.defaults().boxes.stream().map(b -> b.titleColor).filter(c -> c != null).toList());
		assertEquals("shaft", HudLayout.fromJson(json).boxes.get(2).titleColor, "kept on load");
		assertFalse(json.contains("colorRules"), "no typed rules till someone types them");
		assertFalse(new Line(HudValue.SHAFT_COLD).readyRule, "new lines start w/o colour rules");
		assertFalse(new Line(HudValue.SHAFT_COLD).advancedMode());
	}

	@Test
	void presetsAddTheirLines() {
		List<HudLayout.Preset> presets = HudLayout.presets();
		assertEquals(List.of("Spawn", "Session", "Current shaft", "All-time", "Session profit", "Performance", "Consumables"),
				presets.stream().map(HudLayout.Preset::name).toList());
		HudLayout defaults = HudLayout.defaults();
		assertEquals(defaults.boxes.get(1).id, presets.get(0).box().get().id);
		assertEquals(json(defaults.boxes.get(1)), json(presets.get(0).box().get()), "same as the default box");
		Box allTime = presets.get(3).box().get();
		assertEquals(List.of("§e§lAll-time", "§7Shafts: §f412", "§7Shaft powder: §b1,204,310", "§7Corpse Fine: §9412.8"),
				HudBoxText.lines(allTime, HudData.EXAMPLE, true));
		assertEquals(json(defaults.boxes.get(3)), json(presets.get(4).box().get()), "same as the default box");
		Box performance = presets.get(5).box().get();
		assertTrue(performance.visible(false, false), "shown everywhere");
		assertEquals(List.of("§7FPS: §f144", "§7Ping: §f42ms", "§7TPS: §f19.9"),
				HudBoxText.lines(performance, HudData.EXAMPLE, true));
		assertTrue(presets.get(0).box().get() != presets.get(0).box().get(), "a new box each time");
		Box consumables = presets.get(6).box().get();
		assertTrue(consumables.visible(true, false) && consumables.visible(false, true), "mines + shafts");
		assertFalse(consumables.visible(false, false), "not elsewhere");
		assertEquals(List.of("§7Pristine Potato: §542m", "§7Filet O' Fortune: §942m", "§7Cold Resistance: §b30m", "§7Fiesta Flask: §f11m"),
				HudBoxText.lines(consumables, HudData.EXAMPLE, true));
	}

	@Test
	void consumableLinesHideWhileOff() {
		Box box = HudLayout.presets().get(6).box().get();
		HudData none = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults(), MetalYield.ENCHANTED);
		assertEquals(List.of(), HudBoxText.lines(box, none, false));
		HudData potato = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults(), MetalYield.ENCHANTED)
				.consumables(Map.of(Consumable.PRISTINE_POTATO, 61_000L));
		assertEquals(List.of("§7Pristine Potato: §51:01"), HudBoxText.lines(box, potato, false));
	}

	private static String json(Box box) {
		HudLayout layout = new HudLayout();
		layout.boxes.add(box);
		return layout.toJson();
	}

	@Test
	void formatCodesMatchTheFormats() {
		assertEquals(List.of("#,##0", "0.0k", "0"), HudFormat.options(Kind.INTEGER));
		assertEquals(List.of("0.0", "0", "0.00"), HudFormat.options(Kind.DECIMAL));
		assertEquals(List.of("m:ss", "m\"m\" s\"s\"", "s\"s\"", "m\"m\""), HudFormat.options(Kind.TIME));
		for (Kind kind : List.of(Kind.INTEGER, Kind.DECIMAL, Kind.TIME)) {
			assertEquals(HudFormat.options(kind).size(), HudFormat.names(kind).size(), kind + " names");
			for (int i = 0; i < HudFormat.options(kind).size(); i++) assertFalse(HudFormat.example(kind, i, "").isEmpty());
		}
		assertEquals("43.4kms, 1.2Mms", HudFormat.example(Kind.INTEGER, 1, HudBoxText.suffix(HudValue.PING, null)));
		assertEquals("43,436/h", HudFormat.example(Kind.INTEGER, 0, HudBoxText.suffix(HudValue.SESSION_POWDER, Range.PER_HOUR)));
	}

	@Test
	void toastSpotIsOptionalAndRepaired() {
		HudLayout plain = HudLayout.defaults();
		assertNull(plain.toast);
		assertFalse(plain.toJson().contains("toast"), "never moved = not written");
		HudLayout moved = HudLayout.fromJson("{\"boxes\": [{\"id\": \"toast\"}], \"toast\": {\"id\": \"x\", \"anchorX\": \"END\", \"offsetY\": 30, \"scale\": 9}}");
		assertEquals(HudLayout.TOAST_ID, moved.toast.id);
		assertEquals(HudLayout.Anchor.END, moved.toast.anchorX);
		assertEquals(HudLayout.Anchor.START, moved.toast.anchorY);
		assertEquals(30, moved.toast.offsetY);
		assertEquals(HudLayout.MAX_SCALE, moved.toast.scale);
		assertNotEquals(HudLayout.TOAST_ID, moved.boxes.get(0).id, "a box cant take the toasts id");
		assertEquals(moved.toJson(), HudLayout.fromJson(moved.toJson()).toJson());
	}

	// LiveHud only works pbs out while a drawn line reads one
	@Test
	void pbReadsAreNoted() {
		Box plain = new Box();
		plain.lines.add(new Line(HudValue.SESSION_SHAFTS));
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		HudBoxText.rows(plain, data, false);
		assertFalse(data.pbRead());

		Box pb = new Box();
		pb.lines.add(new Line(HudValue.PERSONAL_BEST));
		HudBoxText.rows(pb, data, false);
		assertTrue(data.pbRead(), "an easy line, no result yet");

		Box tagged = new Box();
		Line text = new Line(HudValue.SESSION_SHAFTS);
		text.advanced = true;
		text.text = "Best: {pb:shaft_coins}";
		tagged.lines.add(text);
		HudData other = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());
		HudBoxText.rows(tagged, other, false);
		assertTrue(other.pbRead(), "a tag in advanced text");
	}
}
