package dev.skysurveyor.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.google.gson.JsonParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.OldSaves;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudFileTest {
	private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 17, 12, 0, 0);
	private static final String EDITED = HudLayout.defaults().toJson();
	private static final String DEFAULTS = HudLayout.firstStart().toJson();

	private static HudFile file(Path dir) {
		return file(dir, HudLayout::fromJson);
	}

	private static HudFile file(Path dir, Function<String, HudLayout> parse) {
		return new HudFile(dir.resolve("hud.json"), Runnable::run, () -> NOON, parse);
	}

	private static HudLayout load(HudFile file) {
		HudLayout read = file.load().join();
		file.loaded();
		return read;
	}

	private static List<String> files(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(path -> path.getFileName().toString()).sorted().toList();
		}
	}

	private static String read(Path path) throws IOException {
		return Files.readString(path, StandardCharsets.UTF_8);
	}

	private static void write(Path path, String text) throws IOException {
		Files.writeString(path, text, StandardCharsets.UTF_8);
	}

	@Test
	void newestFileLoadsAndSavesUnchanged(@TempDir Path dir) throws IOException {
		String json = OldSaves.read(OldSaves.newest("hud.json").resolve("hud.json"));
		write(dir.resolve("hud.json"), json);
		HudFile file = file(dir);
		HudLayout read = load(file);
		assertNotNull(read);
		assertTrue(file.save(read.toJson(), () -> false));
		assertEquals(json, read(dir.resolve("hud.json")));
		assertEquals(List.of("hud.json"), files(dir), "no backup");
	}

	@Test
	void noFileLoadsDefaults(@TempDir Path dir) throws IOException {
		HudFile file = file(dir);
		assertNull(load(file));
		assertTrue(file.save(EDITED, () -> false));
		assertEquals(EDITED, read(dir.resolve("hud.json")));
		assertEquals(List.of("hud.json"), files(dir));
	}

	@Test
	void olderFormatIsBackedUpOnceOnFirstSave(@TempDir Path dir) throws IOException {
		String old = OldSaves.read(OldSaves.withFile("hud.json").getFirst().resolve("hud.json"));
		write(dir.resolve("hud.json"), old);
		HudFile file = file(dir);
		HudLayout read = load(file);
		assertNotNull(read);
		assertEquals(old, read(dir.resolve("hud.json")), "loading changes nothing");
		file.save(read.toJson(), () -> false);
		file.save(EDITED, () -> false);
		assertEquals(List.of("hud.json", "hud.json.20261117-120000.old"), files(dir));
		assertEquals(old, read(dir.resolve("hud.json.20261117-120000.old")));
		assertEquals(EDITED, read(dir.resolve("hud.json")));
	}

	@Test
	void backupsInTheSameSecondKeepBoth(@TempDir Path dir) throws IOException {
		String old = OldSaves.read(OldSaves.withFile("hud.json").getFirst().resolve("hud.json"));
		for (int i = 0; i < 2; i++) {
			write(dir.resolve("hud.json"), old);
			HudFile file = file(dir);
			load(file);
			file.save(EDITED, () -> false);
		}
		assertEquals(List.of("hud.json", "hud.json.20261117-120000-2.old", "hud.json.20261117-120000.old"), files(dir));
		assertEquals(old, read(dir.resolve("hud.json.20261117-120000-2.old")));
	}

	@Test
	void invalidJsonIsMovedAway(@TempDir Path dir) throws IOException {
		String broken = "{\"boxes\": [{\"title\": \"Mine\"";
		write(dir.resolve("hud.json"), broken);
		HudFile file = file(dir);
		assertNull(load(file), "defaults");
		assertEquals(List.of("hud.json.20261117-120000.bad"), files(dir));
		assertEquals(broken, read(dir.resolve("hud.json.20261117-120000.bad")));
		file.save(EDITED, () -> false);
		assertEquals(EDITED, read(dir.resolve("hud.json")));
	}

	// wrong type from a hand edit, gson doesnt wrap it
	@Test
	void anyOtherThrowIsMovedAwayToo(@TempDir Path dir) throws IOException {
		String wrongType = "{\"boxes\": [{\"title\": \"Mine\", \"scale\": \"big\"}]}";
		RuntimeException thrown = assertThrows(RuntimeException.class, () -> HudLayout.fromJson(wrongType));
		assertFalse(thrown instanceof JsonParseException, "else this test checks nothing new: " + thrown);
		write(dir.resolve("hud.json"), wrongType);
		assertNull(load(file(dir)));
		assertEquals(List.of("hud.json.20261117-120000.bad"), files(dir));
		assertEquals(wrongType, read(dir.resolve("hud.json.20261117-120000.bad")));
	}

	// deep rule before HudExpr had limits
	@Test
	void stackOverflowIsMovedAway(@TempDir Path dir) throws IOException {
		write(dir.resolve("hud.json"), EDITED);
		HudFile file = file(dir, json -> {
			throw new StackOverflowError();
		});
		assertNull(load(file));
		assertEquals(List.of("hud.json.20261117-120000.bad"), files(dir));
		assertEquals(EDITED, read(dir.resolve("hud.json.20261117-120000.bad")));
	}

	@Test
	void saveBeforeTheLoadWritesNothing(@TempDir Path dir) throws IOException {
		write(dir.resolve("hud.json"), EDITED);
		HudFile file = file(dir);
		assertFalse(file.save(DEFAULTS, () -> true), "defaults would overwrite the file");
		assertEquals(EDITED, read(dir.resolve("hud.json")));
		assertEquals(List.of("hud.json"), files(dir));
		load(file);
		assertTrue(file.save(DEFAULTS, () -> true), "after the load a reset to defaults is the player's choice");
		assertEquals(DEFAULTS, read(dir.resolve("hud.json")));
	}

	@Test
	void tooLongPartsAreCutAndTheOriginalKept(@TempDir Path dir) throws IOException {
		HudLayout layout = HudLayout.defaults();
		layout.boxes.getFirst().title = "x".repeat(5000);
		String json = layout.toJson();
		write(dir.resolve("hud.json"), json);
		HudFile file = file(dir);
		HudLayout read = load(file);
		assertEquals(HudLayout.MAX_TEXT, read.boxes.getFirst().title.length());
		assertEquals(1, read.cutParts());
		file.save(read.toJson(), () -> false);
		assertEquals(json, read(dir.resolve("hud.json.20261117-120000.old")));
	}
}
