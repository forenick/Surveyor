package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.ItemPrice;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.settings.MoneySettings;

import static org.junit.jupiter.api.Assertions.assertEquals;

// chat + sacks lines from recording 6, counts in the made up sacks msgs are made up
class RareDropTest {
	private static final TextPatterns PATTERNS = TextPatterns.defaults();
	private static final String CITRINE = "§6§lRARE DROP! §9 Fine Citrine Gemstone §b(§b+105 Magic Find§b)";
	private static final String PERIDOT = "§6§lRARE DROP! §9 Fine Peridot Gemstone §b(§b+103 Magic Find§b)";
	private static final String JEWEL = "§6§lRARE DROP! §9Glacite Jewel §b(§b+103 Magic Find§b)";
	// recording 6 at 16:40.4, cut down
	private static final String SACKS = "§6[Sacks] §a+621§e items§e.§8 (Last 14s.)";
	private static final String FINE_CITRINE = "§a  +2 §e Fine Citrine Gemstone§8 (Gemstones Sack)§a";
	private static final String FINE_PERIDOT = "§a  +1 §e Fine Peridot Gemstone§8 (Gemstones Sack)§a";
	private static final String FLAWED_CITRINE = "§a  +80 §e Flawed Citrine Gemstone§8 (Gemstones Sack)§a";
	// round made up prices, not real ones
	private static final Map<String, ItemPrice> PRICES = Map.of(
			"FLAWLESS_CITRINE_GEM", new ItemPrice(8_000_000.0, 8_400_000.0, null),
			"GLACITE_JEWEL", new ItemPrice(20_000.0, 21_000.0, null));

	private final List<Shaft> left = new ArrayList<>();
	private final ShaftTracker tracker = new ShaftTracker(PATTERNS, event -> {
		if (event instanceof ShaftLeft l) left.add(l.shaft());
	});

	private JournalEntry entry() {
		return JournalEntry.of(left.getFirst(), 0, null, PATTERNS);
	}

	private void enter(String id) {
		tracker.onWorldChange(0);
		tracker.onScoreboard(1_000, List.of("§e§lSKYBLOCK", "§711/17/26 §8m19AB " + id));
	}

	private void sacks(long now, String... lines) {
		tracker.onChat(now, SACKS, List.of("§aAdded items:\n" + String.join("\n", lines)));
	}

	// like AMET_1 + FAIR_1 in recording 6: left before any sacks msg
	@Test
	void shortShaftKeepsItsRareDrops() {
		enter("AMET_1");
		tracker.onChat(5_000, CITRINE, List.of());
		tracker.onChat(5_000, PERIDOT, List.of());
		tracker.onChat(6_000, JEWEL, List.of());
		tracker.onChat(7_000, CITRINE, List.of());
		tracker.onWorldChange(9_000);

		JournalEntry entry = entry();
		assertEquals(Map.of("Fine Citrine Gemstone", 2L, "Fine Peridot Gemstone", 1L, "Glacite Jewel", 1L), entry.otherDrops());
		assertEquals(List.of(), entry.loots(), "not corpse loot");
		ShaftValue value = ShaftValue.of(entry, PRICES::get, MoneySettings.DEFAULTS, ItemIds.bundled(Map.of()), PATTERNS);
		double tax = 1 - 1.125 / 100;
		// insta sell = buy order, fine = flawless / 80
		assertEquals((2 * 8_000_000 / 80.0 + 20_000) * tax, value.total(Part.OTHER), 1e-6);
		assertEquals(1, value.unpriced().size(), "peridot has no price here");
	}

	// like JADE_1 in recording 6: the sacks msg after the chat line has the same items
	@Test
	void sacksCopyCountedOnce() {
		enter("JADE_1");
		tracker.onChat(5_000, CITRINE, List.of());
		tracker.onChat(6_000, PERIDOT, List.of());
		tracker.onChat(8_000, CITRINE, List.of());
		sacks(20_000, FINE_CITRINE, FINE_PERIDOT);
		tracker.onWorldChange(30_000);
		assertEquals(Map.of("Fine Citrine Gemstone", 2L, "Fine Peridot Gemstone", 1L), entry().otherDrops());
	}

	// sacks has 1 more than chat said (f.e. a corpse that never made it into the loot block), that one still counts
	@Test
	void sacksExtraStillCounts() {
		enter("JADE_1");
		tracker.onChat(5_000, CITRINE, List.of());
		sacks(20_000, FINE_CITRINE);
		tracker.onWorldChange(30_000);
		assertEquals(Map.of("Fine Citrine Gemstone", 2L), entry().otherDrops());
	}

	@Test
	void ownGemIsntMined() {
		enter("CITR_1");
		tracker.onChat(5_000, CITRINE, List.of());
		sacks(20_000, FLAWED_CITRINE, "§a  +1 §e Fine Citrine Gemstone§8 (Gemstones Sack)§a");
		tracker.onWorldChange(30_000);

		JournalEntry entry = entry();
		// 80 flawed = 1 fine, the rare fine isnt in it
		assertEquals(1.0, entry.gems().reportedFine(), 1e-9);
		assertEquals(0, entry.gems().fine());
		assertEquals(Map.of("Fine Citrine Gemstone", 1L), entry.otherDrops());
	}

	@Test
	void ownGemBeforeAnySacksMsg() {
		enter("CITR_1");
		sacks(4_000, FLAWED_CITRINE);
		tracker.onChat(5_000, CITRINE, List.of());
		tracker.onWorldChange(9_000);

		JournalEntry entry = entry();
		assertEquals(1.0, entry.gems().reportedFine(), 1e-9);
		assertEquals(Map.of("Fine Citrine Gemstone", 1L), entry.otherDrops());
	}

	// outside a shaft the candidate is never saved, sacks + OutsideMining count it
	@Test
	void outsideShaftsNothingSaved() {
		tracker.onWorldChange(0);
		tracker.onChat(5_000, CITRINE, List.of());
		tracker.onWorldChange(9_000);
		assertEquals(List.of(), left);
	}
}
