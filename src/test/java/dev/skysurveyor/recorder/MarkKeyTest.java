package dev.skysurveyor.recorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// option lines as in nicks options.txt of 2026-11-27 (26.2)
class MarkKeyTest {
	@Test
	void nameNeverChanges() {
		assertEquals("key.skysurveyor.mark", MarkKey.NAME, "options.txt saves bindings by name");
	}

	@Test
	void boundKeyStaysInControls() {
		assertFalse(MarkKey.bound(List.of("version:4790", "key_key.shaftjournal.journal:key.keyboard.grave.accent",
				"key_key.skysurveyor.mark:key.keyboard.unknown")), "nicks own, unbound");
		assertTrue(MarkKey.bound(List.of("key_key.skysurveyor.mark:key.keyboard.f8")));
		assertTrue(MarkKey.bound(List.of("key_key.skysurveyor.mark:key.mouse.4")));
		assertFalse(MarkKey.bound(List.of("key_key.shaftjournal.journal:key.keyboard.f8")), "another key");
		assertFalse(MarkKey.bound(List.of()), "never saved");
	}

	@Test
	void recordersGetTheKey() {
		assertTrue(MarkKey.records("{\"dev\": {\"autoRecord\": \"mining_areas\", \"debugOverlay\": false}}"));
		assertFalse(MarkKey.records("{\"dev\": {\"autoRecord\": \"off\"}}"));
		assertFalse(MarkKey.records("{\"dev\": {\"debugOverlay\": true}}"), "missing = off");
		assertFalse(MarkKey.records("{\"money\": {}}"));
		assertFalse(MarkKey.records("{\"dev\": 5}"));
		assertFalse(MarkKey.records("[]"));
		assertThrows(RuntimeException.class, () -> MarkKey.records("{\"dev\": {\"autoRecord\": "));
	}

	@Test
	void newInstallHasNoMarkKey(@TempDir Path game) throws Exception {
		Path data = game.resolve("skysurveyor");
		assertFalse(MarkKey.inControls(game, data), "no files yet");
		Files.writeString(game.resolve("options.txt"), "version:4790\nkey_key.skysurveyor.mark:key.keyboard.unknown\n");
		Files.createDirectories(data);
		Files.writeString(data.resolve("config.json"), "{\"dev\": {\"autoRecord\": \"off\"}}");
		assertFalse(MarkKey.inControls(game, data));
	}

	@Test
	void anyOneOfTheTwoShowsIt(@TempDir Path game) throws Exception {
		Path data = Files.createDirectories(game.resolve("skysurveyor"));
		Files.writeString(data.resolve("config.json"), "{\"dev\": {\"autoRecord\": \"mining_areas\"}}");
		assertTrue(MarkKey.inControls(game, data), "records, no options.txt");
		Files.writeString(data.resolve("config.json"), "{\"dev\": {\"autoRecord\": \"off\"}}");
		Files.writeString(game.resolve("options.txt"), "key_key.skysurveyor.mark:key.keyboard.m\n");
		assertTrue(MarkKey.inControls(game, data), "bound, doesnt record: the binding must not get lost");
	}

	// in doubt it shows, an unregistered key loses its options.txt line on the next save
	@Test
	void brokenConfigShowsIt(@TempDir Path game) throws Exception {
		Path data = Files.createDirectories(game.resolve("skysurveyor"));
		Files.writeString(data.resolve("config.json"), "{\"dev\": {\"autoRecord\": ");
		assertTrue(MarkKey.inControls(game, data));
	}
}
