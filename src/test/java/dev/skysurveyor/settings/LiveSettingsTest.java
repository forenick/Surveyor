package dev.skysurveyor.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.journal.PersonalBests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveSettingsTest {
	private static LiveSettings started(Path tmp) {
		LiveSettings live = new LiveSettings();
		live.start(tmp, Runnable::run);
		live.reloadIfChanged();
		return live;
	}

	private static JsonObject file(Path tmp) throws Exception {
		return JsonParser.parseString(Files.readString(tmp.resolve("config.json"))).getAsJsonObject();
	}

	@Test
	void defaultsBeforeStart() {
		LiveSettings live = new LiveSettings();
		assertEquals(Settings.DEFAULTS, live.get());
		assertNull(live.saveError());
		live.reloadIfChanged();
		// nothing to save to yet, memory stays
		Settings asked = live.change(s -> s.withJournal(JournalSettings.DEFAULTS.withByDay(false)));
		assertFalse(asked.journal().byDay());
		assertEquals(Settings.DEFAULTS, live.get());
	}

	@Test
	void changeSetsMemoryAndSavesOnlyThatSection(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"),
				"{\"money\": {\"bazaarTax\": 2, \"later\": 5}, \"journal\": {\"byDay\": false}, \"summary\": {\"seconds\": 900}, \"other\": 1}");
		LiveSettings live = started(tmp);
		JsonObject before = file(tmp);

		Settings next = live.change(s -> s.withSummary(s.summary().withShow(SummarySettings.Show.CHAT)));
		assertEquals(next, live.get());
		assertEquals(SummarySettings.Show.CHAT, live.get().summary().show());
		JsonObject after = file(tmp);
		assertEquals("chat", after.getAsJsonObject("summary").get("show").getAsString());
		assertEquals(900, after.getAsJsonObject("summary").get("seconds").getAsInt(), "hand edit stays");
		after.getAsJsonObject("summary").remove("show");
		assertEquals(before, after, "nothing else written");

		live.change(s -> s.withJournal(s.journal().withByDay(true)).withMoney(s.money().withIronman(true)));
		JsonObject both = file(tmp);
		assertTrue(both.getAsJsonObject("journal").get("byDay").getAsBoolean());
		assertTrue(both.getAsJsonObject("money").get("ironman").getAsBoolean());
		assertEquals(2, both.getAsJsonObject("money").get("bazaarTax").getAsInt());
		assertEquals(5, both.getAsJsonObject("money").get("later").getAsInt());
		assertFalse(both.getAsJsonObject("money").has("gemTier"));
		assertEquals(1, both.get("other").getAsInt());

		LiveSettings restarted = started(tmp);
		assertEquals(live.get(), restarted.get());
	}

	@Test
	void personalBestsSaveAsAList(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"), "{\"money\": {\"bazaarTax\": 2}}");
		LiveSettings live = started(tmp);
		assertEquals(PersonalBestSettings.DEFAULTS, live.get().personalBests(), "no section = the default list");
		List<PersonalBests.Def> list = List.of(new PersonalBests.Def("shaft_coins", "JASP", "lowest", null),
				new PersonalBests.Def("drystreak", "FAIR", null, "Vanguard wait"));
		live.change(s -> s.withPersonalBests(s.personalBests().withList(list)));
		JsonObject saved = file(tmp);
		assertEquals(2, saved.getAsJsonObject("money").get("bazaarTax").getAsInt(), "money untouched");
		assertEquals("{\"stat\":\"shaft_coins\",\"shaft\":\"JASP\",\"best\":\"lowest\"}",
				saved.getAsJsonObject("personalBests").getAsJsonArray("list").get(0).toString(), "nulls left out");
		assertEquals(list, started(tmp).get().personalBests().list());
		live.change(s -> s.withPersonalBests(PersonalBestSettings.DEFAULTS));
		assertFalse(file(tmp).has("personalBests"), "reset = no section, later defaults reach it");

		Files.writeString(tmp.resolve("config.json"), "{\"personalBests\": {\"list\": [{\"stat\": \"nope\"}, 5, "
				+ "{\"stat\": \"shaft_powder\", \"best\": \"sideways\"}]}}");
		assertEquals(List.of(new PersonalBests.Def("shaft_powder", null, null, null)), started(tmp).get().personalBests().list(),
				"broken entries skipped, the rest loads");
	}

	@Test
	void sameSettingsSaveNothing(@TempDir Path tmp) throws Exception {
		LiveSettings live = started(tmp);
		List<String> heard = new ArrayList<>();
		live.onChange((before, after) -> heard.add("change"));
		Files.writeString(tmp.resolve("config.json"), "kept");
		live.change(s -> s.withMoney(s.money().withBazaarTax(s.money().bazaarTax())));
		assertEquals("kept", Files.readString(tmp.resolve("config.json")));
		assertTrue(heard.isEmpty());
	}

	@Test
	void listenersHearPageChangesNotReloads(@TempDir Path tmp) throws Exception {
		LiveSettings live = started(tmp);
		List<Settings> heard = new ArrayList<>();
		live.onChange((before, after) -> {
			heard.add(before);
			heard.add(after);
		});
		Settings next = live.change(s -> s.withMoney(s.money().withIronman(true)));
		assertEquals(List.of(Settings.DEFAULTS, next), heard);

		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		live.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, live.get().money().gemTier());
		assertEquals(2, heard.size());
	}

	@Test
	void failedSaveIsReported(@TempDir Path tmp) throws Exception {
		LiveSettings live = started(tmp);
		Files.writeString(tmp.resolve("config.json"), "{\"money\": {");
		live.change(s -> s.withJournal(JournalSettings.DEFAULTS.withByDay(false)));
		assertFalse(live.get().journal().byDay(), "memory still changes");
		assertEquals("{\"money\": {", Files.readString(tmp.resolve("config.json")));
		assertTrue(live.saveError() != null);
	}
}
