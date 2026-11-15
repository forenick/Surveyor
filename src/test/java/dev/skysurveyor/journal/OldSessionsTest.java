package dev.skysurveyor.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.journal.JournalStats.SessionSummary;

// session times + rates w/ outside mining. expected files made w/ the release that wrote the data
class OldSessionsTest {
	@TestFactory
	Stream<DynamicTest> oldSessionsKeepTheirRates() {
		return OldSaves.withFile("mining.jsonl").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			List<JournalEntry> entries = new JournalFile(dir.resolve("journal.jsonl"), Runnable::run).read();
			List<MiningEntry> mining = read(dir.resolve("mining.jsonl"));
			StringBuilder out = new StringBuilder();
			out.append("time ").append(JournalStats.allTime(entries, mining, List.of()).timeMillis()).append('\n');
			for (SessionSummary session : JournalStats.sessions(entries, mining, List.of())) {
				out.append(String.format(Locale.ROOT, "%d shafts=%d outside=%d length=%d shafts/h=%s per1k/h=%s%n", session.start(),
						session.shafts(), session.mining().size(), session.lengthMillis(), rate(session.shaftsPerHour()),
						rate(session.perHour(1000, session.end()))));
			}
			OldSaves.assertExpected(dir, "expected-sessions.txt", out.toString());
		}));
	}

	private static String rate(Double rate) {
		return rate == null ? "-" : String.format(Locale.ROOT, "%.4f", rate);
	}

	private static List<MiningEntry> read(Path file) throws Exception {
		if (!Files.exists(file)) return List.of();
		return new JsonLinesFile<>(file, MiningEntry.class, e -> e.items() != null, Runnable::run).read();
	}
}
