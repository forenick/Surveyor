package dev.skysurveyor.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.settings.DevSettings;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.Settings;
import dev.skysurveyor.util.Guard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DebugInfoTest {
	@Test
	void changedSettingsFromConfigShape() {
		Settings changed = Settings.DEFAULTS.withMoney(Settings.DEFAULTS.money().withGemTier(MoneySettings.GemTier.FINE))
				.withDev(DevSettings.DEFAULTS.withAutoRecord(DevSettings.AutoRecord.MINING_AREAS));
		assertEquals(List.of("money.gemTier: \"fine\"", "dev.autoRecord: \"mining_areas\""),
				DebugInfo.changedSettings(LiveSettings.toJson(changed), LiveSettings.toJson(Settings.DEFAULTS)));
		assertEquals(List.of(), DebugInfo.changedSettings(LiveSettings.toJson(Settings.DEFAULTS), LiveSettings.toJson(Settings.DEFAULTS)));
	}

	@Test
	void wholeSectionsSayChanged() {
		JsonObject now = JsonParser.parseString("{\"personalBests\": {\"list\": []}}").getAsJsonObject();
		assertEquals(List.of("personalBests: changed"), DebugInfo.changedSettings(now, new JsonObject()));
	}

	@Test
	void textHasEveryPart() {
		Map<String, String> versions = new LinkedHashMap<>();
		versions.put("Surveyor", "0.26.0");
		String text = new DebugInfo(versions, List.of("caxton 0.6"), List.of(), List.of(new Guard.Failure("hud tick", 3, "java.lang.X: For input string: \"Alex\"", "a.B(B.java:1)")),
				List.of(DebugInfo.file("journal.jsonl", 2048, 12)), Map.of("journal.jsonl", 2), "off").text();
		assertTrue(text.contains("Surveyor: 0.26.0\n"));
		assertTrue(text.contains("Settings not at default: none"));
		assertTrue(text.contains("  hud tick (3x): java.lang.X\n    at a.B(B.java:1)"));
		assertFalse(text.contains("Alex"), "no error message, it can hold game text");
		assertTrue(text.contains("  journal.jsonl: 2 KB, 12 lines"));
		assertTrue(text.contains("  journal.jsonl: 2 lines couldn't be read"));
		assertTrue(text.contains("Recording: off"));
		assertTrue(text.contains("caxton 0.6"));
	}

	@Test
	void tooManyErrorsCut() {
		List<Guard.Failure> many = new ArrayList<>();
		for (int i = 0; i < DebugInfo.MAX_ERRORS + 3; i++) many.add(new Guard.Failure("e" + i, 1, "x", "f"));
		String text = new DebugInfo(Map.of(), List.of(), List.of(), many, List.of(), Map.of(), "off").text();
		assertTrue(text.contains("and 3 more kinds"));
		assertFalse(text.contains("e" + (DebugInfo.MAX_ERRORS + 1) + " "));
	}

	@Test
	void sizes() {
		assertEquals("512 B", DebugInfo.size(512));
		assertEquals("3 KB", DebugInfo.size(3 * 1024 + 5));
		assertEquals("1.5 MB", DebugInfo.size(3 * 512 * 1024));
	}
}
