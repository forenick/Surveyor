package dev.skysurveyor.data;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import dev.skysurveyor.hud.ToastClock;

import static org.junit.jupiter.api.Assertions.assertEquals;

// expected = corpse loot lines, rec 11-15
class ItemNamesTest {
	private static final ItemNames HYPIXEL = new ItemNames(Map.of("Enchanted Tungsten", "RARE", "Tungsten Key", "EPIC", "Fine Jasper Gemstone", "RARE")::get);

	@AfterEach
	void noIcons() {
		ItemNames.iconsLoaded(false);
	}

	@Test
	void likeCorpseLoot() {
		ItemNames.iconsLoaded(true);
		assertEquals("§a Flawed Peridot Gemstone §8x20", HYPIXEL.styled("Flawed Peridot Gemstone", "20"));
		assertEquals("§9 Fine Citrine Gemstone", HYPIXEL.styled("Fine Citrine Gemstone", "1"), "x1 left out");
		assertEquals("§9Enchanted Tungsten §8x2", HYPIXEL.styled("Enchanted Tungsten", "2"));
		assertEquals("§bGlacite Powder §8x4,536", HYPIXEL.styled("Glacite Powder", "4,536"));
	}

	@Test
	void iconsOnlyWithThePack() {
		assertEquals("§9Fine Jasper Gemstone §8x931", HYPIXEL.styled("Fine Jasper Gemstone", "931"));
		ItemNames.iconsLoaded(true);
		assertEquals("§9 Fine Jasper Gemstone §8x931", HYPIXEL.styled("Fine Jasper Gemstone", "931"));
		assertEquals("§9Fine Ruby Gemstone", ItemNames.BUNDLED.styled("Fine Ruby Gemstone"), "ruby icon unknown");
	}

	@Test
	void rarityFromTheItemList() {
		assertEquals('5', HYPIXEL.color("Tungsten Key"));
		assertEquals('f', HYPIXEL.color("Tungsten"), "no tier = common");
		assertEquals('f', ItemNames.BUNDLED.color("Rough Jade Gemstone"));
		assertEquals('a', ItemNames.BUNDLED.color("Flawed Jade Gemstone"), "gems w/o the item list");
		assertEquals('5', ItemNames.BUNDLED.color("Flawless Jade Gemstone"));
		assertEquals('6', ItemNames.BUNDLED.color("Perfect Jade Gemstone"));
	}

	@Test
	void toastClockPauses() {
		ToastClock clock = new ToastClock();
		assertEquals(0, clock.tick(1_000, false));
		assertEquals(500, clock.tick(1_500, false));
		assertEquals(500, clock.tick(9_000, true), "mod screen open");
		assertEquals(700, clock.tick(9_200, false), "counts on after");
	}
}
