package dev.skysurveyor.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.money.DropList;
import dev.skysurveyor.money.DropList.Row;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.MiningValue;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.GemTier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// hud drops line over real journals, saved prices only
class OldDropsTest {
	private static final TextPatterns PATTERNS = TextPatterns.defaults();

	// the test that matters: rows (folded ones too) + spent = the coins line, every old session
	@TestFactory
	Stream<DynamicTest> rowsAddUpToTheCoinsLine() {
		ItemIds ids = ItemIds.bundled(Map.of());
		List<MoneySettings> settings = List.of(MoneySettings.DEFAULTS, MoneySettings.DEFAULTS.withGemTier(GemTier.FINE),
				MoneySettings.DEFAULTS.withIronman(true));
		return OldSaves.withFile("journal.jsonl").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			List<JournalEntry> entries = new JournalFile(dir.resolve("journal.jsonl"), Runnable::run).read();
			List<MiningEntry> mining = mining(dir.resolve("mining.jsonl"));
			int sessions = 0;
			for (MoneySettings money : settings) {
				Function<JournalEntry, ShaftValue> values = e -> ShaftValue.of(e, ShaftValue.layered(e.prices(), id -> null),
						money, ids, PATTERNS);
				Function<MiningEntry, List<Line>> outside = m -> MiningValue.lines(m, ShaftValue.layered(m.prices(),
						id -> null), money, ids, PATTERNS);
				for (SessionSummary session : JournalStats.sessions(entries, mining, List.of())) {
					List<Line> shaftLines = new ArrayList<>();
					List<Line> outsideLines = new ArrayList<>();
					for (JournalEntry entry : session.entries()) {
						ShaftValue value = values.apply(entry);
						shaftLines.addAll(value.lines());
						DropList one = DropList.of(value.lines(), List.of(), money.isIronman(), PATTERNS);
						assertEquals(value.net(), one.total(), 0.01, "one shaft " + entry.enteredAt());
					}
					for (MiningEntry entry : session.mining()) outsideLines.addAll(outside.apply(entry));
					DropList list = DropList.of(shaftLines, outsideLines, money.isIronman(), PATTERNS);
					double coins = MoneyStats.of(session.entries(), values, session.mining(), outside, money.isIronman()).total();
					assertEquals(coins, list.total(), 0.01, "session " + session.start());
					double spent = list.spent().stream().mapToDouble(Row::coins).sum();
					for (int rows : new int[] {3, 5, 8, 0}) {
						double shown = list.shown(rows).stream().mapToDouble(Row::coins).sum();
						assertEquals(coins, shown + spent, 0.01, rows + " rows, session " + session.start());
						double sources = list.shownSources(rows).stream().mapToDouble(s -> s.row().coins()).sum();
						assertEquals(coins, sources + spent, 0.01, rows + " sources, session " + session.start());
					}
					sessions++;
				}
			}
			assertTrue(sessions > 0);
			// all-time like LiveHud: summed lines, = the all-time coins line
			for (MoneySettings money : settings) {
				DropList.Lines in = new DropList.Lines();
				for (JournalEntry entry : entries) in.shaft(ShaftValue.of(entry, ShaftValue.layered(entry.prices(), id -> null), money, ids, PATTERNS));
				for (MiningEntry entry : mining) in.outside(MiningValue.lines(entry, ShaftValue.layered(entry.prices(), id -> null), money, ids, PATTERNS));
				DropList all = in.summed().list(money.isIronman(), PATTERNS);
				double coins = MoneyStats.of(entries, e -> ShaftValue.of(e, ShaftValue.layered(e.prices(), id -> null), money, ids, PATTERNS), mining,
						m -> MiningValue.lines(m, ShaftValue.layered(m.prices(), id -> null), money, ids, PATTERNS), money.isIronman()).total();
				assertEquals(coins, all.total(), 0.01, "all-time");
				double spent = all.spent().stream().mapToDouble(Row::coins).sum();
				assertEquals(coins, all.shown(5).stream().mapToDouble(Row::coins).sum() + spent, 0.01, "all-time, 5 rows");
				assertEquals(coins, all.shownSources(5).stream().mapToDouble(s -> s.row().coins()).sum() + spent, 0.01, "all-time sources");
			}
		}));
	}

	// money-tracker-design.md section 1: 25 recorded shafts of this 29 shaft session
	// corpse loot there leaves out the 4 shafts before the recording, outside entries were replayed w/o prices
	@Test
	void recordingFiveSources() throws Exception {
		Path dir = OldSaves.ROOT.resolve("0.24.0-alpha.13");
		// game reads this id from hypixels item list, not bundled
		ItemIds ids = ItemIds.bundled(Map.of("Glossy Gemstone", "GLOSSY_GEMSTONE"));
		List<Line> lines = new ArrayList<>();
		for (JournalEntry entry : new JournalFile(dir.resolve("journal.jsonl"), Runnable::run).read()) {
			if (entry.sessionId() != 1794754944760L) continue;
			lines.addAll(ShaftValue.of(entry, ShaftValue.layered(entry.prices(), id -> null), MoneySettings.DEFAULTS, ids, PATTERNS).lines());
		}
		DropList list = DropList.of(lines, List.of(), false, PATTERNS);
		Map<String, Double> sources = new java.util.HashMap<>();
		for (DropList.Source source : list.sources()) sources.put(source.row().name(), source.row().coins());
		assertEquals(42.74e6, sources.get("Mined gems"), 0.005e6);
		assertEquals(11.92e6, sources.get("Glossy Gemstones"), 0.005e6);
		assertEquals(0.98e6, sources.get("Suspicious Scrap"), 0.005e6);
		assertEquals(-10.43e6, list.spent().stream().mapToDouble(DropList.Row::coins).sum(), 0.005e6, "keys per key now");
		assertEquals("Mined gems", list.sources().getFirst().row().name());
	}

	private static List<MiningEntry> mining(Path file) throws Exception {
		if (!Files.exists(file)) return List.of();
		return new JsonLinesFile<>(file, MiningEntry.class, e -> e.items() != null, Runnable::run).read();
	}
}
