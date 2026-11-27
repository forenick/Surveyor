package dev.skysurveyor.settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.calc.Layouts;
import dev.skysurveyor.journal.StatsRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OldConfigTest {
	@TestFactory
	Stream<DynamicTest> oldSettingsLoadAndSurviveSaves() {
		return OldSaves.withFile("config.json").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			Path tmp = Files.createTempDirectory("old-config");
			Path file = tmp.resolve("config.json");
			Files.copy(dir.resolve("config.json"), file);
			String before = Files.readString(file);
			JsonObject old = JsonParser.parseString(before).getAsJsonObject();

			ConfigFile config = new ConfigFile(tmp);
			config.reloadIfChanged();
			assertEquals(before, Files.readString(file), "loading never rewrites it");
			OldSaves.assertKept(old.get("money"), new Gson().toJsonTree(config.get().money()), "money", Map.of());

			boolean byDay = !byDay(config);
			saveByDay(config, byDay);
			JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			old.remove("journal");
			OldSaves.assertKept(old, saved, "config", Map.of());

			ConfigFile reloaded = new ConfigFile(tmp);
			reloaded.reloadIfChanged();
			assertEquals(byDay, byDay(reloaded), "by day comes back");
			assertEquals(config.get().money(), reloaded.get().money());

			// settings page save keeps journal + any other keys
			MoneySettings changed = config.get().money().withGemTier(MoneySettings.GemTier.FINE).withBazaarTax(1.25);
			config.save(ConfigSection.MONEY, config.get().money(), changed);
			JsonObject afterMoney = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			JsonObject expected = saved.deepCopy();
			expected.remove("money");
			JsonObject withoutMoney = afterMoney.deepCopy();
			withoutMoney.remove("money");
			assertEquals(expected, withoutMoney, "only money changes");
			ConfigFile again = new ConfigFile(tmp);
			again.reloadIfChanged();
			assertEquals(changed, again.get().money(), "money comes back");
			assertEquals(byDay, byDay(again));
		}));
	}

	@Test
	void savingMoneyKeepsUnknownKeys(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\", \"later\": 5}, \"other\": {\"x\": 1}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		config.save(ConfigSection.MONEY, config.get().money(), config.get().money().withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals(5, json.getAsJsonObject("money").get("later").getAsInt());
		assertEquals("sell_offer", json.getAsJsonObject("money").get("sellPrice").getAsString());
		assertEquals("fine", json.getAsJsonObject("money").get("gemTier").getAsString());
		assertEquals(1, json.getAsJsonObject("other").get("x").getAsInt());
	}

	// no file yet: saving by day keeps money changed on the page before
	@Test
	void firstSaveKeepsMemorySettings(@TempDir Path tmp) throws Exception {
		ConfigFile config = new ConfigFile(tmp);
		config.set(ConfigSection.MONEY, MoneySettings.DEFAULTS.withCrystals(MoneySettings.Crystals.ZERO));
		saveByDay(config, false);
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(MoneySettings.Crystals.ZERO, reloaded.get().money().crystals());
		assertFalse(byDay(reloaded));
	}

	@Test
	void byDayFollowsTheFile(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"journal\": {\"byDay\": false}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertFalse(byDay(config));
		Files.writeString(file, "{\"journal\": {\"byDay\": \"oops\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		config.reloadIfChanged();
		assertFalse(byDay(config), "only a real true turns it on");
	}

	@Test
	void chartTypesSaveAndLoad(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"journal\": {\"byDay\": false}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		JournalSettings before = config.get().journal();
		assertEquals(JournalSettings.ChartType.BARS, before.spawnChart(), "old file = bars");
		config.save(ConfigSection.JOURNAL, before, before.withCoinChart(JournalSettings.ChartType.LINE));
		JsonObject journal = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("journal");
		assertEquals("line", journal.get("coinChart").getAsString());
		assertFalse(journal.has("spawnChart"), "only changes get written");
		assertFalse(journal.get("byDay").getAsBoolean());
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(JournalSettings.ChartType.LINE, reloaded.get().journal().coinChart());
		assertFalse(byDay(reloaded));
		Files.writeString(file, "{\"journal\": {\"spawnChart\": \"pie\", \"coinChart\": 3}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		reloaded.reloadIfChanged();
		assertEquals(JournalSettings.DEFAULTS, reloaded.get().journal(), "weird values = defaults");
	}

	// a money edit made right before toggling by day still loads
	@Test
	void byDayDoesNotSwallowAnEdit(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(1_000));
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.get().money().gemTier());
		Files.writeString(file, "{\"money\": {\"gemTier\": \"flawless\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(2_000));
		saveByDay(config, false);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FLAWLESS, config.get().money().gemTier());
	}

	private static boolean byDay(ConfigFile config) {
		return config.get().journal().byDay();
	}

	// the page only saves a by day that changed
	private static void saveByDay(ConfigFile config, boolean value) {
		config.save(ConfigSection.JOURNAL, JournalSettings.DEFAULTS.withByDay(!value), JournalSettings.DEFAULTS.withByDay(value));
	}

	private static JsonObject money(Path file) throws Exception {
		return JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("money");
	}

	// hand edit made after the last load, page saves another setting
	@Test
	void pageSaveKeepsHandEditNotLoadedYet(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		MoneySettings before = config.get().money();
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		json.getAsJsonObject("money").addProperty("crystals", "zero");
		Files.writeString(file, json.toString());
		config.save(ConfigSection.MONEY, before, before.withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		assertEquals("zero", money(file).get("crystals").getAsString());
		assertEquals("sell_offer", money(file).get("sellPrice").getAsString());
	}

	// gson cant read "1,5", load fails, a page save still leaves it
	@Test
	void pageSaveKeepsBrokenHandEdit(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"bazaarTax\": \"1,5\", \"gemTier\": \"fine\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.get().money(), "load failed, defaults kept");
		config.save(ConfigSection.MONEY, config.get().money(), config.get().money().withSellPrice(MoneySettings.SellPrice.SELL_OFFER));
		assertEquals("1,5", money(file).get("bazaarTax").getAsString());
		assertEquals("fine", money(file).get("gemTier").getAsString());
		assertEquals("sell_offer", money(file).get("sellPrice").getAsString());
	}

	// queued saves run after memory moved on, they must not set it back
	@Test
	void savesNeverTouchMemory(@TempDir Path tmp) throws Exception {
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		MoneySettings a = MoneySettings.DEFAULTS.withGemTier(MoneySettings.GemTier.FINE);
		MoneySettings b = a.withBazaarTax(2);
		config.set(ConfigSection.MONEY, b);
		config.set(ConfigSection.JOURNAL, JournalSettings.DEFAULTS.withByDay(false));
		config.save(ConfigSection.MONEY, MoneySettings.DEFAULTS, a);
		saveByDay(config, true);
		assertEquals(b, config.get().money());
		assertFalse(byDay(config));
		config.save(ConfigSection.MONEY, a, b);
		saveByDay(config, false);
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(b, reloaded.get().money());
		assertFalse(byDay(reloaded));
	}

	@Test
	void deletedFileBringsBackDefaults(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\", \"bazaarTax\": 2}, \"journal\": {\"byDay\": false}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.get().money().gemTier());
		Files.delete(file);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.get().money());
		assertEquals(JournalSettings.DEFAULTS.byDay(), byDay(config));
		ConfigFile reloaded = new ConfigFile(tmp);
		reloaded.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, reloaded.get().money(), "file written w/ defaults");
	}

	@Test
	void failedSaveIsReported(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"money\": {\"gemTier\": \"fine\",}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		String broken = Files.readString(file);
		config.save(ConfigSection.MONEY, config.get().money(), config.get().money().withCrystals(MoneySettings.Crystals.ZERO));
		assertNotNull(config.saveError());
		assertEquals(broken, Files.readString(file), "never overwritten");
		Files.writeString(file, "{}");
		saveByDay(config, false);
		assertNull(config.saveError());
		assertTrue(Files.readString(file).contains("byDay"));
	}

	@Test
	void summaryLoadsRepairsAndSavesOnlyChanges(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"summary\": {\"show\": \"both\", \"seconds\": 900, \"later\": 1}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		SummarySettings loaded = config.get().summary();
		assertEquals(SummarySettings.Show.BOTH, loaded.show());
		assertEquals(SummarySettings.DEFAULTS.seconds(), loaded.seconds(), "out of range = default");
		assertEquals(SummarySettings.Style.VANILLA, loaded.style());
		config.save(ConfigSection.SUMMARY, loaded, loaded.withPosition(SummarySettings.Position.MOVABLE));
		JsonObject summary = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("summary");
		assertEquals("movable", summary.get("position").getAsString());
		assertEquals(900, summary.get("seconds").getAsInt(), "unchanged hand edit stays");
		assertEquals(1, summary.get("later").getAsInt());
		assertFalse(summary.has("style"));
	}

	// 0.22.0-alpha.15 first start, key order + layout. new at the end: metalTier 0.23.0, ownMetal 0.24.0, charts 0.24.0-alpha.12, dev 0.26.0-alpha.3, entryTitle alpha.23, waypoints alpha.24, statsRange 0.27.0-alpha.39
	@Test
	void newFileLooksTheSame(@TempDir Path tmp) throws Exception {
		new ConfigFile(tmp).reloadIfChanged();
		assertEquals("""
				{
				  "money": {
				    "sellPrice": "insta_sell",
				    "gemTier": "flawless",
				    "bazaarTax": 1.125,
				    "oldShafts": "that_day",
				    "crystals": "perfect_minus_flawless",
				    "ironman": false,
				    "metalTier": "enchanted",
				    "ownMetal": "insta_buy"
				  },
				  "journal": {
				    "byDay": true,
				    "spawnChart": "bars",
				    "coinChart": "bars",
				    "statsRange": "all_time"
				  },
				  "summary": {
				    "show": "toast",
				    "style": "vanilla",
				    "position": "top_right",
				    "seconds": 5.0
				  },
				  "dev": {
				    "autoRecord": "off",
				    "keepRecordings": "30_days",
				    "recordScreens": true,
				    "anonymize": true,
				    "debugOverlay": false
				  },
				  "entryTitle": {
				    "show": true,
				    "seconds": 5.0,
				    "verdict": true
				  },
				  "waypoints": {
				    "fossil": true,
				    "color": "#FF55FF"
				  }
				}""", Files.readString(tmp.resolve("config.json")));
	}

	// ranges since 0.27.0-alpha.39: every older file has neither key
	@TestFactory
	Stream<DynamicTest> oldFilesOpenOnTheOldRanges() {
		return OldSaves.withFile("config.json").stream().map(dir -> DynamicTest.dynamicTest(dir.getFileName().toString(), () -> {
			Path tmp = Files.createTempDirectory("old-config-range");
			Path file = tmp.resolve("config.json");
			Files.copy(dir.resolve("config.json"), file);
			JsonObject old = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			ConfigFile config = new ConfigFile(tmp);
			config.reloadIfChanged();
			assertEquals(StatsRange.ALL_TIME, config.get().journal().statsRange(), "stats opened on all-time");
			assertEquals(StatsRange.LAST_5_SESSIONS, config.get().calculator().range(), "calculator on last 5 sessions");

			// both picked, like a click on a chip
			JournalSettings journal = config.get().journal();
			config.save(ConfigSection.JOURNAL, journal, journal.withStatsRange(StatsRange.LAST_7_DAYS));
			CalculatorSettings calculator = config.get().calculator();
			config.save(ConfigSection.CALCULATOR, calculator, calculator.withRange(StatsRange.SINCE_PATCH));
			JsonObject saved = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			assertEquals("last_7_days", saved.getAsJsonObject("journal").get("statsRange").getAsString());
			assertEquals("since_patch", saved.getAsJsonObject("calculator").get("range").getAsString());
			// the calculator section is rewritten whole, a moved crystal key (alpha.19) is the one thing that changes
			JsonObject kept = old.deepCopy();
			if (kept.has("calculator") && kept.getAsJsonObject("calculator").has("typed")) {
				JsonObject typed = kept.getAsJsonObject("calculator").getAsJsonObject("typed");
				for (String key : List.copyOf(typed.keySet())) {
					if (Layouts.movedCrystalKey(key) != null) typed.remove(key);
				}
			}
			OldSaves.assertKept(kept, saved, "config", Map.of());

			ConfigFile reloaded = new ConfigFile(tmp);
			reloaded.reloadIfChanged();
			assertEquals(StatsRange.LAST_7_DAYS, reloaded.get().journal().statsRange());
			assertEquals(StatsRange.SINCE_PATCH, reloaded.get().calculator().range());
			assertEquals(config.get().journal().byDay(), reloaded.get().journal().byDay());
			assertEquals(calculator.typed(), reloaded.get().calculator().typed(), "typed numbers stay");
			assertEquals(calculator.keyLoot(), reloaded.get().calculator().keyLoot());
			assertEquals(calculator.minedShafts(), reloaded.get().calculator().minedShafts());
			assertEquals(config.get().money(), reloaded.get().money());
		}));
	}

	@Test
	void rangesSaveByNameAndOddOnesFallBack(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"journal\": {\"byDay\": false, \"statsRange\": \"last_5_sessions\"},"
				+ " \"calculator\": {\"typed\": {\"spawn\": 92.0}, \"range\": \"all_time\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(StatsRange.LAST_5_SESSIONS, config.get().journal().statsRange());
		assertEquals(StatsRange.ALL_TIME, config.get().calculator().range());
		assertFalse(byDay(config));
		assertEquals(92.0, config.get().calculator().typed().get("spawn"));
		long time = 1_000;
		// display names, a later mod's range, wrong types
		for (String odd : new String[] {"\"Since patch 0.27.3\"", "\"last_30_days\"", "3", "null", "[]", "{}", "true"}) {
			Files.writeString(file, "{\"journal\": {\"byDay\": false, \"statsRange\": " + odd + "},"
					+ " \"calculator\": {\"typed\": {\"spawn\": 92.0}, \"range\": " + odd + "}}");
			Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
			config.reloadIfChanged();
			assertEquals(StatsRange.ALL_TIME, config.get().journal().statsRange(), odd);
			assertEquals(StatsRange.LAST_5_SESSIONS, config.get().calculator().range(), odd);
			assertFalse(byDay(config), odd);
			assertEquals(92.0, config.get().calculator().typed().get("spawn"), odd);
		}
	}

	// calculator is left out while default, the range alone brings it in and takes it out again
	@Test
	void calculatorRangeIsOnlyWrittenOnceItDiffers(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertFalse(Files.readString(file).contains("calculator"));
		CalculatorSettings picked = CalculatorSettings.DEFAULTS.withRange(StatsRange.LAST_7_DAYS);
		config.save(ConfigSection.CALCULATOR, CalculatorSettings.DEFAULTS, picked);
		JsonObject json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
		assertEquals("last_7_days", json.getAsJsonObject("calculator").get("range").getAsString());
		assertEquals(List.of("typed", "keyLoot", "minedShafts", "range"), List.copyOf(json.getAsJsonObject("calculator").keySet()), "new key last");
		config.save(ConfigSection.CALCULATOR, picked, CalculatorSettings.DEFAULTS);
		assertFalse(Files.readString(file).contains("calculator"));
	}

	// journal saves changed keys only
	@Test
	void statsRangeLeavesTheOtherJournalKeysAlone(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		Files.writeString(file, "{\"journal\": {\"byDay\": false, \"later\": 1}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		JournalSettings before = config.get().journal();
		config.save(ConfigSection.JOURNAL, before, before.withCoinChart(JournalSettings.ChartType.LINE));
		assertFalse(Files.readString(file).contains("statsRange"), "not written till picked");
		config.save(ConfigSection.JOURNAL, before, before.withStatsRange(StatsRange.SINCE_PATCH));
		JsonObject journal = JsonParser.parseString(Files.readString(file)).getAsJsonObject().getAsJsonObject("journal");
		assertEquals("since_patch", journal.get("statsRange").getAsString());
		assertEquals(1, journal.get("later").getAsInt());
		assertFalse(journal.get("byDay").getAsBoolean());
		assertEquals(List.of("byDay", "later", "coinChart", "statsRange"), List.copyOf(journal.keySet()));
	}

	// a section gson cant read keeps its last values, the others still load
	@Test
	void brokenMoneyStillLoadsJournalAndSummary(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"),
				"{\"money\": {\"ironman\": 1}, \"journal\": {\"byDay\": false}, \"summary\": {\"show\": \"chat\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.DEFAULTS, config.get().money());
		assertFalse(byDay(config));
		assertEquals(SummarySettings.Show.CHAT, config.get().summary().show());
	}

	@Test
	void brokenSummaryKeepsMoneyAndJournal(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"),
				"{\"money\": {\"gemTier\": \"fine\"}, \"journal\": {\"byDay\": false}, \"summary\": {\"seconds\": \"1,5\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(MoneySettings.GemTier.FINE, config.get().money().gemTier());
		assertFalse(byDay(config));
		assertEquals(SummarySettings.DEFAULTS, config.get().summary());
	}

	// by day read by hand, odd = default, the rest still loads
	@Test
	void oddJournalNeverFailsTheLoad(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("config.json");
		ConfigFile config = new ConfigFile(tmp);
		long time = 1_000;
		for (String journal : new String[] {"5", "[]", "null", "{}", "{\"byDay\": null}", "{\"byDay\": []}", "{\"byDay\": {}}"}) {
			Files.writeString(file, "{\"journal\": " + journal + ", \"summary\": {\"show\": \"chat\"}}");
			Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
			config.set(ConfigSection.JOURNAL, JournalSettings.DEFAULTS.withByDay(false));
			config.set(ConfigSection.SUMMARY, SummarySettings.DEFAULTS);
			config.reloadIfChanged();
			assertTrue(byDay(config), journal);
			assertEquals(SummarySettings.Show.CHAT, config.get().summary().show(), journal);
		}
		for (String off : new String[] {"1", "\"yes\"", "false"}) {
			Files.writeString(file, "{\"journal\": {\"byDay\": " + off + "}}");
			Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
			config.reloadIfChanged();
			assertFalse(byDay(config), off);
		}
		Files.writeString(file, "{\"journal\": {\"byDay\": \"true\"}}");
		Files.setLastModifiedTime(file, FileTime.fromMillis(time += 1_000));
		config.reloadIfChanged();
		assertTrue(byDay(config));
	}

	@Test
	void noSummaryInOldFiles(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("config.json"), "{\"money\": {\"gemTier\": \"fine\"}}");
		ConfigFile config = new ConfigFile(tmp);
		config.reloadIfChanged();
		assertEquals(SummarySettings.DEFAULTS, config.get().summary());
		assertTrue(config.get().summary().show().toast());
		assertFalse(config.get().summary().show().chat());
	}
}
