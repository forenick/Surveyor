package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.mining.MiningStats;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// made up, like LearnerTest
class GuideProgressTest {
	private static final GameData DATA = GameData.defaults();

	private static GuideProgress progress(List<JournalEntry> entries, Map<String, Double> typed) {
		List<SessionSummary> sessions = entries.isEmpty() ? List.of() : List.of(new SessionSummary(1, entries));
		Learner.Input in = new Learner.Input(sessions, e -> null, e -> 100_000.0, typed, 0, TextPatterns.defaults());
		return GuideProgress.of(in, Learner.learn(in, DATA), DATA);
	}

	// skipped shafts, every one w/ a spawn time
	private static List<JournalEntry> skips(int count) {
		List<JournalEntry> out = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			out.add(EntryBuilder.entry().sessionId(1).enteredAt(1_000_000L + i * 120_000L).durationMillis(10_000).searchMillis(100_000L)
					.shaftId("PERI_1").corpses(List.of(new Corpse("Lapis", true))).build());
		}
		return out;
	}

	// LearnerTest's mined jasper, no spawn time
	private static JournalEntry mined(long at) {
		return EntryBuilder.entry().sessionId(1).enteredAt(at).shaftId("JASP_1").durationMillis(760_000)
				.corpses(List.of(new Corpse("Lapis", true))).gems(new GemYield("Jasper", 935_000, 165_000, 0, 0, 0))
				.mining(new MiningStats(700_000, 60_000, 1_000, 100, "magenta_stained_glass", 11.0, 4.0, 140_000, 0, 11.0)).build();
	}

	private static String plain(String text) {
		return text.replaceAll("§.", "");
	}

	@Test
	void freshInstallListsAllThree() {
		GuideProgress progress = progress(List.of(), Map.of());
		assertTrue(progress.shows());
		assertEquals("Starting values until yours are in: 0 of 5 spawns, 0 of 1 mined shaft, 0 of 10 shafts", plain(progress.line()));
	}

	@Test
	void countsWhatIsThere() {
		GuideProgress progress = progress(skips(3), Map.of());
		assertEquals("Starting values until yours are in: 3 of 5 spawns, 0 of 1 mined shaft, 3 of 10 shafts", plain(progress.line()));
	}

	@Test
	void onlyWhatIsMissing() {
		List<JournalEntry> entries = new ArrayList<>(skips(6));
		entries.add(mined(10_000_000L));
		GuideProgress progress = progress(entries, Map.of());
		assertTrue(progress.shows());
		assertEquals("Starting values until yours are in: 7 of 10 shafts", plain(progress.line()));
		assertTrue(progress.hover().contains("§7Spawn numbers: §aTracked"));
		assertTrue(progress.hover().contains("§7Mining numbers: §aTracked"));
		assertTrue(progress.hover().contains("§7Corpse odds: §f7 of 10 shafts"));
	}

	@Test
	void goneOnceEverythingIsIn() {
		List<JournalEntry> entries = new ArrayList<>(skips(12));
		entries.add(mined(10_000_000L));
		GuideProgress progress = progress(entries, Map.of());
		assertFalse(progress.shows());
		assertEquals(12, progress.spawns());
		assertEquals(1, progress.mined());
		assertEquals(13, progress.shafts());
	}

	// a mined shaft that isnt 5 min on its own gem doesnt count
	@Test
	void miningNeedsAMinedShaft() {
		List<JournalEntry> entries = new ArrayList<>(skips(12));
		entries.add(EntryBuilder.entry().sessionId(1).enteredAt(10_000_000L).shaftId("JASP_1").durationMillis(200_000).build());
		GuideProgress progress = progress(entries, Map.of());
		assertEquals("Starting values until yours are in: 0 of 1 mined shaft", plain(progress.line()));
	}

	@Test
	void typedCountsAsIn() {
		Map<String, Double> typed = Map.of(NumberId.SPAWN.key(), 90.0, NumberId.EXTRA_CORPSE.key(), 0.2, NumberId.LAPIS_SHARE.key(), 0.3,
				NumberId.EFFICIENCY.key(), 70.0, NumberId.GEMS_PER_BLOCK.key(), 100.0, NumberId.TICKS_4000.key(), 11.0);
		GuideProgress progress = progress(List.of(), typed);
		assertFalse(progress.shows());
		assertTrue(progress.hover().contains("§7Spawn numbers: §eSet by you"));
		assertTrue(progress.hover().contains("§7Mining numbers: §eSet by you"));
		assertTrue(progress.hover().contains("§7Corpse odds: §eSet by you"));
	}

	// one of the two corpse numbers typed isnt enough
	@Test
	void halfTypedStillMissing() {
		GuideProgress progress = progress(skips(12), Map.of(NumberId.EFFICIENCY.key(), 70.0));
		assertEquals("Starting values until yours are in: 0 of 1 mined shaft", plain(progress.line()));
		assertTrue(progress(List.of(), Map.of(NumberId.EXTRA_CORPSE.key(), 0.2)).line().contains("0 of 10 shafts"));
	}

	@Test
	void doneShowsNothing() {
		assertFalse(GuideProgress.DONE.shows());
	}

	// nicks text rules: no dot separators, no em dashes
	@Test
	void plainText() {
		List<GuideProgress> all = List.of(progress(List.of(), Map.of()), progress(skips(3), Map.of()), progress(skips(12), Map.of()),
				progress(List.of(), Map.of(NumberId.SPAWN.key(), 90.0)));
		for (GuideProgress progress : all) {
			List<String> text = new ArrayList<>(progress.hover());
			text.add(progress.line());
			for (String line : text) assertFalse(line.contains("·") || line.contains("—") || plain(line).isBlank(), line);
		}
	}
}
