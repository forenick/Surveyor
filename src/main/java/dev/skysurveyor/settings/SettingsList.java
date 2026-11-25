package dev.skysurveyor.settings;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.settings.DevSettings.AutoRecord;
import dev.skysurveyor.settings.DevSettings.KeepRecordings;
import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.MetalTier;
import dev.skysurveyor.settings.MoneySettings.OldShafts;
import dev.skysurveyor.settings.MoneySettings.OwnMetal;
import dev.skysurveyor.settings.MoneySettings.SellPrice;
import dev.skysurveyor.settings.Setting.Choice;
import dev.skysurveyor.settings.Setting.Number;
import dev.skysurveyor.settings.Setting.Option;
import dev.skysurveyor.settings.Setting.Toggle;
import dev.skysurveyor.settings.Setting.Unused;
import dev.skysurveyor.settings.SummarySettings.Position;
import dev.skysurveyor.settings.SummarySettings.Show;
import dev.skysurveyor.settings.SummarySettings.Style;

// new setting = one entry here, the page builds its card, search + reset from it
public final class SettingsList {
	public record Section(String name, List<Setting> settings) {
	}

	// icon = textures/gui/<icon>.png
	public record Category(String id, String name, String icon, String description, List<Section> sections) {
		public List<Setting> settings() {
			return sections.stream().flatMap(s -> s.settings().stream()).toList();
		}

		public Category matching(String query) {
			List<Section> kept = new ArrayList<>();
			for (Section section : sections) {
				List<Setting> hits = section.settings().stream().filter(s -> s.matches(query)).toList();
				if (!hits.isEmpty()) kept.add(new Section(section.name(), hits));
			}
			return new Category(id, name, icon, description, kept);
		}

		public boolean isDefault(Settings settings) {
			return settings().stream().allMatch(s -> s.isDefault(settings));
		}

		public Settings reset(Settings settings) {
			for (Setting setting : settings()) settings = setting.reset(settings);
			return settings;
		}
	}

	public static final String MONEY = "money";
	public static final String JOURNAL = "journal";
	public static final String DEV = "dev";

	// money/PriceRules: ironman returns before reading these. old shafts still picks saved vs todays npc prices
	private static final Unused IRONMAN = new Unused(s -> s.money().isIronman(), "Not used while Ironman is on");
	private static final Unused NO_TOAST = new Unused(s -> !s.summary().show().toast(), "Not used while the toast is off");
	private static final Unused NO_TITLE = new Unused(s -> !s.entryTitle().isShow(), "Not used while the entry title is off");

	public static final List<Category> CATEGORIES = List.of(
			new Category(MONEY, "Money", "money_icon", "How a shaft turns into coins, everywhere the journal and HUD show coins.", List.of(
					new Section("Profile", List.of(
							new Toggle("Ironman", "NPC sell prices only, gems as Fine, metal as Tungsten or Umber. Items with no NPC price "
									+ "show without coins, keys and campfires cost nothing. Selling, Gems and Metal settings don't apply.",
									s -> s.money().isIronman(), (s, v) -> s.withMoney(s.money().withIronman(v))))),
					new Section("Selling", List.of(
							new Choice<>("Sell price", "Price used for everything sold on the Bazaar.", List.of(
									new Option<>(SellPrice.INSTA_SELL, "Insta-sell", "The highest buy order, what you get right away."),
									new Option<>(SellPrice.SELL_OFFER, "Sell offer", "The lowest sell offer, more coins but you wait for a buyer.")),
									s -> s.money().sellPrice(), (s, v) -> s.withMoney(s.money().withSellPrice(v)), IRONMAN),
							new Number("Bazaar tax", "Taken off everything sold on the Bazaar. Not off lowest BIN or NPC prices.", "%", 0, 100,
									s -> s.money().bazaarTax(), (s, v) -> s.withMoney(s.money().withBazaarTax(v)), IRONMAN))),
					new Section("Gems", List.of(
							new Choice<>("Gem tier", "Rough, Flawed and Fine gems all count as this tier. Crafting up is free.", List.of(
									new Option<>(GemTier.FLAWLESS, "Flawless", "Every 80 Fine count as 1 Flawless."),
									new Option<>(GemTier.FINE, "Fine", "Gems count at the Fine price.")),
									s -> s.money().gemTier(), (s, v) -> s.withMoney(s.money().withGemTier(v)), IRONMAN),
							new Choice<>("Gemstone Crystals", "Crystals from crystal shafts. They can't be sold.", List.of(
									new Option<>(Crystals.PERFECT_MINUS_FLAWLESS, "Perfect minus 5 Flawless",
											"A Perfect gem minus 5 Flawless of that gem."),
									new Option<>(Crystals.ZERO, "Zero", "Crystals add no coins.")),
									s -> s.money().crystals(), (s, v) -> s.withMoney(s.money().withCrystals(v)), IRONMAN))),
					new Section("Metal", List.of(
							new Choice<>("Metal tier", "Tungsten mined in Tungsten shafts and Umber in Umber shafts count as this tier. "
									+ "Metal from corpses keeps its own price.", List.of(
											new Option<>(MetalTier.ENCHANTED, "Enchanted", "Every 160 count as 1 Enchanted. Crafting is free."),
											new Option<>(MetalTier.RAW, "Tungsten or Umber", "The price of the metal you mined."),
											new Option<>(MetalTier.REFINED, "Refined", "Every 160 Enchanted count as 1 Refined, an hour in the Forge.")),
									s -> s.money().metalTier(), (s, v) -> s.withMoney(s.money().withMetalTier(v)), IRONMAN),
							new Choice<>("Keys and campfires", "What a Tungsten Key, Umber Key or Portable Campfire costs.", List.of(
									new Option<>(OwnMetal.INSTA_BUY, "Insta-buy", "Bought on the Bazaar."),
									new Option<>(OwnMetal.CRAFT, "Craft from your metal",
											"A key is 192 Enchanted Tungsten or Umber at your sell price plus a Bejeweled Handle. "
													+ "A campfire is 160 Enchanted Umber plus 16 Match-Sticks."),
									new Option<>(OwnMetal.DONT_COUNT, "Don't count", "Keys and campfires cost nothing. "
											+ "A shaft shows how many you used.")),
									s -> s.money().ownMetal(), (s, v) -> s.withMoney(s.money().withOwnMetal(v)), IRONMAN))),
					new Section("Old shafts", List.of(
							new Choice<>("Prices for old shafts",
									"Each shaft saves its prices when it ends. Shafts from before Surveyor saved prices always use today's.", List.of(
											new Option<>(OldShafts.THAT_DAY, "That day's", "The prices saved when each shaft ended."),
											new Option<>(OldShafts.TODAY, "Today's", "Every shaft at the latest prices.")),
									s -> s.money().oldShafts(), (s, v) -> s.withMoney(s.money().withOldShafts(v))))))),
			new Category(JOURNAL, "Journal", "journal_icon", "The journal screen, what you see in a shaft and the summary "
					+ "after each one.", List.of(
					new Section("Overview", List.of(
							new Toggle("Group by day", "Sessions sit under a row per day with that day's totals.",
									s -> s.journal().byDay(), (s, v) -> s.withJournal(s.journal().withByDay(v))))),
					new Section("Entry title", List.of(
							new Toggle("Entry title", "A title when you enter a shaft: its type, then its corpses, "
									+ "whether it has a fossil and your Mayhem buff once the game shows them.",
									s -> s.entryTitle().isShow(), (s, v) -> s.withEntryTitle(s.entryTitle().withShow(v))),
							new Number("Title time", "How long the title stays after the last thing it learned.", "s",
									EntryTitleSettings.MIN_SECONDS, EntryTitleSettings.MAX_SECONDS,
									s -> s.entryTitle().seconds(), (s, v) -> s.withEntryTitle(s.entryTitle().withSeconds(v)), NO_TITLE))),
					new Section("Waypoints", List.of(
							new Toggle("Fossil waypoints", "A box around the fossil you can mine for Fossil Essence, seen through walls, "
									+ "with how far it is. Only in shafts whose fossil Surveyor knows, gone once you mined most of it.",
									s -> s.waypoints().isFossil(), (s, v) -> s.withWaypoints(s.waypoints().withFossil(v)), null,
									new Setting.Swatch("Waypoint colour", s -> s.waypoints().colorRgb(),
											(s, v) -> s.withWaypoints(s.waypoints().withColor(v)))))),
					new Section("Shaft summary", List.of(
							new Choice<>("Show summary", "What you see when you leave a shaft.", List.of(
									new Option<>(Show.TOAST, "Toast", "A short card that pops up for a few seconds."),
									new Option<>(Show.CHAT, "Chat", "The full summary in chat."),
									new Option<>(Show.BOTH, "Both", "The card and the chat summary."),
									new Option<>(Show.OFF, "Off", "Nothing, the journal still saves the shaft.")),
									s -> s.summary().show(), (s, v) -> s.withSummary(s.summary().withShow(v))),
							new Choice<>("Toast style", "How the card looks.", List.of(
									new Option<>(Style.VANILLA, "Vanilla", "Like Minecraft's own toasts."),
									new Option<>(Style.FRAME, "Frame", "The tooltip frame the HUD boxes use."),
									new Option<>(Style.DARK, "Dark", "A dark see-through box.")),
									s -> s.summary().style(), (s, v) -> s.withSummary(s.summary().withStyle(v)), NO_TOAST),
							new Choice<>("Toast position", "Where the card shows up.", List.of(
									new Option<>(Position.TOP_RIGHT, "Top right", "Where Minecraft's toasts go, slides in."),
									new Option<>(Position.MOVABLE, "Movable", "Anywhere you like, drag it in Edit HUD.")),
									s -> s.summary().position(), (s, v) -> s.withSummary(s.summary().withPosition(v)), NO_TOAST),
							new Number("Toast time", "How long the card stays. Minecraft's Notification Time setting stretches it.", "s",
									SummarySettings.MIN_SECONDS, SummarySettings.MAX_SECONDS,
									s -> s.summary().seconds(), (s, v) -> s.withSummary(s.summary().withSeconds(v)), NO_TOAST))))),
			new Category(DEV, "Developer", "dev_icon", "For testing and bug reports.", List.of(
					new Section("Recording", List.of(
							new Choice<>("Auto-record", "Records what the game shows you to a file in skysurveyor/recordings, "
									+ "for bug reports and testing.", List.of(
											new Option<>(AutoRecord.OFF, "Off", "Only /sv record start records."),
											new Option<>(AutoRecord.MINING_AREAS, "Mining areas",
													"Starts when you mine in the Dwarven Mines or a shaft, stops when your session ends.")),
									s -> s.dev().autoRecord(), (s, v) -> s.withDev(s.dev().withAutoRecord(v))),
							new Choice<>("Keep auto recordings", "Older auto recordings are deleted when the game starts. "
									+ "Recordings you started yourself always stay.", List.of(
											new Option<>(KeepRecordings.DAYS_7, "7 days", "About 1 MB per hour played."),
											new Option<>(KeepRecordings.DAYS_30, "30 days", "About 1 MB per hour played."),
											new Option<>(KeepRecordings.DAYS_90, "90 days", "About 1 MB per hour played."),
											new Option<>(KeepRecordings.FOREVER, "Forever", "Never deleted.")),
									s -> s.dev().keepRecordings(), (s, v) -> s.withDev(s.dev().withKeepRecordings(v))),
							new Toggle("Record screens", "Recordings also note what menus like Sell Sacks show. Your own inventory isn't recorded.",
									s -> s.dev().isRecordScreens(), (s, v) -> s.withDev(s.dev().withRecordScreens(v))),
							new Toggle("Anonymize recordings", "Player names in new recordings become Player1, Player2 and so on, "
									+ "yours becomes You. Names that are also SkyBlock words stay.",
									s -> s.dev().isAnonymize(), (s, v) -> s.withDev(s.dev().withAnonymize(v))))),
					new Section("Debug", List.of(
							new Toggle("Debug overlay", "A box in the top left with what Surveyor sees right now: area, shaft, session, "
									+ "spawn timer, last event, errors, prices, ping and recording. /sv debug log lists the last events.",
									s -> s.dev().isDebugOverlay(), (s, v) -> s.withDev(s.dev().withDebugOverlay(v))))))));

	private SettingsList() {
	}

	// a choices option label, f.e. for tooltips outside the settings page
	public static String label(Object value) {
		for (Category category : CATEGORIES) {
			for (Setting setting : category.settings()) {
				if (!(setting instanceof Choice<?> choice)) continue;
				for (Option<?> option : choice.options()) {
					if (option.value().equals(value)) return option.label();
				}
			}
		}
		return String.valueOf(value);
	}
}
