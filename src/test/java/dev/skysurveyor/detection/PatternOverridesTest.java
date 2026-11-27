package dev.skysurveyor.detection;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.debug.PatternOverrides;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PatternOverridesTest {
	// research/05 campfire msg, made up rewording
	private static final String NEW_TEXT = "You set down a Portable Campfire!";

	@Test
	void overridesComeFirstAndBuiltInStays() {
		TextPatterns patterns = TextPatterns.builtInWith(List.of("# fix", "campfire.placed ^You set down a Portable Campfire!$"));
		assertTrue(patterns.matches("campfire.placed", NEW_TEXT));
		assertTrue(patterns.matches("campfire.placed", "You placed a Portable Campfire!"));
		assertFalse(TextPatterns.defaults().matches("campfire.placed", NEW_TEXT), "defaults untouched");
	}

	@Test
	void badLineThrows() {
		assertThrows(IllegalArgumentException.class, () -> TextPatterns.builtInWith(List.of("nospace")));
		assertThrows(RuntimeException.class, () -> TextPatterns.builtInWith(List.of("key ([")));
	}

	// missing group crashed the journal (LootSummary reads quality)
	@Test
	void missingGroupThrows() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> TextPatterns.builtInWith(List.of("gem.item ^(Rough|Fine) (?<gem>\\w+) Gemstone$")));
		assertEquals("gem.item needs the group (?<quality>...)", e.getMessage());
		TextPatterns.builtInWith(List.of("gem.item ^(?<quality>Rough|Fine) (?<gem>\\w+) Gemstone$"));
	}

	@Test
	void fileSwapsTheSharedPatternsAndBack(@TempDir Path tmp) throws Exception {
		TextPatterns shared = TextPatterns.defaults();
		try {
			assertNull(PatternOverrides.load(tmp), "no file, nothing to say");
			Files.writeString(tmp.resolve("patterns.txt"), "campfire.placed ^You set down a Portable Campfire!$\n");
			assertEquals("§e[Surveyor] Using 1 pattern override from skysurveyor/patterns.txt.", PatternOverrides.load(tmp));
			assertTrue(shared.matches("campfire.placed", NEW_TEXT), "same object every parser holds");
			assertEquals(List.of("campfire.placed ^You set down a Portable Campfire!$"), TextPatterns.overrides());

			Files.writeString(tmp.resolve("patterns.txt"), "campfire.placed ([\n");
			assertTrue(PatternOverrides.load(tmp).startsWith("§c[Surveyor] Could not use skysurveyor/patterns.txt, kept the last overrides"));
			assertTrue(shared.matches("campfire.placed", NEW_TEXT), "bad file changes nothing");

			Files.delete(tmp.resolve("patterns.txt"));
			assertEquals("§7[Surveyor] No skysurveyor/patterns.txt anymore, using the built-in patterns.", PatternOverrides.load(tmp));
			assertFalse(shared.matches("campfire.placed", NEW_TEXT));
		} finally {
			TextPatterns.useOverrides(List.of());
		}
	}

	@Test
	void replayUsesTheRecordingsOverrides() {
		JsonObject start = JsonParser.parseString("{\"t\":0,\"type\":\"start\",\"patternOverrides\":[\"campfire.placed ^You set down a Portable Campfire!$\"]}")
				.getAsJsonObject();
		assertTrue(RecordingReplay.patterns(List.of(start)).matches("campfire.placed", NEW_TEXT));
		TextPatterns none = RecordingReplay.patterns(List.of());
		assertNotSame(TextPatterns.defaults(), none, "own copy, a patterns line swaps it");
		assertFalse(none.matches("campfire.placed", NEW_TEXT));
	}
}
