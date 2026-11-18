package dev.skysurveyor.money;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.OutsideMining;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.MoneySettings;
import dev.skysurveyor.settings.MoneySettings.Crystals;
import dev.skysurveyor.settings.MoneySettings.GemTier;
import dev.skysurveyor.settings.MoneySettings.MetalTier;
import dev.skysurveyor.settings.MoneySettings.OldShafts;
import dev.skysurveyor.settings.MoneySettings.SellPrice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutsideCoinsTest {
	private static final TextPatterns PATTERNS = TextPatterns.defaults();
	// made up prices, not real ones
	private static final Map<String, ItemPrice> PRICES = Map.of(
			"FLAWLESS_JADE_GEM", new ItemPrice(8_000_000.0, 8_100_000.0, null),
			"FINE_JADE_GEM", new ItemPrice(90_000.0, 95_000.0, null),
			"GLOSSY_GEMSTONE", new ItemPrice(50_000.0, 55_000.0, null),
			"GLACITE", new ItemPrice(8.0, 9.0, null));
	private static final ItemIds IDS = ItemIds.bundled(Map.of("Glossy Gemstone", "GLOSSY_GEMSTONE", "Glacite", "GLACITE"));
	private static final MoneySettings NO_TAX = new MoneySettings(SellPrice.INSTA_SELL, GemTier.FLAWLESS, 0.0, OldShafts.THAT_DAY,
			Crystals.ZERO, false, MetalTier.ENCHANTED, MoneySettings.OwnMetal.INSTA_BUY);

	private static MiningEntry mining(long session, long at, Map<String, Long> items) {
		return new MiningEntry(MiningEntry.VERSION, session, at, items, false, null);
	}

	// 800 flawed + 6400 rough = 11 fine jade
	private static MiningEntry tunnels() {
		Map<String, Long> items = new LinkedHashMap<>();
		items.put("Flawed Jade Gemstone", 800L);
		items.put("Glossy Gemstone", 2L);
		items.put("Rough Jade Gemstone", 6_400L);
		items.put("Glacite", 100L);
		items.put("Mystery Rock", 1L);
		return mining(0, 1_000, items);
	}

	@Test
	void gemsMergedIntoOneLinePerGem() {
		List<Line> lines = MiningValue.lines(tunnels(), PRICES::get, NO_TAX, IDS, PATTERNS);
		// 11 fine = 11/80 flawless at 8M
		assertEquals(new Line(Part.MINED, "Flawless Jade Gemstone", 11 / 80.0, 1_100_000.0), lines.getFirst());
		assertEquals(new Line(Part.GLOSSY, "Glossy Gemstone", 2, 100_000.0), lines.get(1));
		assertEquals(new Line(Part.OTHER, "Glacite", 100, 800.0), lines.get(2));
		assertEquals(new Line(Part.OTHER, "Mystery Rock", 1, null), lines.get(3));
		assertEquals(1_200_800, MiningValue.coins(tunnels(), PRICES::get, NO_TAX, IDS, PATTERNS), 1e-6);

		List<Line> fine = MiningValue.lines(tunnels(), PRICES::get, NO_TAX.withGemTier(GemTier.FINE), IDS, PATTERNS);
		assertEquals(new Line(Part.MINED, "Fine Jade Gemstone", 11, 990_000.0), fine.getFirst());
		assertEquals(Map.of("Jade", 11.0), OutsideMining.fineGems(List.of(tunnels()), PATTERNS));
	}

	// recorded hour of recording 5 (money-tracker-design.md section 1): session 16:02:24 to 17:10:37,
	// shafts 55.58M (55.27M recorded + 0.31M before), outside 24.09M reported + 1.69M tail
	private static final long START = 1_794_754_944_000L;
	private static final long LAST_EXIT = START + 68 * 60_000L + 13_000;

	private final Map<JournalEntry, ShaftValue> shafts = new HashMap<>();
	private final Map<MiningEntry, Double> outside = new HashMap<>();

	private JournalEntry shaft(long entered, long millis, double net) {
		JournalEntry entry = EntryBuilder.entry().sessionId(START).enteredAt(entered).durationMillis(millis).build();
		shafts.put(entry, new ShaftValue(List.of(new Line(Part.MINED, "Fine Jasper Gemstone", 1, net)), List.of()));
		return entry;
	}

	private MiningEntry outside(long session, long at, double coins) {
		MiningEntry entry = mining(session, at, Map.of("Flawed Jade Gemstone", 1L));
		outside.put(entry, coins);
		return entry;
	}

	@Test
	void recordedHourCountsOutsideCoins() {
		List<JournalEntry> entries = List.of(shaft(START + 60_000, 60_000, 30_000_000), shaft(LAST_EXIT - 600_000, 600_000, 25_580_000));
		List<MiningEntry> mining = List.of(outside(START, START + 30_000, 24_090_000), outside(START, LAST_EXIT - 600_000, 1_690_000),
				outside(9, 10, 5_000_000));
		SessionSummary session = JournalStats.sessions(entries, mining, List.of()).getFirst();
		assertEquals(2, session.mining().size(), "other sessions mining left out");
		assertEquals(LAST_EXIT, session.end());
		assertEquals(55_580_000, MoneyStats.coins(session.entries(), shafts::get), 1e-6);
		assertEquals(81_360_000, MoneyStats.session(session, shafts::get, outside::get), 1e-6);
		// 81.4M and 71.6M / h in the design doc
		assertEquals(71_560_225, MoneyStats.perHour(session, shafts::get, outside::get), 1);
	}

	@Test
	void sessionEndsAtItsLastActivity() {
		List<JournalEntry> entries = List.of(shaft(START + 60_000, 60_000, 30_000_000), shaft(LAST_EXIT - 600_000, 600_000, 25_580_000));
		List<MiningEntry> mining = List.of(outside(START, START + 30_000, 24_090_000), outside(START, LAST_EXIT + 600_000, 2_000_000));
		SessionSummary session = JournalStats.sessions(entries, mining, List.of()).getFirst();
		assertEquals(LAST_EXIT + 600_000, session.end(), "tunnels after the last shaft count w/ their time");
		assertEquals(81_670_000 * 3_600_000.0 / (LAST_EXIT + 600_000 - START), MoneyStats.perHour(session, shafts::get, outside::get), 1e-3);
		assertEquals(LAST_EXIT + 600_000 - START, JournalStats.allTime(entries, mining, List.of()).timeMillis());
		assertEquals(LAST_EXIT - START, JournalStats.allTime(entries).timeMillis(), "w/o mining like before");
	}

	@Test
	void statsTotalsAndBreakdown() {
		List<JournalEntry> entries = List.of(shaft(START + 60_000, 60_000, 30_000_000));
		List<MiningEntry> mining = List.of(tunnels());
		MoneyStats money = MoneyStats.of(entries, shafts::get, mining, e -> MiningValue.lines(e, PRICES::get, NO_TAX, IDS, PATTERNS), false);
		assertEquals(30_000_000, money.coins(), 1e-6);
		assertEquals(30_000_000, money.perShaft(), 1e-6, "per shaft stays shaft-only");
		assertEquals(1_200_800, money.outside(), 1e-6);
		assertEquals(31_200_800, money.total(), 1e-6);

		List<String> lines = CoinBreakdown.detailed(money, null, 0, true, null, 1000);
		assertTrue(lines.contains("§fOutside shafts §6+1.2M §84%"), lines.toString());
		assertTrue(lines.contains("  §7§5Flawless Jade Gemstone §8x0.1 §6+1.1M"), lines.toString());
		assertTrue(lines.contains("  §7§fGlossy Gemstone §8x2 §6+100.0k"), lines.toString());
		assertTrue(lines.contains("§8Outside shafts counted since 0.24.0"));
		assertTrue(lines.contains("§c1 item without a price"), "outside items count too");
		assertFalse(CoinBreakdown.detailed(money, null, 0, false, null, 1000).contains("§8Outside shafts counted since 0.24.0"));
		assertTrue(CoinBreakdown.basic(money, null, 0).contains("§7Outside shafts §6+1.2M"));

		List<String> tile = CoinBreakdown.session(money, 40_000_000.0, 0, null, NO_TAX, 1000);
		assertTrue(tile.contains("§fShafts §6+30.0M"), tile.toString());
		assertTrue(tile.contains("  §7Mined gems §6+30.0M"), tile.toString());
		assertTrue(tile.contains("§fOutside shafts §6+1.2M"), tile.toString());
		assertTrue(tile.contains("  §7§5Flawless Jade Gemstone §8x0.1 §6+1.1M"), tile.toString());
		assertTrue(tile.contains("§7Earned §6+31.2M"), tile.toString());
		assertTrue(tile.contains("§7Per shaft §6+30.0M §8(shafts only)"), tile.toString());
		assertTrue(tile.contains("§7Per hour §6+40.0M"), tile.toString());
		List<String> shift = CoinBreakdown.sessionShort(money, 40_000_000.0, 0, null, NO_TAX);
		assertTrue(shift.contains("§fOutside shafts §6+1.2M"), shift.toString());
		assertFalse(shift.contains("  §7Mined gems §6+30.0M"), "no parts w/ shift");
		assertFalse(shift.contains(CoinBreakdown.SHIFT_HINT));

		List<String> shaftOnly = CoinBreakdown.session(MoneyStats.of(entries, shafts::get, false), null, 0, null, NO_TAX, 1000);
		assertEquals(List.of("§fCoins this session", "", "§fShafts §6+30.0M", "  §7Mined gems §6+30.0M", "", "§7Earned §6+30.0M", "",
				"§7Per hour §8after 5 minutes", "§7Per shaft §6+30.0M", "", "§7Gems as Flawless, insta-sell", "", CoinBreakdown.SHIFT_HINT),
				shaftOnly);
	}

	// made up prices: 11 fine jade 990k, 2 glossy 100k, 100 glacite 800 = 1,090,800 before tax
	@Test
	void taxLineAddsUpToBeforeTax() {
		MoneySettings fine = NO_TAX.withGemTier(GemTier.FINE).withBazaarTax(1.125);
		List<MiningEntry> mining = List.of(tunnels());
		JournalEntry keyShaft = shaft(START + 60_000, 60_000, 0);
		shafts.put(keyShaft, new ShaftValue(List.of(new Line(Part.KEYS, "Umber Key", 1, -3_000_000.0)), List.of()));
		List<JournalEntry> entries = List.of(keyShaft);
		MoneyStats taxed = MoneyStats.of(entries, shafts::get, mining, e -> MiningValue.lines(e, PRICES::get, fine, IDS, PATTERNS), false);
		MoneyStats untaxed = MoneyStats.of(entries, shafts::get, mining,
				e -> MiningValue.lines(e, PRICES::get, fine.withBazaarTax(0), IDS, PATTERNS), false);
		assertEquals(1_090_800 * 0.98875, taxed.income(), 1e-6);
		assertEquals(-3_000_000, taxed.costs(), 1e-6, "keys bought, no tax");
		double tax = MoneyStats.tax(taxed, untaxed);
		assertEquals(1_090_800, taxed.income() + tax, 1e-6);

		List<String> tile = CoinBreakdown.session(taxed, null, 0, tax, fine, 1000);
		assertTrue(tile.contains("§7Earned §6+1.1M"), tile.toString());
		assertTrue(tile.contains("§7Spent §c-3.0M"), tile.toString());
		assertTrue(tile.contains("§fNet §c-1.9M"), tile.toString());
		assertTrue(tile.contains("§7Gems as Fine, insta-sell"), tile.toString());
		assertTrue(tile.contains("§7Bazaar tax taken off: 12.3k"), tile.toString());

		List<String> ironman = CoinBreakdown.sessionShort(taxed, null, 0, 0.0, fine.withIronman(true));
		assertTrue(ironman.contains("§7Ironman: NPC sell prices"), ironman.toString());
		assertTrue(ironman.stream().noneMatch(l -> l.contains("tax")), ironman.toString());
	}

	@Test
	void sessionCoinRecordsCountOutsideCoins() {
		List<JournalEntry> entries = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) entries.add(shaft(START + i * 120_000L, 60_000, 1_000_000));
		List<MiningEntry> mining = List.of(outside(START, START + 30_000, 4_000_000));
		List<SessionSummary> sessions = JournalStats.sessions(entries, mining, List.of());
		PersonalBests.Coins coins = PersonalBests.Coins.of(e -> shafts.get(e).net(), outside::get);
		assertEquals(9_000_000, best("session_coins", null, entries, sessions, coins).value(), 1e-6);
		// 9M over 9 min
		assertEquals(60_000_000, best("session_coin_rate", null, entries, sessions, coins).value(), 1e-6);
		// one shaft type = only those shafts
		assertNull(best("session_coins", "JASP", entries, sessions, coins).value());
		assertEquals(5_000_000, best("session_coins", null, entries, sessions, e -> shafts.get(e).net()).value(), 1e-6);
	}

	private static PersonalBests.Result best(String stat, String shaft, List<JournalEntry> entries, List<SessionSummary> sessions,
			PersonalBests.Coins coins) {
		return PersonalBests.best(new PersonalBests.Def(stat, shaft, null, null), entries, sessions, coins);
	}
}
