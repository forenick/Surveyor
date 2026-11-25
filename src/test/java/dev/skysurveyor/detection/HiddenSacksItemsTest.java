package dev.skysurveyor.detection;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.ShaftEvent.CorpseLooted;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;

import static org.junit.jupiter.api.Assertions.assertEquals;

// recording 8 PERI_C: lapis loot w/ flawed citrine x40 + peridot x20, next sacks msg lists neither, ends "+60 other items."
class HiddenSacksItemsTest {
	private static Shaft looted() {
		Shaft shaft = new Shaft(0);
		shaft.apply(new CorpseLooted(new CorpseLoot("Lapis", true, List.of(new ItemCount("Enchanted Glacite", 2),
				new ItemCount("Flawed Citrine Gemstone", 40), new ItemCount("Flawed Peridot Gemstone", 20)), 4_756)), 585_312);
		return shaft;
	}

	@Test
	void lootInOtherItemsIsntTakenOff() {
		Shaft shaft = looted();
		shaft.apply(new SacksChanged(23, List.of(new ItemCount("Enchanted Glacite", 36), new ItemCount("Fine Peridot Gemstone", 1)),
				List.of(), 60, 0), 606_194);
		assertEquals(Map.of("Enchanted Glacite", 2L), shaft.notMinedInSacks());
	}

	@Test
	void fullListTakesAllLootOff() {
		Shaft shaft = looted();
		shaft.apply(new SacksChanged(23, List.of(new ItemCount("Enchanted Glacite", 36), new ItemCount("Flawed Citrine Gemstone", 40),
				new ItemCount("Flawed Peridot Gemstone", 300)), List.of(), 0, 0), 606_194);
		assertEquals(Map.of("Enchanted Glacite", 2L, "Flawed Citrine Gemstone", 40L, "Flawed Peridot Gemstone", 20L), shaft.notMinedInSacks());
	}

	@Test
	void cappedListTakesOffAtMostWhatItShows() {
		Shaft shaft = looted();
		shaft.apply(new SacksChanged(23, List.of(new ItemCount("Enchanted Glacite", 1), new ItemCount("Flawed Peridot Gemstone", 300)),
				List.of(), 40, 0), 606_194);
		assertEquals(Map.of("Enchanted Glacite", 1L, "Flawed Peridot Gemstone", 20L), shaft.notMinedInSacks());
	}
}
