package dev.skysurveyor.calc;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.YourNumbers.Num;
import dev.skysurveyor.calc.YourNumbers.Source;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// vs guide_model.py (11-25, green loop + layouts 11-26), same model in python
class ShaftModelTest {
	private static final GameData DATA = GameData.defaults();
	// guide_model.py PRICES
	static final Map<String, Double> PRICES = Map.ofEntries(Map.entry("JASP", 3_180_000 / 80.0), Map.entry("ONYX", 38_400.0),
			Map.entry("PERI", 38_400.0), Map.entry("CITR", 38_400.0), Map.entry("AQUA", 38_400.0), Map.entry("TOPA", 32_000.0),
			Map.entry("AMBE", 2_220_000 / 80.0), Map.entry("SAPP", 2_150_000 / 80.0), Map.entry("AMET", 2_120_000 / 80.0),
			Map.entry("JADE", 25_600.0), Map.entry("OPAL", 25_600.0), Map.entry("RUBY", 1_700_000 / 80.0));
	private static final double CAMPFIRE = 2_810_000;

	// guide_model.py NICK
	static Map<NumberId, Double> nick() {
		Map<NumberId, Double> v = new EnumMap<>(NumberId.class);
		v.put(NumberId.FREEZE, 755.0);
		v.put(NumberId.SETUP, 72.0);
		v.put(NumberId.EFFICIENCY, 0.65);
		v.put(NumberId.BOOST, 0.22);
		v.put(NumberId.SPREAD, 0.17);
		v.put(NumberId.GEMS_PER_BLOCK, 110.0);
		v.put(NumberId.FLAWED, 0.15);
		v.put(NumberId.LOST_TICKS, 2.0);
		v.put(NumberId.TICKS_4000, 11.0);
		v.put(NumberId.TICKS_3500, 10.0);
		v.put(NumberId.TICKS_3000, 8.0);
		v.put(NumberId.TICKS_2500, 7.0);
		v.put(NumberId.BOOST_TICKS, 4.0);
		v.put(NumberId.EXTRA_CORPSE, 0.32);
		v.put(NumberId.LAPIS_SHARE, 0.59);
		v.put(NumberId.SPAWN_GEM, 0.19);
		v.put(NumberId.OTHER, 72_100.0);
		v.put(NumberId.COINS_PER_SPAWN, 1_400_000.0);
		v.put(NumberId.LAPIS_LOOT, 174_400.0);
		v.put(NumberId.SPAWN, 106.0);
		v.put(NumberId.NOT_MINING, 27.0);
		// same as a skip = model before the way back, guide_model.py w/o "back"
		v.put(NumberId.BACK_MINED, 27.0);
		v.put(NumberId.CLOSE_CALL, 1_000_000.0);
		v.put(NumberId.KEY_CLOSE_CALL, 300_000.0);
		// research/16: wiki table at his prices, 5.80 rolls
		v.put(NumberId.KEY_LOOT, 2_989_000.0);
		v.put(NumberId.EXTRA_ROLL, 0.302);
		return v;
	}

	static YourNumbers numbers(Map<NumberId, Double> values, String spawnGem) {
		Map<NumberId, Num> nums = new EnumMap<>(NumberId.class);
		values.forEach((id, value) -> nums.put(id, new Num(value, Source.TRACKED, value, id == NumberId.TICKS_4000 ? 7 : 1, "shafts")));
		for (NumberId id : new NumberId[] {NumberId.TICKS_3500, NumberId.TICKS_3000, NumberId.TICKS_2500}) {
			nums.put(id, new Num(values.get(id), Source.CALCULATED, null, 0, null));
		}
		double freeze = values.get(NumberId.FREEZE);
		double perCold = ColdModel.exposure(freeze) / 100;
		nums.put(NumberId.CAMPFIRE, new Num(ColdModel.secondsUntil(150, perCold) - freeze, Source.CALCULATED, null, 0, null));
		return new YourNumbers(nums, spawnGem);
	}

	private static void gains(ShaftModel.Guide guide, String code, double[] gain, double[] campfire) {
		ShaftModel.Row row = guide.row(code);
		for (int k = 0; k <= 4; k++) {
			assertEquals(gain[k], row.lapis().get(k).gain(), 0.5, code + " " + k + " lapis");
			assertEquals(campfire[k], row.lapis().get(k).campfire(), 0.5, code + " campfire " + k + " lapis");
		}
	}

	// guide_model.py "nick, 82 s back" (11-26): mined = inside + 82 s, not 27
	@Test
	void theWayBackAfterAMinedShaftCounts() {
		Map<NumberId, Double> v = nick();
		v.put(NumberId.BACK_MINED, 82.0);
		ShaftModel.Guide guide = ShaftModel.run(numbers(v, "PERI"), PRICES, CAMPFIRE, DATA);
		assertEquals(755 + 82 - 27, guide.away(), 1e-9);
		assertEquals(54_933_685.8, guide.perHour(guide.leaving()), 1);
		assertEquals(49_175_333.8, guide.perHour(guide.jasperOnly()), 1);
		// green only = the headline + the share since alpha.14, yellow apart
		assertEquals(54_337_715.2, guide.perHour(guide.greenOnly()), 1);
		assertEquals(0.221597, guide.minedShare(), 1e-6);
		assertEquals(0.148908, guide.closeShare(), 1e-6);
		gains(guide, "JASP", new double[] {30301.0, 736267.3, 1442233.5, 2148199.8, 2854166.0},
				new double[] {-2143959.5, -1894080.5, -1644201.6, -1394322.6, -1144443.6});
		gains(guide, "PERI", new double[] {-359691.8, 322298.2, 1004288.3, 1686278.3, 2368268.3},
				new double[] {-2281998.7, -2040606.2, -1799213.7, -1557821.1, -1316428.6});
		gains(guide, "JADE", new double[] {-2680323.4, -2141002.6, -1601681.7, -1062360.9, -523040.0},
				new double[] {-3103393.6, -2912499.2, -2721604.9, -2530710.6, -2339816.3});
	}

	@Test
	void nicksNumbers() {
		ShaftModel.Guide guide = ShaftModel.run(numbers(nick(), "PERI"), PRICES, CAMPFIRE, DATA);
		assertEquals(57_652_526.8, guide.perHour(guide.leaving()), 1);
		assertEquals(49_975_012.9, guide.perHour(guide.jasperOnly()), 1);
		assertEquals(56_787_363.3, guide.perHour(guide.greenOnly()), 1);
		assertEquals(0.238774, guide.minedShare(), 1e-6);
		assertEquals(0.140640, guide.closeShare(), 1e-6);
		// peridot = his spawn gem, 19%. others share the rest
		assertEquals(0.19, guide.row("PERI").chance(), 1e-9);
		assertEquals(0.81 / 16, guide.row("JASP").chance(), 1e-9);
		assertEquals(241.75, guide.campfire(), 1e-4);
		assertArrayEquals(new double[] {0.096136, 0.339285, 0.38861, 0.156581, 0.019388}, guide.lapisChance(), 1e-6);
		gains(guide, "JASP", new double[] {299364.3, 1005330.5, 1711296.8, 2417263.0, 3123229.3},
				new double[] {-2326537.2, -2076658.3, -1826779.3, -1576900.3, -1327021.3});
		gains(guide, "PERI", new double[] {-90628.5, 591361.5, 1273351.5, 1955341.6, 2637331.6},
				new double[] {-2464576.4, -2223183.9, -1981791.4, -1740398.9, -1499006.3});
		gains(guide, "TOPA", new double[] {-1461331.3, -863610.1, -265889.0, 331832.2, 929553.3},
				new double[] {-2949741.0, -2738175.7, -2526610.4, -2315045.1, -2103479.8});
		gains(guide, "JADE", new double[] {-2411260.2, -1871939.3, -1332618.4, -793297.6, -253976.7},
				new double[] {-3285971.3, -3095077.0, -2904182.7, -2713288.3, -2522394.0});
		gains(guide, "RUBY", new double[] {-3351526.4, -2870011.8, -2388497.2, -1906982.6, -1425467.9},
				new double[] {-3618781.5, -3448347.9, -3277914.2, -3107480.6, -2937047.0});
		// key corpse: pristine whole shaft + loot - key at insta-buy (research/16)
		ShaftModel.Guide keyed = ShaftModel.run(numbers(nick(), "PERI"), PRICES, CAMPFIRE,
				Map.of("Tungsten", 3_213_703.1, "Umber", 3_705_229.4), DATA);
		assertEquals(681_990.0, keyed.row("PERI").lapis().get(2).pristine(), 0.5);
		assertEquals(705_966.3, keyed.row("JASP").lapis().get(0).pristine(), 0.5);
		assertEquals(681_990.0 + 2_989_000 - 3_213_703.1, keyed.keyGain(keyed.row("PERI"), 2, "Tungsten"), 0.5);
		assertEquals(681_990.0 + 2_989_000 - 3_705_229.4, keyed.keyGain(keyed.row("PERI"), 2, "Umber"), 0.5);
		assertNull(guide.keyGain(guide.row("PERI"), 2, "Umber"), "no key price");
		// each corpse adds the same flawed share, so same gain at every count
		for (int k = 0; k <= 4; k++) assertEquals(keyed.keyGain(keyed.row("PERI"), 2, "Tungsten"), keyed.keyGain(keyed.row("PERI"), k, "Tungsten"), 1e-6);
		// tungsten +457k green on the 300k key band (yellow on 1M), umber -34k red
		assertEquals(300_000, keyed.keyBand());
		assertEquals("§a", GuideText.colour(keyed.keyGain(keyed.row("PERI"), 2, "Tungsten"), keyed.keyBand()));
		assertEquals("§c", GuideText.colour(keyed.keyGain(keyed.row("PERI"), 2, "Umber"), keyed.keyBand()));
		assertEquals("§e", GuideText.colour(keyed.keyGain(keyed.row("PERI"), 2, "Tungsten"), keyed.band()));
		// red < 0, yellow < band, green from band
		assertEquals("§c", GuideText.colour(-1, 1_000_000));
		assertEquals("§e", GuideText.colour(0, 1_000_000));
		assertEquals("§e", GuideText.colour(999_999, 1_000_000));
		assertEquals("§a", GuideText.colour(1_000_000, 1_000_000));
		// plain words
		assertEquals("2 Lapis", GuideText.lapis(2));
		assertEquals("Always", GuideText.from(0));
		assertEquals("3 Lapis", GuideText.from(3));
		assertEquals("4 Lapis", GuideText.from(4));
		assertEquals("§7Never", GuideText.campfire(guide.row("JASP"), guide.band()));
		ShaftModel.Guide freeFire = ShaftModel.run(numbers(nick(), "PERI"), PRICES, 0.0, DATA);
		// campfire = 1st green count too (nick 11-26). free one: +483k at 0 is yellow, over 1M from 3 (+1.23M)
		assertEquals("§a3 Lapis", GuideText.campfire(freeFire.row("JASP"), freeFire.band()));
		assertEquals("§aAlways", GuideText.campfire(freeFire.row("JASP"), 400_000));
		// mine from = 1st green cell (nick 11-26). peridot +591k at 1 lapis is yellow, +1.27M at 2
		assertEquals("§a2 Lapis", GuideText.mineFrom(guide.row("PERI")));
		assertEquals("§a1 Lapis", GuideText.mineFrom(guide.row("JASP")));
		assertEquals("§cSkip", GuideText.mineFrom(guide.row("JADE")));
		Map<NumberId, Double> lowBand = nick();
		lowBand.put(NumberId.CLOSE_CALL, 250_000.0);
		assertEquals("§aAlways", GuideText.mineFrom(ShaftModel.run(numbers(lowBand, "PERI"), PRICES, CAMPFIRE, DATA).row("JASP")));
		// key corpse in a skipped shaft: loot - key, no pristine
		assertEquals(2_989_000 - 3_213_703.1, keyed.keyGainSkipped("Tungsten"), 0.5);
		assertNull(guide.keyGainSkipped("Tungsten"), "no key price");
		// ruby w/ a free campfire still loses (-127k at 4)
		assertEquals("§7Never", GuideText.campfire(freeFire.row("RUBY"), freeFire.band()));
		// none over 2M: never green
		assertEquals("§7Never", GuideText.campfire(freeFire.row("JASP"), 2_000_000));
		// campfire at 2.81M never pays for him. free: jasper +483k at 0 lapis,
		// over 1M from 3 (+1.23M), ruby still -127k at 4
		assertNull(guide.row("JASP").campfireFrom(guide.band()));
		ShaftModel.Guide free = ShaftModel.run(numbers(nick(), "PERI"), PRICES, 0.0, DATA);
		assertEquals(3, free.row("JASP").campfireFrom(free.band()));
		assertEquals(0, free.row("JASP").campfireFrom(0));
		assertNull(free.row("RUBY").campfireFrom(0));
		// green from 1st count >= close call band
		// 0 + 0 typed: no div by 0 (review 1.4)
		Map<NumberId, Double> noLoop = nick();
		noLoop.put(NumberId.SPAWN, 0.0);
		noLoop.put(NumberId.NOT_MINING, 0.0);
		assertEquals(true, Double.isFinite(ShaftModel.run(numbers(noLoop, "PERI"), PRICES, CAMPFIRE, DATA).leaving()));
		assertEquals(1, guide.row("JASP").mineFrom());
		assertEquals(2, guide.row("PERI").mineFrom());
		assertNull(guide.row("JADE").mineFrom());
	}

	@Test
	void noSpawnGemIsEvenOdds() {
		ShaftModel.Guide guide = ShaftModel.run(numbers(nick(), null), PRICES, CAMPFIRE, DATA);
		assertEquals(56_673_054.8, guide.perHour(guide.leaving()), 1);
		assertEquals(50_488_091.6, guide.perHour(guide.jasperOnly()), 1);
		assertEquals(55_641_870.0, guide.perHour(guide.greenOnly()), 1);
		assertEquals(0.187151, guide.minedShare(), 1e-6);
		assertEquals(0.129950, guide.closeShare(), 1e-6);
		gains(guide, "TOPA", new double[] {-1255914.3, -658193.1, -60471.9, 537249.2, 1134970.4},
				new double[] {-2883966.7, -2672401.4, -2460836.1, -2249270.9, -2037705.6});
	}

	@Test
	void slowPlayer() {
		Map<NumberId, Double> v = nick();
		v.put(NumberId.EFFICIENCY, 0.5);
		v.put(NumberId.TICKS_4000, 14.0);
		v.put(NumberId.TICKS_3500, 13.0);
		v.put(NumberId.TICKS_3000, 11.0);
		v.put(NumberId.TICKS_2500, 9.0);
		v.put(NumberId.BOOST_TICKS, 5.0);
		v.put(NumberId.SPAWN, 140.0);
		v.put(NumberId.COINS_PER_SPAWN, 400_000.0);
		v.put(NumberId.GEMS_PER_BLOCK, 80.0);
		v.put(NumberId.FREEZE, 600.0);
		v.put(NumberId.LOST_TICKS, 1.0);
		v.put(NumberId.EXTRA_CORPSE, 0.25);
		v.put(NumberId.LAPIS_SHARE, 0.5);
		ShaftModel.Guide guide = ShaftModel.run(numbers(v, null), PRICES, CAMPFIRE, DATA);
		assertEquals(23_244_823.4, guide.perHour(guide.leaving()), 1);
		assertEquals(16_605_748.9, guide.perHour(guide.jasperOnly()), 1);
		// big gap: most of his cells are yellow
		assertEquals(19_727_050.6, guide.perHour(guide.greenOnly()), 1);
		assertEquals(0.156710, guide.minedShare(), 1e-6);
		assertEquals(0.496324, guide.closeShare(), 1e-6);
		assertEquals(200.0, guide.campfire(), 1e-4);
		gains(guide, "JADE", new double[] {-39296.6, 152138.0, 343572.6, 535007.1, 726441.7},
				new double[] {-2681560.6, -2609047.5, -2536534.5, -2464021.4, -2391508.3});
		gains(guide, "RUBY", new double[] {-260135.5, -82277.8, 95579.9, 273437.6, 451295.2},
				new double[] {-2765211.8, -2697841.4, -2630471.1, -2563100.7, -2495730.4});
	}

	private static YourNumbers.LayoutNum layout(double efficiency, double weight) {
		return new YourNumbers.LayoutNum(new Num(efficiency, Source.TRACKED, efficiency, 3, "shafts"), weight, 0, 0);
	}

	// guide_model.py "nick w/ layouts": peridot on 3 layouts, jade on its 1, rest on the one efficiency
	@Test
	void aGemTakesItsLayoutsByHowOftenTheyCome() {
		Map<NumberId, Double> v = nick();
		v.put(NumberId.BACK_MINED, 82.0);
		YourNumbers yours = numbers(v, "PERI").withLayouts(Map.of("PERI_1", layout(0.61, 0.5), "PERI_2", layout(0.66, 0.25),
				"PERI_C", layout(0.70, 0.25), "JADE_1", layout(0.70, 1)));
		ShaftModel.Guide guide = ShaftModel.run(yours, PRICES, CAMPFIRE, DATA);
		// best loop picks per layout since alpha.20 (peridot crystal yes, peridot 1 no)
		assertEquals(54_928_998.8, guide.perHour(guide.leaving()), 1);
		assertEquals(53_091_795.4, guide.perHour(guide.greenOnly()), 1);
		assertEquals(0.147761, guide.minedShare(), 1e-6);
		assertEquals(0.223725, guide.closeShare(), 1e-6);
		double[][] expected = {{31355.6, 737321.8, 1443288.1, 2149254.3, 2855220.6}, {-443969.0, 232775.0, 909518.9, 1586262.9, 2263006.9},
				{-1805477.8, -1212437.5, -619397.2, -26356.9, 566683.4}};
		String[] gems = {"JASP", "PERI", "JADE"};
		for (int i = 0; i < gems.length; i++) {
			for (int k = 0; k <= 4; k++) assertEquals(expected[i][k], guide.row(gems[i]).lapis().get(k).gain(), 0.5, gems[i] + " " + k);
		}
		// the shaft u are in: its own layout, vs the same leaving
		double[][] own = {{-1041291.3, -401269.9, 238751.5, 878772.9, 1518794.3}, {-187973.7, 504508.5, 1196990.7, 1889472.9, 2581955.1},
				{494680.4, 1229131.2, 1963582.0, 2698032.8, 3432483.6}};
		String[] variants = {"1", "2", "C"};
		for (int i = 0; i < variants.length; i++) {
			for (int k = 0; k <= 4; k++) assertEquals(own[i][k], guide.row("PERI", variants[i]).lapis().get(k).gain(), 0.5, variants[i] + " " + k);
		}
		// 1 layout = the gems row. 70% at 8 ticks, not at jaspers 11
		assertEquals(guide.row("JADE").lapis().get(3).gain(), guide.row("JADE", "1").lapis().get(3).gain(), 1e-6);
		assertEquals(0.19 * 0.25, guide.row("PERI", "C").chance(), 1e-9);
		// no number for the layout = its gem
		assertEquals(guide.row("JASP"), guide.row("JASP", "1"));
		assertEquals(guide.row("PERI"), guide.row("PERI", null));
		// the crystal is green from 1l (+1.23M), type 1 only from 4l (+879k at 3)
		assertEquals(1, guide.row("PERI", "C").mineFrom());
		assertEquals(4, guide.row("PERI", "1").mineFrom());
	}

	private static YourNumbers.LayoutNum layout(double efficiency, double weight, double route) {
		return new YourNumbers.LayoutNum(new Num(efficiency, Source.TRACKED, efficiency, 3, "shafts"), new Num(route, Source.CALCULATED, null, 0, null),
				weight, 0, 0);
	}

	// guide_model.py "routes": u leave when the route is done
	@Test
	void aShortRouteEndsTheShaftEarly() {
		Map<NumberId, Double> v = nick();
		v.put(NumberId.BACK_MINED, 82.0);
		YourNumbers yours = numbers(v, "PERI").withLayouts(Map.of("CITR_1", layout(0.65, 0.5, 784), "CITR_2", layout(0.65, 0.5, 556),
				"PERI_2", layout(0.66, 1, 1394), "JADE_1", layout(0.64, 1, 1100)));
		ShaftModel.Guide guide = ShaftModel.run(yours, PRICES, CAMPFIRE, DATA);
		assertEquals(54_900_687.2, guide.perHour(guide.leaving()), 1);
		assertEquals(54_109_599.5, guide.perHour(guide.greenOnly()), 1);
		assertEquals(0.193016, guide.minedShare(), 1e-6);
		assertEquals(0.140640, guide.closeShare(), 1e-6);
		double[][] expected = {{-952491.2, -543765.7, -135040.3, 273685.2, 682410.7}, {-181603.6, 510878.6, 1203360.8, 1895843.0, 2588325.2},
				{-2490245.1, -2042883.9, -1595522.7, -1148161.5, -700800.3}};
		String[] gems = {"CITR", "PERI", "JADE"};
		for (int i = 0; i < gems.length; i++) {
			for (int k = 0; k <= 4; k++) assertEquals(expected[i][k], guide.row(gems[i]).lapis().get(k).gain(), 0.5, gems[i] + " " + k);
		}
		// citrine 1: 784 blocks are done after 9:11, he took 9:45 and 8:21 (research/17 s9)
		assertEquals(550.978, guide.row("CITR", "1").lapis().getFirst().seconds(), 0.001);
		assertEquals(411.683, guide.row("CITR", "2").lapis().getFirst().seconds(), 0.001);
		assertEquals(156802.1, guide.row("CITR", "1").lapis().get(2).gain(), 0.5);
		assertEquals(-87701.5, guide.row("CITR", "2").lapis().get(3).gain(), 0.5);
		// a route he never finishes changes nothing
		assertEquals(755, guide.row("PERI", "2").lapis().getFirst().seconds(), 1e-9);
		// no campfire on a route thats done b4 the freeze: only its cost
		assertEquals(-CAMPFIRE, guide.row("CITR").lapis().get(4).campfire(), 1e-6);
		assertEquals(-1797844.7, guide.row("PERI").lapis().get(4).campfire(), 0.5);
	}

	@Test
	void gemLayouts() {
		assertEquals(784, DATA.layout("CITR_1").route());
		// crystals: no simulated count, no limit till his own
		assertEquals(0, DATA.layout("PERI_C").route());
		assertEquals("PERI", DATA.layout("PERI_2").gem());
		assertEquals("2", DATA.layout("PERI_2").variant());
		assertEquals(24, DATA.layouts().size());
		assertEquals(3, DATA.layoutsOf("PERI").size());
		assertNull(DATA.layout("TUNG_1"));
		assertNull(DATA.layout(null));
		for (GameData.Gem gem : DATA.gems()) assertEquals(false, DATA.layoutsOf(gem.code()).isEmpty(), gem.code());
	}

	@Test
	void aGemWithoutAPriceIsLeftOut() {
		Map<String, Double> prices = new HashMap<>(PRICES);
		prices.remove("RUBY");
		ShaftModel.Guide guide = ShaftModel.run(numbers(nick(), "PERI"), prices, CAMPFIRE, DATA);
		assertNull(guide.row("RUBY").perFine());
		assertNull(guide.row("RUBY").mineFrom());
		// ruby never worth mining lol, leaving unchanged
		assertEquals(57_652_526.8, guide.perHour(guide.leaving()), 1);
	}
}
