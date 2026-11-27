package dev.skysurveyor.calc;

import java.util.EnumMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.YourNumbers.Num;
import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.ShaftId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// gains from ShaftModelTest.nicksNumbers (guide_model.py)
class VerdictTest {
	private static final YourNumbers NICK = ShaftModelTest.numbers(ShaftModelTest.nick(), "PERI");
	private static final ShaftModel.Guide GUIDE = ShaftModel.run(NICK, ShaftModelTest.PRICES, 2_810_000.0, GameData.defaults());

	private static ShaftId id(String type) {
		return new ShaftId(type + "_1", type, "1");
	}

	private static List<Corpse> corpses(String... types) {
		return java.util.Arrays.stream(types).map(t -> new Corpse(t, false)).toList();
	}

	@Test
	void lapisDecideIt() {
		// peridot -0.09M at 0 lapis, +0.59M at 1, +1.27M at 2. < 0 = skip (nick 11-26)
		assertEquals("§cSkip: -90.6k §7over leaving", Verdict.of(GUIDE, NICK, id("PERI"), corpses("Umber", "Tungsten")).text());
		assertEquals("§eYour call: +591.4k §7over leaving", Verdict.of(GUIDE, NICK, id("PERI"), corpses("Lapis", "Umber")).text());
		assertEquals("§aMine: +1.3M §7over leaving", Verdict.of(GUIDE, NICK, id("PERI"), corpses("Lapis", "Lapis", "Tungsten")).text());
		// ruby 4 lapis is still -1.4M
		assertEquals("§cSkip: -1.4M §7over leaving", Verdict.of(GUIDE, NICK, id("RUBY"), corpses("Lapis", "Lapis", "Lapis", "Lapis")).text());
	}

	@Test
	void campfire() {
		// guides campfire cell, 2 lapis
		ShaftModel.Shaft two = GUIDE.row("PERI").lapis().get(2);
		assertEquals(two.campfire(), Verdict.campfire(GUIDE, NICK, id("PERI"), corpses("Lapis", "Lapis"), 0), 1e-6);
		assertEquals(-1_981_791.4, two.campfire(), 0.5);
		// 2nd one adds less: 150 -> 200 cold
		double perCold = ColdModel.exposure(755) / 100;
		double second = ColdModel.secondsUntil(200, perCold) - ColdModel.secondsUntil(150, perCold);
		assertEquals(second * two.extraSecond() - 2_810_000, Verdict.campfire(GUIDE, NICK, id("PERI"), corpses("Lapis", "Lapis"), 1), 1e-6);
	}

	@Test
	void keyCorpseLosesItsPristineAsTheShaftRunsOut() {
		ShaftModel.Guide keyed = ShaftModel.run(NICK, ShaftModelTest.PRICES, 2_810_000.0, java.util.Map.of("Tungsten", 3_213_703.1),
				GameData.defaults());
		List<Corpse> corpses = corpses("Lapis", "Lapis", "Tungsten");
		double start = 681_990.0 + 2_989_000 - 3_213_703.1;
		assertEquals(start, Verdict.key(keyed, NICK, id("PERI"), corpses, "Tungsten", 1), 0.5);
		assertEquals(start - 681_990.0 / 2, Verdict.key(keyed, NICK, id("PERI"), corpses, "Tungsten", 0.5), 0.5);
		// 755 s freeze, 72 s setup: halfway = 413.5 s
		assertEquals(1, Verdict.left(keyed, NICK, 30_000), 1e-9, "still setting up");
		assertEquals(0.5, Verdict.left(keyed, NICK, 413_500), 1e-9);
		assertEquals(0, Verdict.left(keyed, NICK, 900_000), 1e-9);
		// campfire minutes still ahead (683 s mining w/o one)
		double perCold = ColdModel.exposure(755) / 100;
		double campfire = ColdModel.secondsUntil(150, perCold) - 755;
		assertEquals((683 + campfire - 828) / 683, Verdict.left(keyed, NICK, 900_000, 1), 1e-9, "past the old freeze, still mining");
		assertEquals(Verdict.left(keyed, NICK, 413_500), Verdict.left(keyed, NICK, 413_500, 0), 1e-12);
		// a placed campfires minutes count, so more than at the start (review 1.1)
		double withFire = Verdict.left(keyed, NICK, 180_000, 1);
		assertEquals(start - 681_990.0 * (1 - withFire), Verdict.key(keyed, NICK, id("PERI"), corpses, "Tungsten", withFire), 0.5);
		assertEquals(true, withFire > 1);
	}

	// review 1.3
	@Test
	void noCampfirePriceIsNoCampfireGain() {
		ShaftModel.Guide unpriced = ShaftModel.run(NICK, ShaftModelTest.PRICES, null, GameData.defaults());
		assertNull(Verdict.campfire(unpriced, NICK, id("PERI"), corpses("Lapis", "Lapis"), 0));
		assertNull(unpriced.row("PERI").lapis().get(2).campfire());
		assertNull(unpriced.row("PERI").campfireFrom(-1e12));
		assertEquals(GUIDE.row("PERI").lapis().get(2).gain(), unpriced.row("PERI").lapis().get(2).gain(), 1e-6);
	}

	// review 1.2: own ticks on any gem, the rest scale from them
	@Test
	void ownTicksOnAnotherGemAreEnough() {
		EnumMap<NumberId, Num> nums = new EnumMap<>(NICK.numbers());
		nums.put(NumberId.TICKS_4000, new Num(11, Source.CALCULATED, null, 0, null));
		nums.put(NumberId.TICKS_3000, new Num(8, Source.TRACKED, 8.0, 5, "shafts"));
		YourNumbers jadeOnly = new YourNumbers(nums, "JADE");
		assertEquals(true, jadeOnly.ownPace());
		nums.put(NumberId.TICKS_3000, new Num(8, Source.CALCULATED, null, 0, null));
		assertEquals(false, new YourNumbers(nums, "JADE").ownPace());
	}

	@Test
	void theLayoutYouAreInDecides() {
		YourNumbers.LayoutNum slow = new YourNumbers.LayoutNum(new Num(0.55, Source.SET, null, 0, null), 0.5, 0, 0);
		YourNumbers.LayoutNum fast = new YourNumbers.LayoutNum(new Num(0.75, Source.SET, null, 0, null), 0.5, 0, 0);
		YourNumbers yours = NICK.withLayouts(java.util.Map.of("PERI_1", slow, "PERI_2", fast));
		ShaftModel.Guide guide = ShaftModel.run(yours, ShaftModelTest.PRICES, 2_810_000.0, GameData.defaults());
		List<Corpse> corpses = corpses("Lapis", "Tungsten");
		double one = Verdict.of(guide, yours, new ShaftId("PERI_1", "PERI", "1"), corpses).gain();
		double two = Verdict.of(guide, yours, new ShaftId("PERI_2", "PERI", "2"), corpses).gain();
		assertEquals(guide.row("PERI", "1").lapis().get(1).gain(), one, 1e-6);
		assertEquals(guide.row("PERI", "2").lapis().get(1).gain(), two, 1e-6);
		// gems scale w/ efficiency, other drops dont
		assertEquals(0.75 / 0.55, guide.row("PERI", "2").lapis().get(1).gems() / guide.row("PERI", "1").lapis().get(1).gems(), 1e-9);
		// the crystal has no number here: the gems mix
		assertEquals(guide.row("PERI").lapis().get(1).gain(), Verdict.of(guide, yours, new ShaftId("PERI_C", "PERI", "C"), corpses).gain(), 1e-6);
		assertEquals((one + two) / 2, guide.row("PERI").lapis().get(1).gain(), 1e-6);
	}

	@Test
	void nothingToSay() {
		assertNull(Verdict.of(GUIDE, NICK, id("TUNG"), corpses("Lapis")), "not a gem shaft");
		assertNull(Verdict.of(GUIDE, NICK, id("PERI"), List.of()), "corpses not known yet");
		assertNull(Verdict.of(null, NICK, id("PERI"), corpses("Lapis")), "no prices");
		assertNull(Verdict.of(GUIDE, NICK, null, corpses("Lapis")), "shaft id not read yet");
		EnumMap<NumberId, Num> nums = new EnumMap<>(NICK.numbers());
		nums.put(NumberId.EFFICIENCY, new Num(0.65, Source.START, null, 0, null));
		assertNull(Verdict.of(GUIDE, new YourNumbers(nums, "PERI"), id("PERI"), corpses("Lapis")), "pace not tracked yet");
	}
}
