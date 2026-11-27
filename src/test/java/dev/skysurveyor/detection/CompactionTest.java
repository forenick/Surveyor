package dev.skysurveyor.detection;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.Ladder;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// sacks msgs, rec 6 (11-17, skyblock 0.27.3)
class CompactionTest {
	private static final String END = "\n\n§8This message can be disabled in the settings.";
	private final ChatParser parser = new ChatParser(TextPatterns.defaults());

	// 0:01.8
	private SacksChanged first() {
		return sacks("§6[Sacks] §a+6,536§e items§e, §c-4,736§e items§e.§8 (Last 18s.)",
				"§aAdded items:\n§a  +32 §eEnchanted Glacite§8 (Enchanted Mining Sack)§a\n§a  +1 §eEnchanted Hard Stone§8 (Enchanted Mining Sack)§a\n"
						+ "§a  +4,133 §eGlacite§8 (Mining Sack)§a\n§a  +111 §eHard Stone§8 (Mining Sack)§a\n"
						+ "§a  +2 §eRefined Mineral§8 (Mining Sack, Events Sack)§a\n§a  +488 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a\n"
						+ "§a  +1,769 §e Rough Jade Gemstone§8 (Gemstones Sack)§a" + END,
				"§cRemoved items:\n§c  -4,160 §eGlacite§8 (Mining Sack)§c\n§c  -576 §eHard Stone§8 (Mining Sack)§c" + END);
	}

	// 0:18.2, nothing compacted
	private SacksChanged second() {
		return sacks("§6[Sacks] §a+2,532§e items§e.§8 (Last 15s.)",
				"§aAdded items:\n§a  +426 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a\n"
						+ "§a  +2,106 §e Rough Jade Gemstone§8 (Gemstones Sack)§a" + END);
	}

	// 0:37.4
	private SacksChanged third() {
		return sacks("§6[Sacks] §a+6,580§e items§e, §c-3,360§e items§e.§8 (Last 18s.)",
				"§aAdded items:\n§a  +21 §eEnchanted Glacite§8 (Enchanted Mining Sack)§a\n§a  +3,408 §eGlacite§8 (Mining Sack)§a\n"
						+ "§a  +329 §eHard Stone§8 (Mining Sack)§a\n§a  +457 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a\n"
						+ "§a  +2,365 §e Rough Jade Gemstone§8 (Gemstones Sack)§a" + END,
				"§cRemoved items:\n§c  -3,360 §eGlacite§8 (Mining Sack)§c" + END);
	}

	@Test
	void stepsComeFromTheLadders() {
		assertEquals(new Ladder.Step("Enchanted Glacite", 160), Ladder.compactsInto("Glacite"));
		assertEquals(new Ladder.Step("Enchanted Hard Stone", 576), Ladder.compactsInto("Hard Stone"));
		assertEquals(new Ladder.Step("Flawed Jade Gemstone", 80), Ladder.compactsInto("Rough Jade Gemstone"));
		assertEquals(new Ladder.Step("Fine Jade Gemstone", 80), Ladder.compactsInto("Flawed Jade Gemstone"));
		assertEquals(new Ladder.Step("Enchanted Tungsten", 160), Ladder.compactsInto("Tungsten"));
		assertNull(Ladder.compactsInto("Enchanted Glacite"));
		assertNull(Ladder.compactsInto("Flawless Jade Gemstone"));
		assertNull(Ladder.compactsInto("Refined Mineral"));
		// compaction ladders arent shaft metals
		assertNull(Ladder.metal("Glacite"));
		assertNull(Ladder.metalOfItem("Enchanted Hard Stone"));
	}

	@Test
	void removesWithTheirCompactedFormAreCompaction() {
		Map<String, Long> compacted = Compaction.of(first(), Map.of());
		assertEquals(Map.of("Glacite", 4_160L, "Hard Stone", 576L), compacted);

		Map<String, Long> expected = new LinkedHashMap<>();
		expected.put("Enchanted Glacite", 32L);
		expected.put("Enchanted Hard Stone", 1L);
		expected.put("Refined Mineral", 2L);
		expected.put("Flawed Jade Gemstone", 488L);
		expected.put("Rough Jade Gemstone", 1_769L);
		assertEquals(expected, new Compaction().net(added(first()), compacted));
	}

	// 4,133 in, 4,160 out: 27 owed till glacite comes in again
	@Test
	void shortfallComesOffTheNextAdds() {
		Compaction compaction = new Compaction();
		Map<String, Long> one = compaction.net(added(first()), Compaction.of(first(), Map.of()));
		Map<String, Long> two = compaction.net(added(second()), Compaction.of(second(), Map.of()));
		Map<String, Long> three = compaction.net(added(third()), Compaction.of(third(), Map.of()));

		assertNull(one.get("Glacite"));
		assertNull(one.get("Hard Stone"));
		assertEquals(Map.of("Flawed Jade Gemstone", 426L, "Rough Jade Gemstone", 2_106L), two);
		// 3,408 - 3,360 - 27
		assertEquals(21L, three.get("Glacite"));
		// 329 - 465 owed from the 1st msg (111 in, 576 out), still 136 owed
		assertNull(three.get("Hard Stone"));
		assertEquals(21L, three.get("Enchanted Glacite"));
		assertEquals(Map.of(), compaction.net(Map.of("Hard Stone", 136L), Map.of()));
		assertEquals(Map.of("Hard Stone", 5L), compaction.net(Map.of("Hard Stone", 5L), Map.of()));
	}

	// 28:21.4 after supercrafting 2,814 flawed + 663 fine jade: removes = craft inputs
	@Test
	void supercraftInputsArentCompaction() {
		SacksChanged sacks = sacks("§6[Sacks] §a+3,477§e items§e, §c-278,160§e items§e.§8 (Last 7s.)",
				"§aAdded items:\n§a  +663 §e Fine Jade Gemstone§8 (Gemstones Sack)§a\n"
						+ "§a  +2,814 §e Flawed Jade Gemstone§8 (Gemstones Sack)§a" + END,
				"§cRemoved items:\n§c  -53,040 §e Flawed Jade Gemstone§8 (Gemstones Sack)§c\n"
						+ "§c  -225,120 §e Rough Jade Gemstone§8 (Gemstones Sack)§c" + END);
		assertEquals(Map.of(), Compaction.of(sacks, Map.of("Flawed Jade Gemstone", 2_814L, "Fine Jade Gemstone", 663L)));
	}

	// synthetic, removes w/o an add never recorded (withdraw, sell sacks)
	@Test
	void plainRemoveIsIgnored() {
		SacksChanged sacks = new SacksChanged(30, List.of(new ItemCount("Hard Stone", 300)), List.of(new ItemCount("Glacite", 2_000)), 0, 0);
		assertEquals(Map.of(), Compaction.of(sacks, Map.of()));
	}

	// synthetic, gem compactor unconfirmed (review 0.2). netted like glacite if so
	@Test
	void gemCompactionNetsLikeGlacite() {
		SacksChanged sacks = new SacksChanged(30,
				List.of(new ItemCount("Flawed Jade Gemstone", 412), new ItemCount("Rough Jade Gemstone", 2_000), new ItemCount("Fine Jade Gemstone", 2)),
				List.of(new ItemCount("Rough Jade Gemstone", 1_920), new ItemCount("Flawed Jade Gemstone", 160)), 0, 0);
		Map<String, Long> compacted = Compaction.of(sacks, Map.of());
		assertEquals(Map.of("Rough Jade Gemstone", 1_920L, "Flawed Jade Gemstone", 160L), compacted);
		assertEquals(Map.of("Flawed Jade Gemstone", 252L, "Rough Jade Gemstone", 80L, "Fine Jade Gemstone", 2L),
				new Compaction().net(added(sacks), compacted));
	}

	// synthetic: sell or withdraw + pristine flawed in one msg
	@Test
	void roughRemovedWithPristineFlawedIsntCompaction() {
		SacksChanged sold = new SacksChanged(30, List.of(new ItemCount("Flawed Jade Gemstone", 3), new ItemCount("Rough Jade Gemstone", 400)),
				List.of(new ItemCount("Rough Jade Gemstone", 5_000)), 0, 0);
		assertEquals(Map.of(), Compaction.of(sold, Map.of()));
		// whole steps, but more than 3 flawed worth
		SacksChanged withdrawn = new SacksChanged(30, List.of(new ItemCount("Flawed Jade Gemstone", 3)),
				List.of(new ItemCount("Rough Jade Gemstone", 640)), 0, 0);
		assertEquals(Map.of(), Compaction.of(withdrawn, Map.of()));
	}

	// synthetic: 1,920 rough made 24 of the 412 flawed, 160 flawed made the 2 fine
	@Test
	void mixBeforeCompactingTakesOffWhatCompactionMade() {
		Map<String, Long> added = Map.of("Flawed Jade Gemstone", 412L, "Rough Jade Gemstone", 2_000L, "Fine Jade Gemstone", 2L);
		Map<String, Long> compacted = Map.of("Rough Jade Gemstone", 1_920L, "Flawed Jade Gemstone", 160L);
		assertEquals(Map.of("Flawed Jade Gemstone", 388L, "Rough Jade Gemstone", 2_000L, "Fine Jade Gemstone", 0L),
				Compaction.beforeCompacting(added, compacted));
	}

	@Test
	void owedCanBeHandedOn() {
		Compaction shaft = new Compaction();
		shaft.net(Map.of("Glacite", 20L, "Enchanted Glacite", 1L), Map.of("Glacite", 160L));
		assertEquals(Map.of("Glacite", 140L), shaft.owed());
		Compaction outside = new Compaction();
		outside.owe(shaft.owed());
		assertEquals(Map.of("Glacite", 60L), outside.net(Map.of("Glacite", 200L), Map.of()));
	}

	private SacksChanged sacks(String legacy, String... hovers) {
		return (SacksChanged) parser.parse(legacy, List.of(hovers));
	}

	private static Map<String, Long> added(SacksChanged sacks) {
		Map<String, Long> added = new LinkedHashMap<>();
		sacks.added().forEach(item -> added.merge(item.item(), item.count(), Long::sum));
		return added;
	}
}
