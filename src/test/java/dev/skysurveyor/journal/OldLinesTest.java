package dev.skysurveyor.journal;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import dev.skysurveyor.OldSaves;

import static org.junit.jupiter.api.Assertions.assertEquals;

// mining, breaks + stash files load like LiveJournal reads them, nothing lost. journal.jsonl: OldJournalTest
class OldLinesTest {
	private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

	@TestFactory
	Stream<DynamicTest> mining() {
		return lines("mining.jsonl", MiningEntry.class, e -> e.items() != null);
	}

	@TestFactory
	Stream<DynamicTest> breaks() {
		return lines("breaks.jsonl", BreakEntry.class, e -> e.to() > e.from());
	}

	@TestFactory
	Stream<DynamicTest> stash() {
		return lines("stash.jsonl", StashEntry.class, e -> e.grew() > 0);
	}

	private static <T> Stream<DynamicTest> lines(String name, Class<T> type, Predicate<T> complete) {
		return OldSaves.withFile(name).stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName() + "/" + name, () -> {
			Path file = dir.resolve(name);
			List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).toList();
			List<T> entries = OldSessionsTest.read(file, type, complete);
			assertEquals(lines.size(), entries.size(), "every line loads");
			for (int i = 0; i < lines.size(); i++) {
				OldSaves.assertKept(JsonParser.parseString(lines.get(i)), GSON.toJsonTree(entries.get(i)), "line[" + (i + 1) + "]", Map.of());
			}
		}));
	}
}
