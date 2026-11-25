package dev.skysurveyor.effects;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// held items, chat + footer from recordings 2026-11-08 (potato 284s, filet 287s, splash 293s) and 2026-11-15
class EffectTimersTest {
	private static final String POTATO = "§5Chilled Pristine Potato";
	private static final List<String> POTATO_LORE = List.of("§8Consumable", "", "§7Grants §5+0.1 Pristine §7while in §bGlacite",
			"§bMineshafts§7.", "§7Duration: §a60m", "", "§eRight-click to consume!", "", "§5§lEPIC");
	private static final String FILET = "§9Filet O' Fortune";
	private static final List<String> FILET_LORE = List.of("§8Consumable", "", "§7Grants §6+20 Mining Fortune.", "§7Duration: §a60m", "",
			"§eRight-click to consume!", "", "§9§lRARE");
	private static final String SPLASH = "§aCold Resistance IV Splash Potion";
	private static final List<String> SPLASH_LORE = List.of("", "§bCold Resistance IV §f(51:12)", "§7Gain §b+10 Cold Resistance§7.", "",
			"§a§lUNCOMMON");
	private static final String BUFF = "§r§a§lBUFF! §fYou splashed yourself with §r§bCold Resistance IV§r§f! Press TAB or type /effects to view your active effects!";
	private static final String FLASK_FOOTER = "§r\n§a§lActive Effects§r\n§7You have a §cGod Potion §7active! §d5 hours§r\n"
			+ "§7You have §e4 §7non-god effects.§r\n§7Use \"§6/effects§7\" to see them!§r\n§6Fiesta Flask I§r §f54m§r\n§r§s§r\n"
			+ "§d§lCookie Buff§r\n§f6 days, 18 hours§r\n§r§s§r\n§aRanks, Boosters & MORE! §c§lSTORE.HYPIXEL.NET";
	private static final long HOUR = 3_600_000;

	private final EffectText text = new EffectText(TextPatterns.defaults());
	private final EffectTimers timers = new EffectTimers(text);

	private static String footer(int count, String effect) {
		return FLASK_FOOTER.replace("§e4 ", "§e" + count + " ").replace("§6Fiesta Flask I§r §f54m", effect);
	}

	@Test
	void parsesTimes() {
		assertEquals(new EffectTime(HOUR, 60_000), EffectTime.parse("60m"));
		assertEquals(new EffectTime(71 * 60_000, 60_000), EffectTime.parse("1h 11m"));
		assertEquals(new EffectTime(3_072_000, 1000), EffectTime.parse("51:12"));
		assertEquals(new EffectTime(3_723_000, 1000), EffectTime.parse("1:02:03"));
		assertEquals(new EffectTime(53 * 60_000, 60_000), EffectTime.parse("53 minutes"));
		assertEquals(new EffectTime(6 * 86_400_000L + 18 * HOUR, HOUR), EffectTime.parse("6 days, 18 hours"));
		assertEquals(new EffectTime(71 * 60_000, 60_000), EffectTime.parse("1h11m"));
		assertEquals(new EffectTime(45 * 60_000 + 30_000, 1000), EffectTime.parse("45m30s"));
		assertNull(EffectTime.parse("+20 Mining Fortune"));
		assertNull(EffectTime.parse("Consumable"));
	}

	@Test
	void eatingStartsFromTheLore() {
		timers.onHeld(0, 3, POTATO, 6, () -> POTATO_LORE, false);
		timers.onHeld(500, 3, POTATO, 5, () -> POTATO_LORE, true);
		assertEquals(HOUR, timers.left(Consumable.PRISTINE_POTATO));
		timers.onHeld(2500, 4, FILET, 5, () -> FILET_LORE, false);
		timers.onHeld(3000, 4, FILET, 4, () -> FILET_LORE, true);
		assertEquals(HOUR, timers.left(Consumable.FILET_O_FORTUNE));
	}

	@Test
	void droppingIsNotEating() {
		timers.onHeld(0, 3, POTATO, 6, () -> POTATO_LORE, false);
		timers.onHeld(500, 3, POTATO, 5, () -> POTATO_LORE, false);
		assertNull(timers.left(Consumable.PRISTINE_POTATO));
	}

	@Test
	void lastOneEatenLeavesAnEmptySlot() {
		timers.onHeld(0, 3, POTATO, 1, () -> POTATO_LORE, false);
		timers.onHeld(500, 3, null, 0, () -> null, true);
		assertEquals(HOUR, timers.left(Consumable.PRISTINE_POTATO));
	}

	@Test
	void switchingSlotsIsNotEating() {
		timers.onHeld(0, 3, POTATO, 1, () -> POTATO_LORE, false);
		timers.onHeld(500, 4, null, 0, () -> null, true);
		assertNull(timers.left(Consumable.PRISTINE_POTATO));
	}

	@Test
	void splashStartsFromThePotion() {
		timers.onHeld(0, 2, SPLASH, 1, () -> SPLASH_LORE, false);
		timers.onChat(357, BUFF);
		assertEquals(3_072_000L, timers.left(Consumable.COLD_RESISTANCE));
	}

	@Test
	void splashAfterALongHoldStillStarts() {
		timers.onHeld(0, 2, SPLASH, 1, () -> SPLASH_LORE, false);
		// count drop missed the use window
		timers.onHeld(15_000, 2, null, 0, () -> null, false);
		timers.onChat(15_400, BUFF);
		assertEquals(3_072_000L, timers.left(Consumable.COLD_RESISTANCE));
	}

	@Test
	void eatingAgainResets() {
		timers.onHeld(0, 3, POTATO, 6, () -> POTATO_LORE, false);
		timers.onHeld(500, 3, POTATO, 5, () -> POTATO_LORE, true);
		timers.tick(1000, true);
		timers.tick(1_801_000, true);
		timers.onHeld(1_802_000, 3, POTATO, 4, () -> POTATO_LORE, true);
		assertEquals(HOUR, timers.left(Consumable.PRISTINE_POTATO));
	}

	@Test
	void pausesOffSkyblock() {
		timers.onHeld(0, 3, POTATO, 6, () -> POTATO_LORE, false);
		timers.onHeld(500, 3, POTATO, 5, () -> POTATO_LORE, true);
		timers.tick(1000, true);
		timers.tick(6000, true);
		timers.tick(9000, false);
		timers.tick(9500, true);
		assertEquals(HOUR - 5500, timers.left(Consumable.PRISTINE_POTATO));
	}

	@Test
	void footerStartsAndCorrects() {
		EffectText.Footer flask = text.footer(FLASK_FOOTER);
		assertEquals(4, flask.count());
		assertEquals(Map.of(Consumable.FIESTA_FLASK, new EffectTime(54 * 60_000, 60_000)), flask.shown());
		timers.onFooter(0, flask);
		assertEquals(54 * 60_000 + 30_000, timers.left(Consumable.FIESTA_FLASK));
		timers.onFooter(100_000, text.footer(footer(4, "§6Fiesta Flask I§r §f40m")));
		assertEquals(41 * 60_000 - 1, timers.left(Consumable.FIESTA_FLASK));
	}

	@Test
	void freshEatIgnoresTheOldFooterTime() {
		timers.onHeld(0, 3, POTATO, 6, () -> POTATO_LORE, false);
		timers.onHeld(500, 3, POTATO, 5, () -> POTATO_LORE, true);
		timers.onFooter(1500, text.footer(footer(6, "§5Chilled Pristine Potato I§r §f12m")));
		assertEquals(HOUR, timers.left(Consumable.PRISTINE_POTATO));
	}

	@Test
	void fewerEffectsClearsTheOneEnding() {
		timers.onFooter(0, text.footer(FLASK_FOOTER));
		timers.restore(Map.of("pristine_potato", 20_000L, "filet_o_fortune", 90_000L));
		timers.onFooter(1000, text.footer(footer(3, "§6Fiesta Flask I§r §f54m")));
		assertNull(timers.left(Consumable.PRISTINE_POTATO));
		// ours runs fast, 1:30 left can still be minutes in game
		assertEquals(90_000L, timers.left(Consumable.FILET_O_FORTUNE));
	}

	@Test
	void runsOutAtZero() {
		timers.restore(Map.of("pristine_potato", 8_000L));
		timers.tick(0, true);
		int before = timers.version();
		timers.tick(9_000, true);
		assertNull(timers.left(Consumable.PRISTINE_POTATO));
		assertTrue(timers.version() > before);
	}

	@Test
	void savesAndRestores() {
		timers.restore(Map.of("fiesta_flask", 1234L, "unknown_thing", 5L));
		assertEquals(Map.of("fiesta_flask", 1234L), timers.saved());
	}

	@Test
	void effectsScreen() {
		Map<Consumable, EffectTime> shown = text.screen("§rActive Effects", List.of(
				new EffectText.Item("§aCold Resistance IV", List.of("§7Gain §b+10 Cold Resistance§7.", "§7Remaining: §a12:34")),
				new EffectText.Item("§6Speed II", List.of("§7Remaining: §a2:00"))));
		assertEquals(Map.of(Consumable.COLD_RESISTANCE, new EffectTime(754_000, 1000)), shown);
		timers.onScreen(shown);
		assertEquals(754_500L, timers.left(Consumable.COLD_RESISTANCE));
		// polled while open: counts down inside the shown second, not reset each poll
		timers.tick(0, true);
		timers.tick(400, true);
		timers.onScreen(shown);
		assertEquals(754_100L, timers.left(Consumable.COLD_RESISTANCE));
		assertNull(text.screen("§rSkyBlock Menu", List.of()));
	}

	@Test
	void skyblockTitle() {
		assertTrue(text.skyblock("§e§lSKYBLOCK"));
		assertEquals(false, text.skyblock("§e§lBED WARS"));
	}
}
