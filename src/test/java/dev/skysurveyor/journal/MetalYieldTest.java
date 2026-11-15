package dev.skysurveyor.journal;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetalYieldTest {
	private static final String BAR = "§a§l" + "▬".repeat(64);

	private final List<JournalEntry> entries = new ArrayList<>();
	private final Journal journal = new Journal(TextPatterns.defaults(), () -> true, entries::add);
	private long now;
	private final ShaftTracker tracker = new ShaftTracker(TextPatterns.defaults(), event -> journal.onEvent(event, now));

	// nicks TUNG_1 (journal 2026-11-11): 92 tungsten + 1 enchanted from a lapis corpse, iron ingot source unknown
	@Test
	void nicksTungstenShaft() {
		enter("TUNG_1");
		lapisLoot("§r    §9Enchanted Tungsten", "§r    §9Yellow Goblin Egg");
		sacks("§a  +92 §fTungsten§8 (Mining Sack)§a", "§a  +1 §9Enchanted Tungsten§8 (Mining Sack)§a",
				"§a  +90 §fIron Ingot§8 (Mining Sack)§a", "§a  +1 §9Yellow Goblin Egg§8 (Goblin Sack)§a");
		leave();

		JournalEntry entry = entries.getFirst();
		assertEquals(new MetalYield("Tungsten", 92, 0, 0), entry.metal(), "corpse enchanted tungsten isnt mined");
		assertEquals(Map.of("Iron Ingot", 90L), entry.otherDrops());
		assertNull(entry.gems());
	}

	@Test
	void umberShaftCountsEveryTierButNotTungsten() {
		enter("UMBE_1");
		sacks("§a  +3,000 §fUmber§8 (Mining Sack)§a", "§a  +2 §9Enchanted Umber§8 (Mining Sack)§a",
				"§a  +50 §fTungsten§8 (Mining Sack)§a");
		leave();
		assertEquals(new MetalYield("Umber", 3_000, 2, 0), entries.getFirst().metal());
		assertEquals(Map.of("Tungsten", 50L), entries.getFirst().otherDrops());
	}

	@Test
	void gemShaftKeepsMetalInOtherDrops() {
		enter("JADE_1");
		sacks("§a  +80 §aFlawed Jade Gemstone§8 (Gemstones Sack)§a", "§a  +50 §fTungsten§8 (Mining Sack)§a");
		leave();
		assertNull(entries.getFirst().metal());
		assertEquals(Map.of("Tungsten", 50L), entries.getFirst().otherDrops());
		assertEquals(new GemYield("Jade", 0, 80, 0, 0, 0), entries.getFirst().gems());
	}

	@Test
	void noMetalMinedSavesNoField(@TempDir Path dir) throws Exception {
		enter("TUNG_1");
		sacks("§a  +3 §fEnchanted Glacite§8 (Mining Sack)§a");
		leave();
		assertNull(entries.getFirst().metal());

		JournalFile file = new JournalFile(dir.resolve("journal.jsonl"), Runnable::run);
		file.append(entries.getFirst());
		file.append(EntryBuilder.from(entries.getFirst()).metal(new MetalYield("Tungsten", 48_211, 2, 0)).build());
		List<String> lines = Files.readAllLines(dir.resolve("journal.jsonl"));
		assertFalse(lines.get(0).contains("metal"), lines.get(0));
		assertTrue(lines.get(1).endsWith(",\"metal\":{\"metal\":\"Tungsten\",\"raw\":48211,\"enchanted\":2,\"refined\":0}}"), lines.get(1));
		assertEquals(new MetalYield("Tungsten", 48_211, 2, 0), new JournalFile(dir.resolve("journal.jsonl"), Runnable::run).read().get(1).metal());
	}

	@Test
	void tiersAre160Apart() {
		MetalYield metal = new MetalYield("Tungsten", 48_211, 2, 0);
		assertEquals(48_531, metal.in(MetalYield.RAW), 1e-9);
		assertEquals(48_531 / 160.0, metal.in(MetalYield.ENCHANTED), 1e-9);
		assertEquals(48_531 / 25_600.0, metal.in(MetalYield.REFINED), 1e-9);
		assertEquals("48,531 Tungsten", metal.text(MetalYield.RAW));
		assertEquals("303.3 Enchanted Tungsten", metal.text(MetalYield.ENCHANTED));
		assertEquals("1.9 Refined Tungsten", metal.text(MetalYield.REFINED));
		assertEquals("48,531", metal.amount(MetalYield.RAW));
		assertEquals("303.3", metal.amount(MetalYield.ENCHANTED));
		assertEquals("303.3 Enchanted", metal.shortText(MetalYield.ENCHANTED));
		assertEquals("1.9 Refined", metal.shortText(MetalYield.REFINED));
		assertEquals("48,531 Tungsten", metal.shortText(MetalYield.RAW));
		assertEquals(25_600, Ladder.metalOf("UMBE").base(MetalYield.REFINED));
		assertNull(Ladder.metalOf("JADE"));
	}

	@Test
	void mostMetalByRawCount() {
		JournalEntry tungsten = EntryBuilder.entry().shaftId("TUNG_1").metal(new MetalYield("Tungsten", 30_000, 0, 0)).build();
		JournalEntry umber = EntryBuilder.entry().shaftId("UMBE_1").metal(new MetalYield("Umber", 1_000, 200, 0)).build();
		JournalEntry gems = EntryBuilder.entry().shaftId("JADE_1").build();
		assertEquals(umber, best("shaft_metal", List.of(tungsten, umber, gems)).entry());
		assertNull(best("shaft_metal", List.of(gems)).entry());
	}

	private static PersonalBests.Result best(String stat, List<JournalEntry> entries) {
		return PersonalBests.best(new PersonalBests.Def(stat, null, null, null), entries, JournalStats.sessions(entries), e -> null);
	}

	// same numbers as before the ladder
	@Test
	void gemLadderKeepsItsRatios() {
		assertEquals(80, GemYield.RECIPE);
		assertEquals(6_400.0, GemYield.countPerFine("Rough"));
		assertEquals(80.0, GemYield.countPerFine("Flawed"));
		assertEquals(1.0, GemYield.countPerFine("Fine"));
		assertEquals(1.0, GemYield.countPerFine("Flawless"));
		assertEquals(1.0, GemYield.countPerFine("Weird"));
		GemYield gems = new GemYield("Jade", 98_296, 20_094, 3, 1_130, 231);
		assertEquals(98_296 / 6400.0 + 20_094 / 80.0 + 3, gems.reportedFine());
		assertEquals((98_296 + 1_130) / 6400.0 + (20_094 + 231) / 80.0 + 3, gems.fineEquivalent());
	}

	private void enter(String id) {
		now = 0;
		tracker.onWorldChange(now);
		tracker.onScoreboard(now, List.of("§e§lSKYBLOCK", "§711/11/26 §8m19AB " + id));
	}

	private void lapisLoot(String... items) {
		now += 5_000;
		List<String> lines = new ArrayList<>(List.of(BAR, "§r  §9§lLAPIS §b§lCORPSE LOOT! ", "§r  §a§lREWARDS"));
		lines.addAll(List.of(items));
		lines.add(BAR);
		for (String line : lines) tracker.onChat(now, line, List.of());
	}

	private void sacks(String... added) {
		now += 5_000;
		tracker.onChat(now, "§6[Sacks] §a+1§e items§e.§8 (Last 5s.)", List.of("§aAdded items:\n" + String.join("\n", added)));
	}

	private void leave() {
		now += 15_000;
		tracker.onWorldChange(now);
	}
}
