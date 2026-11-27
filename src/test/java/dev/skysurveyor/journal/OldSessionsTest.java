package dev.skysurveyor.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

// expected files made w/ the release that wrote the data
class OldSessionsTest {
	@TestFactory
	Stream<DynamicTest> oldSessionsKeepTheirRates() {
		return OldSaves.withFile("mining.jsonl").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			List<JournalEntry> entries = new JournalFile(dir.resolve("journal.jsonl"), Runnable::run).read();
			List<MiningEntry> mining = read(dir.resolve("mining.jsonl"), MiningEntry.class, e -> e.items() != null);
			Path breaksFile = dir.resolve("breaks.jsonl");
			List<BreakEntry> breaks = read(breaksFile, BreakEntry.class, e -> e.to() > e.from());
			Path stashFile = dir.resolve("stash.jsonl");
			List<StashEntry> stash = read(stashFile, StashEntry.class, e -> e.grew() > 0);
			StringBuilder out = new StringBuilder();
			out.append("time ").append(JournalStats.allTime(entries, mining, breaks).timeMillis()).append('\n');
			for (SessionSummary session : JournalStats.sessions(entries, mining, breaks)) {
				out.append(String.format(Locale.ROOT, "%d shafts=%d outside=%d length=%d shafts/h=%s per1k/h=%s", session.start(),
						session.shafts(), session.mining().size(), session.lengthMillis(), rate(session.shaftsPerHour()),
						rate(session.perHour(1000, session.end()))));
				// only w/ the file, pre 0.24.0 folders keep their lines
				if (Files.exists(breaksFile)) out.append(" breaks=").append(session.breakMillis());
				if (Files.exists(stashFile)) out.append(" stashed=").append(Stash.grew(stash, session.start()));
				out.append('\n');
			}
			OldSaves.assertExpected(dir, "expected-sessions.txt", out.toString());
		}));
	}

	private static String rate(Double rate) {
		return rate == null ? "-" : String.format(Locale.ROOT, "%.4f", rate);
	}

	static <T> List<T> read(Path file, Class<T> type, Predicate<T> complete) throws Exception {
		if (!Files.exists(file)) return List.of();
		return new JsonLinesFile<>(file, type, complete, Runnable::run).read();
	}
}
