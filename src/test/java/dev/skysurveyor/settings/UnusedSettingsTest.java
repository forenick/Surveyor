package dev.skysurveyor.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.ItemPrice;
import dev.skysurveyor.money.PriceRules;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.SummarySettings.Show;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnusedSettingsTest {
	private static final Settings IRONMAN = Settings.DEFAULTS.withMoney(MoneySettings.DEFAULTS.withIronman(true));

	private static List<String> unused(Settings settings) {
		return SettingsList.CATEGORIES.stream().flatMap(c -> c.settings().stream()).filter(s -> s.unused(settings) != null)
				.map(Setting::name).toList();
	}

	private static Settings show(Show show) {
		return Settings.DEFAULTS.withSummary(SummarySettings.DEFAULTS.withShow(show));
	}

	@Test
	void defaultsUseEverything() {
		assertEquals(List.of(), unused(Settings.DEFAULTS));
		assertEquals(List.of(), unused(show(Show.BOTH)));
	}

	@Test
	void ironmanDimsSellingGemsAndMetal() {
		assertEquals(List.of("Sell price", "Bazaar tax", "Gem tier", "Gemstone Crystals", "Metal tier", "Keys and campfires"),
				unused(IRONMAN));
		assertEquals("Not used while Ironman is on", setting("Gem tier").unused(IRONMAN));
		assertNull(setting("Prices for old shafts").unused(IRONMAN), "ironman still picks saved or todays npc prices");
		assertNull(setting("Ironman").unused(IRONMAN));
	}

	@Test
	void noToastDimsToastSettings() {
		for (Show show : List.of(Show.CHAT, Show.OFF)) {
			assertEquals(List.of("Toast style", "Toast position", "Toast time"), unused(show(show)), show.name());
		}
		assertEquals("Not used while the toast is off", setting("Toast time").unused(show(Show.OFF)));
	}

	// made up round prices, npc ones on a few items so ironman has something to value
	private static final Map<String, ItemPrice> PRICES = Map.ofEntries(
			Map.entry("FLAWLESS_JADE_GEM", new ItemPrice(2_400_000.0, 2_600_000.0, null)),
			Map.entry("FINE_JADE_GEM", new ItemPrice(28_000.0, 30_000.0, null, 19_200.0)),
			Map.entry("PERFECT_JADE_GEM", new ItemPrice(15_000_000.0, 16_000_000.0, null)),
			Map.entry("FINE_ONYX_GEM", new ItemPrice(30_000.0, 33_000.0, null, 25_600.0)),
			Map.entry("FLAWLESS_ONYX_GEM", new ItemPrice(2_000_000.0, 2_200_000.0, null)),
			Map.entry("GOBLIN_EGG", new ItemPrice(3_000.0, 4_000.0, null, 10.0)),
			Map.entry("TUNGSTEN", new ItemPrice(60.0, 70.0, null, 10.0)),
			Map.entry("ENCHANTED_TUNGSTEN", new ItemPrice(10_000.0, 11_000.0, null, 1_600.0)),
			Map.entry("REFINED_TUNGSTEN", new ItemPrice(1_700_000.0, 1_800_000.0, null)),
			Map.entry("ENCHANTED_UMBER", new ItemPrice(12_000.0, 13_000.0, null, 1_600.0)),
			Map.entry("TUNGSTEN_KEY", new ItemPrice(2_600_000.0, 2_800_000.0, null)),
			Map.entry("UMBER_KEY", new ItemPrice(2_900_000.0, 3_000_000.0, null)),
			Map.entry("BEJEWELED_HANDLE", new ItemPrice(450_000.0, 500_000.0, null)),
			Map.entry("REFINED_UMBER", new ItemPrice(2_400_000.0, 2_500_000.0, null)),
			Map.entry("MATCH_STICKS", new ItemPrice(30.0, 900.0, null)));
	private static final ItemIds IDS = ItemIds.bundled(Map.of());
	private static final TextPatterns PATTERNS = TextPatterns.defaults();

	private static List<JournalEntry> entries() {
		List<CorpseLoot> loots = List.of(
				new CorpseLoot("Lapis", false, List.of(new ItemCount("Flawed Onyx Gemstone", 20), new ItemCount("Goblin Egg", 4)), 5_000),
				new CorpseLoot("Tungsten", false, List.of(new ItemCount("Enchanted Tungsten", 8)), 9_000),
				new CorpseLoot("Umber", false, List.of(new ItemCount("Enchanted Umber", 8)), 9_000));
		JournalEntry gems = EntryBuilder.entry().loots(loots).campfiresAt(List.of(30_000L)).gems(new GemYield("Jade", 0, 800, 0, 0, 0))
				.crystals(List.of("Jade Crystal")).build();
		JournalEntry metal = EntryBuilder.entry().shaftId("TUNG_1").loots(loots).campfiresAt(List.of(30_000L))
				.metal(new MetalYield("Tungsten", 92, 2, 0)).build();
		return List.of(gems, metal);
	}

	private static List<Object> values(Settings settings) {
		List<Object> out = new ArrayList<>();
		for (JournalEntry entry : entries()) {
			out.add(ShaftValue.of(entry, PRICES::get, settings.money(), IDS, PATTERNS));
			for (ShaftValue.Part part : ShaftValue.Part.values()) {
				out.add(PriceRules.free(settings.money(), part, "Tungsten Key"));
				out.add(PriceRules.free(settings.money(), part, "Umber Key"));
			}
		}
		return out;
	}

	// every value a dimmed setting can take gives the same coins, so dimming tells the truth
	@Test
	void dimmedMoneySettingsChangeNothing() {
		List<Object> base = values(IRONMAN);
		for (Setting setting : SettingsList.CATEGORIES.getFirst().settings()) {
			if (setting.unused(IRONMAN) == null) continue;
			for (Settings variant : variants(setting, IRONMAN)) assertEquals(base, values(variant), setting.name());
		}
	}

	// and w/o ironman the same settings do change coins, so the fixture can tell
	@Test
	void fixtureSeesTheSettingsWithoutIronman() {
		for (Setting setting : SettingsList.CATEGORIES.getFirst().settings()) {
			if (setting.unused(IRONMAN) == null) continue;
			List<Settings> variants = variants(setting, Settings.DEFAULTS);
			assertNotEquals(values(variants.getFirst()), values(variants.getLast()), setting.name());
		}
	}

	private static List<Settings> variants(Setting setting, Settings base) {
		return switch (setting) {
			case Choice<?> choice -> choices(choice, base);
			case Setting.Number number -> List.of(number.set().apply(base, number.min()), number.set().apply(base, 50.0));
			case Setting.Toggle toggle -> List.of(toggle.set().apply(base, false), toggle.set().apply(base, true));
		};
	}

	private static <T> List<Settings> choices(Choice<T> choice, Settings base) {
		return choice.options().stream().map(o -> choice.set().apply(base, o.value())).toList();
	}

	private static Setting setting(String name) {
		return SettingsList.CATEGORIES.stream().flatMap(c -> c.settings().stream()).filter(s -> s.name().equals(name)).findFirst()
				.orElseThrow();
	}
}
