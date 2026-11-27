package dev.skysurveyor.settings;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.SettingsList.Category;
import dev.skysurveyor.settings.SettingsList.Section;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsListTest {
	private static Category money() {
		return SettingsList.CATEGORIES.stream().filter(c -> c.id().equals(SettingsList.MONEY)).findFirst().orElseThrow();
	}

	private static Setting named(String name) {
		return SettingsList.CATEGORIES.stream().flatMap(c -> c.settings().stream()).filter(s -> s.name().equals(name)).findFirst()
				.orElseThrow();
	}

	@Test
	void defaultsAreDefault() {
		for (Category c : SettingsList.CATEGORIES) {
			assertTrue(c.isDefault(Settings.DEFAULTS), c.name());
			for (Setting s : c.settings()) assertTrue(s.isDefault(Settings.DEFAULTS), s.name());
		}
	}

	// new category w/o its png = page crashes on open
	@Test
	void everyCategoryHasAnIcon() {
		for (Category c : SettingsList.CATEGORIES) {
			assertNotNull(SettingsListTest.class.getResource("/assets/skysurveyor/textures/gui/" + c.icon() + ".png"), c.name());
		}
	}

	@Test
	void namesAndIdsAreUnique() {
		Set<String> names = new HashSet<>();
		for (Category c : SettingsList.CATEGORIES) {
			for (Setting s : c.settings()) assertTrue(names.add(s.name()), s.name());
		}
		assertEquals(SettingsList.CATEGORIES.size(), SettingsList.CATEGORIES.stream().map(Category::id).distinct().count());
	}

	// every option of every choice round trips through set + get
	@Test
	void choicesSetWhatTheyShow() {
		for (Category c : SettingsList.CATEGORIES) {
			for (Setting s : c.settings()) {
				if (!(s instanceof Choice<?> choice)) continue;
				roundTrip(choice);
			}
		}
	}

	private static <T> void roundTrip(Choice<T> choice) {
		for (Setting.Option<T> option : choice.options()) {
			Settings set = choice.set().apply(Settings.DEFAULTS, option.value());
			assertEquals(option.value(), choice.get().apply(set), choice.name());
			assertEquals(choice.isDefault(option), choice.isDefault(set), choice.name());
		}
	}

	@Test
	void everyMoneyFieldHasASetting() {
		MoneySettings changed = new MoneySettings(MoneySettings.SellPrice.SELL_OFFER, MoneySettings.GemTier.FINE, 2.0,
				MoneySettings.OldShafts.TODAY, MoneySettings.Crystals.ZERO, true, MoneySettings.MetalTier.REFINED,
				MoneySettings.OwnMetal.CRAFT);
		Settings all = Settings.DEFAULTS.withMoney(changed);
		assertFalse(money().isDefault(all));
		assertEquals(Settings.DEFAULTS, money().reset(all));
	}

	@Test
	void resetCategoryLeavesOthers() {
		Settings changed = Settings.DEFAULTS.withJournal(JournalSettings.DEFAULTS.withByDay(false)).withMoney(MoneySettings.DEFAULTS.withGemTier(MoneySettings.GemTier.FINE));
		Settings reset = money().reset(changed);
		assertEquals(MoneySettings.DEFAULTS, reset.money());
		assertFalse(reset.journal().byDay());
	}

	@Test
	void taxParses() {
		Setting.Number tax = (Setting.Number) named("Bazaar tax");
		assertEquals(1.25, tax.parse(" 1,25 "));
		assertEquals(0.0, tax.parse("0"));
		assertEquals(100.0, tax.parse("100"));
		assertNull(tax.parse("100.5"));
		assertNull(tax.parse("-1"));
		assertNull(tax.parse(""));
		assertNull(tax.parse("abc"));
		assertNull(tax.parse("NaN"));
		assertNull(tax.parse("Infinity"));
		assertNull(tax.parse("5f"));
		assertNull(tax.parse("2d"));
		assertNull(tax.parse("1e1"));
		assertNull(tax.parse("0x1p1"));
		assertNull(tax.parse("1.2.3"));
		assertEquals(2.0, tax.parse("2."));
		assertEquals(0.5, tax.parse(".5"));
		assertEquals("1.125%", tax.defaultText());
		assertEquals("2", Setting.Number.format(2.0));
	}

	@Test
	void searchFindsNamesDescriptionsAndChoices() {
		assertEquals(List.of("Bazaar tax"), names(money().matching("TAX")));
		assertEquals(List.of("Gemstone Crystals"), names(money().matching("perfect")));
		assertEquals(List.of("Ironman", "Gem tier", "Gemstone Crystals"), names(money().matching("gem")));
		assertTrue(money().matching("nothing like this").sections().isEmpty());
		assertEquals(money().settings().size(), money().matching("  ").settings().size());
	}

	// "money" or "selling" finds the whole part
	@Test
	void searchFindsCategoryAndSectionNames() {
		assertEquals(money().settings().size(), money().matching("Coins").settings().size());
		// ironman names the selling part
		assertEquals(List.of("Ironman", "Sell price", "Bazaar tax"), names(money().matching("selling")));
	}

	private static List<String> names(Category category) {
		return category.settings().stream().map(Setting::name).toList();
	}

	@Test
	void defaultTexts() {
		assertEquals("Insta-sell", named("Sell price").defaultText());
		assertEquals("Flawless", named("Gem tier").defaultText());
		assertEquals("On", named("Group by day").defaultText());
	}

	@Test
	void labelsComeFromTheOptions() {
		assertEquals("Tungsten or Umber", SettingsList.label(MoneySettings.MetalTier.RAW));
		assertEquals("Enchanted", SettingsList.label(MoneySettings.MetalTier.ENCHANTED));
	}

	private static Category category(String id) {
		return SettingsList.CATEGORIES.stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
	}

	// ids are what code keys on, names can change
	@Test
	void categoryIdsAndOrder() {
		assertEquals(List.of("money", "shaft", "journal", "hud", "dev"), SettingsList.CATEGORIES.stream().map(Category::id).toList());
		assertEquals(List.of("Coins", "In a shaft", "Journal", "HUD", "Bug reports"),
				SettingsList.CATEGORIES.stream().map(Category::name).toList());
		// only bug reports sits under the line
		assertEquals(List.of("dev"), SettingsList.CATEGORIES.stream().filter(Category::apart).map(Category::id).toList());
		assertEquals("Copy debug info", category(SettingsList.DEV).settings().getFirst().name());
	}

	@Test
	void everyPreviewNamesASetting() {
		Set<String> all = new HashSet<>();
		for (Category c : SettingsList.CATEGORIES) c.settings().forEach(s -> all.add(s.name()));
		for (String name : List.of("Shaft summary", "Toast style", "Toast position", "Toast time", "Entry title", "Title time",
				"Mine or skip", "Fossil waypoints")) {
			assertTrue(all.contains(name), name);
			assertNotNull(SettingsList.preview(named(name)), name);
		}
		assertNull(SettingsList.preview(named("Gem tier")));
	}

	// the grey line under a name follows the pick
	@Test
	void linesFollowThePick() {
		Settings chat = Settings.DEFAULTS.withSummary(SummarySettings.DEFAULTS.withShow(SummarySettings.Show.CHAT));
		assertEquals("A short card for a few seconds.", named("Shaft summary").line(Settings.DEFAULTS));
		assertEquals("The full summary in chat.", named("Shaft summary").line(chat));
		Settings noTitle = Settings.DEFAULTS.withEntryTitle(Settings.DEFAULTS.entryTitle().withShow(false));
		assertEquals("No title when you enter a shaft.", named("Entry title").line(noTitle));
		for (Category c : SettingsList.CATEGORIES) {
			for (Setting s : c.settings()) {
				assertFalse(s.line(Settings.DEFAULTS).isBlank(), s.name());
				if (s instanceof Setting.Toggle t) assertFalse(t.on().equals(t.off()), s.name());
			}
		}
	}

	private static List<String> shown(String category, Settings settings) {
		return category(category).shown(settings).stream().map(Setting::name).toList();
	}

	@Test
	void childrenFoldWhileTheirParentIsOff() {
		assertEquals(List.of("Entry title", "Title time", "Mine or skip", "Fossil waypoints", "Shaft summary", "Toast style",
				"Toast position", "Toast time"), shown(SettingsList.SHAFT, Settings.DEFAULTS));
		Settings noTitle = Settings.DEFAULTS.withEntryTitle(Settings.DEFAULTS.entryTitle().withShow(false));
		assertEquals(List.of("Entry title", "Fossil waypoints", "Shaft summary", "Toast style", "Toast position", "Toast time"),
				shown(SettingsList.SHAFT, noTitle));
		for (SummarySettings.Show show : List.of(SummarySettings.Show.CHAT, SummarySettings.Show.OFF)) {
			Settings noToast = Settings.DEFAULTS.withSummary(SummarySettings.DEFAULTS.withShow(show));
			assertEquals(List.of("Entry title", "Title time", "Mine or skip", "Fossil waypoints", "Shaft summary"),
					shown(SettingsList.SHAFT, noToast), show.name());
		}
		Settings auto = Settings.DEFAULTS.withDev(Settings.DEFAULTS.dev().withAutoRecord(DevSettings.AutoRecord.MINING_AREAS));
		assertFalse(shown(SettingsList.DEV, Settings.DEFAULTS).contains("Keep auto recordings"));
		assertTrue(shown(SettingsList.DEV, auto).contains("Keep auto recordings"));
		// fold only, its old files still get deleted, so no dim
		assertNull(named("Keep auto recordings").unused(Settings.DEFAULTS));
		// coins never fold, ironman only dims
		Settings ironman = Settings.DEFAULTS.withMoney(MoneySettings.DEFAULTS.withIronman(true));
		assertEquals(money().settings().size(), money().shown(ironman).size());
	}

	// a child sits right under its parent or another child
	@Test
	void childrenSitUnderAParent() {
		for (Category c : SettingsList.CATEGORIES) {
			for (Section section : c.sections()) {
				List<Setting> list = section.settings();
				for (int i = 0; i < list.size(); i++) {
					if (list.get(i).isChild()) assertTrue(i > 0, list.get(i).name());
				}
				if (!list.isEmpty()) assertFalse(list.getFirst().isChild(), section.name());
			}
		}
	}

	// search finds folded ones too
	@Test
	void searchFindsFoldedSettings() {
		Settings off = Settings.DEFAULTS.withSummary(SummarySettings.DEFAULTS.withShow(SummarySettings.Show.OFF));
		assertFalse(category(SettingsList.SHAFT).shown(off).contains(named("Toast style")));
		assertEquals(List.of("Shaft summary", "Toast style", "Toast position", "Toast time"), names(category(SettingsList.SHAFT).matching("toast")));
		assertEquals(List.of("Keep auto recordings"), names(category(SettingsList.DEV).matching("keep auto")));
		assertEquals(List.of("Edit HUD"), names(category(SettingsList.HUD).matching("editor")));
	}

	@Test
	void changedCounts() {
		Settings two = Settings.DEFAULTS.withSummary(SummarySettings.DEFAULTS.withShow(SummarySettings.Show.BOTH).withStyle(SummarySettings.Style.FRAME));
		assertEquals(2, category(SettingsList.SHAFT).changed(two));
		assertEquals(0, money().changed(two));
	}
}
