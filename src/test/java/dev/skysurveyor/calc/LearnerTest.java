package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.Fiesta.State;
import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.calc.YourNumbers.StateNum;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.EntryBuilder;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.mining.MiningStats;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Line;
import dev.skysurveyor.money.ShaftValue.Part;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

// made up, worked out by hand
class LearnerTest {
	private static final GameData DATA = GameData.defaults();

	private static YourNumbers learn(List<SessionSummary> sessions, Map<String, Double> typed) {
		return Learner.learn(new Learner.Input(sessions, e -> null, e -> 100_000.0, typed, 0, TextPatterns.defaults()), DATA);
	}

	// skips 10 s inside, spawn every 100 s + 20 s
	private static List<JournalEntry> skips(long session, int count, long spawnMillis) {
		List<JournalEntry> out = new ArrayList<>();
		long at = session;
		for (int i = 0; i < count; i++) {
			at += spawnMillis + 20_000;
			out.add(EntryBuilder.entry().sessionId(session).enteredAt(at).durationMillis(10_000).searchMillis(spawnMillis)
					.shaftId(i % 3 == 0 ? "PERI_1" : "JADE_1").corpses(List.of(new Corpse("Lapis", true), new Corpse("Umber", false)))
					.extraCorpses(i % 4 == 0 ? 1 : 0).build());
		}
		return out;
	}

	@Test
	void nothingTrackedGivesTheStartingValues() {
		YourNumbers numbers = learn(List.of(), Map.of());
		assertEquals(Source.START, numbers.num(NumberId.SPAWN).source());
		assertEquals(100, numbers.get(NumberId.SPAWN));
		// speed 10,000: jasper 12, topaz 10.5 -> 11, bp7 9, ruby 7.5 -> 8
		assertEquals(12, numbers.get(NumberId.TICKS_4000));
		assertEquals(11, numbers.get(NumberId.TICKS_3500));
		assertEquals(9, numbers.get(NumberId.TICKS_3000));
		assertEquals(8, numbers.get(NumberId.TICKS_2500));
		assertNull(numbers.spawnGem());
		// 760 s freeze, 1 campfire = 150 cold: 1001.1 s
		assertEquals(Source.CALCULATED, numbers.num(NumberId.CAMPFIRE).source());
		double perCold = ColdModel.exposure(760) / 100;
		assertEquals(ColdModel.secondsUntil(150, perCold) - 760, numbers.get(NumberId.CAMPFIRE), 1e-9);
	}

	@Test
	void spawnIsTheMedianOfSessionMedians() {
		List<SessionSummary> sessions = List.of(new SessionSummary(2_000_000_000L, skips(2_000_000_000L, 6, 90_000)),
				new SessionSummary(1_000_000_000L, skips(1_000_000_000L, 9, 120_000)),
				// 3 spawns, too few alone
				new SessionSummary(500_000_000L, skips(500_000_000L, 3, 30_000)));
		YourNumbers numbers = learn(sessions, Map.of());
		assertEquals(105, numbers.get(NumberId.SPAWN), 1e-9);
		assertEquals(15, numbers.num(NumberId.SPAWN).basis());
		// cycle = spawn + 20 s, a skip is 10 s of it
		assertEquals(20, numbers.get(NumberId.NOT_MINING), 1e-9);
		// 100k a mining entry, none saved here
		assertEquals(Source.START, numbers.num(NumberId.COINS_PER_SPAWN).source());
	}

	@Test
	void corpseRulesAndSpawnGem() {
		long session = 1_000_000_000L;
		List<JournalEntry> entries = skips(session, 12, 100_000);
		List<MiningEntry> mining = List.of(new MiningEntry(1, session, session + 1, Map.of("Rough Peridot Gemstone", 640_000L), false, Map.of()),
				new MiningEntry(1, session, session + 2, Map.of("Flawed Jade Gemstone", 1_000L), false, Map.of()));
		YourNumbers numbers = learn(List.of(new SessionSummary(session, entries, mining, List.of())), Map.of());
		assertEquals("PERI", numbers.spawnGem(), "100 fine of peridot beats 12.5 of jade");
		assertEquals(4.0 / 12, numbers.get(NumberId.SPAWN_GEM), 1e-9);
		assertEquals(3.0 / 12, numbers.get(NumberId.EXTRA_CORPSE), 1e-9);
		assertEquals(0.5, numbers.get(NumberId.LAPIS_SHARE), 1e-9);
		// 2 mining entries at 100k for 12 spawns
		assertEquals(200_000.0 / 12, numbers.get(NumberId.COINS_PER_SPAWN), 1e-9);
	}

	// mined jasper: 1000 own + 100 spread at 11 ticks, 4 in boost
	private static JournalEntry mined(long at, int looted, double flawedShare) {
		return mined(at, looted, flawedShare, "JASP_1");
	}

	private static JournalEntry mined(long at, int looted, double flawedShare, String shaftId) {
		// 1,100,000 base drops every shaft, a share flawed
		long flawed = Math.round(1_100_000 * flawedShare);
		long rough = 1_100_000 - flawed;
		List<Corpse> corpses = new ArrayList<>();
		for (int i = 0; i < 3; i++) corpses.add(new Corpse("Lapis", i < looted));
		return EntryBuilder.entry().sessionId(1).enteredAt(at).shaftId(shaftId).durationMillis(760_000).corpses(corpses)
				.gems(new GemYield("Jasper", rough, flawed, 0, 0, 0))
				.mining(new MiningStats(700_000, 60_000, 1_000, 100, "magenta_stained_glass", 11.0, 4.0, 140_000, 0, 11.0)).build();
	}

	private static JournalEntry leftEarly(long at, int blocks, int cold, String exit) {
		return EntryBuilder.entry().sessionId(1).enteredAt(at).shaftId("JASP_1").durationMillis(600_000).exit(exit).coldAtExit(cold)
				.gems(new GemYield("Jasper", 900_000, 150_000, 0, 0, 0))
				.mining(new MiningStats(540_000, 60_000, blocks, 100, "magenta_stained_glass", 11.0, 4.0, 108_000, 0, 11.0)).build();
	}

	@Test
	void minedShaftsGiveThePace() {
		List<JournalEntry> entries = new ArrayList<>();
		// flawed 15% + 1 point per looted corpse
		for (int i = 0; i < 8; i++) entries.add(mined(i * 1_000_000L, i % 4, 0.15 + 0.01 * (i % 4)));
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of());
		assertEquals(Source.TRACKED, numbers.num(NumberId.TICKS_4000).source());
		assertEquals(11, numbers.get(NumberId.TICKS_4000));
		// speed 4000 x 30 / 11 = 10,909: topaz 9.6 -> 10, bp7 8.25 -> 8, ruby 6.9 -> 7
		assertEquals(Source.CALCULATED, numbers.num(NumberId.TICKS_3500).source());
		assertEquals(10, numbers.get(NumberId.TICKS_3500));
		assertEquals(8, numbers.get(NumberId.TICKS_3000));
		assertEquals(7, numbers.get(NumberId.TICKS_2500));
		assertEquals(4, numbers.get(NumberId.BOOST_TICKS));
		assertEquals(0.2, numbers.get(NumberId.BOOST), 1e-9);
		assertEquals(0.1, numbers.get(NumberId.SPREAD), 1e-9);
		assertEquals(60, numbers.get(NumberId.SETUP), 1e-9);
		assertEquals(0.15, numbers.get(NumberId.FLAWED), 1e-5);
		// rough + 80 x flawed = drops x (1 + 79 x share), so 1,100,000 / 1,100 whatever the share
		assertEquals(1_000, numbers.get(NumberId.GEMS_PER_BLOCK), 1e-6);
	}

	// 8 shafts on JASP_1 at 11 ticks
	@Test
	void efficiencyPerLayout() {
		List<JournalEntry> entries = new ArrayList<>();
		for (int i = 0; i < 8; i++) entries.add(mined(i * 1_000_000L, 0, 0.15));
		double shown = Efficiency.shown(entries.getFirst().mining());
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of("efficiency_PERI_2", 0.5, "efficiency_CITR_1", 7.0, "efficiency_CRYS_7", 0.4, "efficiency_PERI_C", 0.3));
		assertEquals(24, numbers.layouts().size());
		// enough shafts on it: its own
		YourNumbers.LayoutNum jasper = numbers.layouts().get("JASP_1");
		assertEquals(Source.TRACKED, jasper.efficiency().source());
		assertEquals(shown, jasper.efficiency().value(), 1e-9);
		// its newest 5
		assertEquals(Layouts.LAST_SHAFTS, jasper.efficiency().basis());
		// under 10 jasper shafts: both layouts count the same
		assertEquals(0.5, jasper.weight(), 1e-9);
		assertEquals(8, jasper.seen());
		assertEquals(1.0 / 3, numbers.layouts().get("PERI_1").weight(), 1e-9);
		// never mined: his level, same ticks
		YourNumbers.LayoutNum crystal = numbers.layouts().get("JASP_C");
		assertEquals(Source.CALCULATED, crystal.efficiency().source());
		assertEquals(shown, crystal.efficiency().value(), 1e-9);
		assertEquals(shown, numbers.layouts().get("PERI_1").efficiency().value(), 1e-9);
		// crystals: typed per bp group, the old key per crystal layout does nothing
		assertEquals(shown, numbers.layouts().get("PERI_C").efficiency().value(), 1e-9);
		assertEquals(Source.SET, numbers.layouts().get("OPAL_C").efficiency().source());
		assertEquals(0.4, numbers.layouts().get("OPAL_C").efficiency().value());
		assertEquals("CRYS_9", DATA.layout("PERI_C").group());
		assertEquals("CRYS_9", DATA.layout("JASP_C").group());
		assertEquals("CRYS_6", DATA.layout("RUBY_C").group());
		assertEquals("PERI_2", DATA.layout("PERI_2").group());
		// other ticks: through the share of the time mining
		double level = shown * ShaftModel.toShare(numbers, 4000, DATA);
		assertEquals(level / ShaftModel.toShare(numbers, 3000, DATA), numbers.layouts().get("JADE_1").efficiency().value(), 1e-9);
		// typed for one layout. 700% is no efficiency, skipped
		assertEquals(Source.SET, numbers.layouts().get("PERI_2").efficiency().source());
		assertEquals(0.5, numbers.layouts().get("PERI_2").efficiency().value());
		assertEquals(Source.CALCULATED, numbers.layouts().get("CITR_1").efficiency().source());

		// tracked from the 1st shaft on it (nick 11-26)
		YourNumbers one = learn(List.of(new SessionSummary(1, entries.subList(0, 1))), Map.of());
		assertEquals(Source.TRACKED, one.layouts().get("JASP_1").efficiency().source());
		assertEquals(shown, one.layouts().get("JASP_1").efficiency().value(), 1e-9);
		assertEquals(1, one.layouts().get("JASP_1").efficiency().basis());
		assertEquals(Source.CALCULATED, one.layouts().get("JASP_C").efficiency().source());

		// 1 crystal shaft tracks every crystal of that bp, not opals or rubys
		YourNumbers pooled = learn(List.of(new SessionSummary(1, List.of(mined(0, 0, 0.15, "JASP_C")))), Map.of());
		assertEquals(Source.TRACKED, pooled.layouts().get("PERI_C").efficiency().source());
		assertEquals(1, pooled.layouts().get("ONYX_C").efficiency().basis());
		assertEquals(Source.CALCULATED, pooled.layouts().get("OPAL_C").efficiency().source());
		assertEquals(Source.CALCULATED, pooled.layouts().get("JASP_1").efficiency().source());

		// route: the simulated count till 2 shafts left w/ cold to spare. these 8 ended at the cold
		assertEquals(Source.CALCULATED, jasper.route().source());
		assertEquals(1864, jasper.route().value());
		List<JournalEntry> early = new ArrayList<>(entries);
		early.add(leftEarly(9_000_000L, 700, -60, "BYE"));
		early.add(leftEarly(10_000_000L, 900, -95, "BYE"));
		early.add(leftEarly(11_000_000L, 950, -50, "DEATH"));
		YourNumbers routes = learn(List.of(new SessionSummary(1, early)), Map.of("route_CITR_1", 0.0, "route_ONYX_1", -5.0));
		assertEquals(Source.CALCULATED, routes.layouts().get("JASP_1").route().source());
		assertEquals(700, routes.layouts().get("JASP_1").route().tracked());
		early.add(leftEarly(12_000_000L, 800, -70, "WARP"));
		routes = learn(List.of(new SessionSummary(1, early)), Map.of("route_CITR_1", 0.0, "route_ONYX_1", -5.0));
		assertEquals(Source.TRACKED, routes.layouts().get("JASP_1").route().source());
		assertEquals(750, routes.layouts().get("JASP_1").route().value());
		// typed 0 = no limit, a bad one skipped
		assertEquals(Source.SET, routes.layouts().get("CITR_1").route().source());
		assertEquals(0, routes.layouts().get("CITR_1").route().value());
		assertEquals(1261, routes.layouts().get("ONYX_1").route().value());
		// crystals: no limit till 2 crystal shafts left early, then every crystal has it
		assertEquals(Source.START, routes.layouts().get("PERI_C").route().source());
		assertEquals(0, routes.layouts().get("PERI_C").route().value());
		List<JournalEntry> crystals = List.of(EntryBuilder.from(leftEarly(0, 1_018, -78, "BYE")).shaftId("JASP_C").build(),
				EntryBuilder.from(leftEarly(1_000_000L, 1_107, -89, "BYE")).shaftId("JASP_C").build());
		YourNumbers ownRoute = learn(List.of(new SessionSummary(1, crystals.subList(0, 1))), Map.of());
		assertEquals(Source.START, ownRoute.layouts().get("JASP_C").route().source());
		assertEquals(1_018, ownRoute.layouts().get("JASP_C").route().tracked());
		ownRoute = learn(List.of(new SessionSummary(1, crystals)), Map.of());
		assertEquals(Source.TRACKED, ownRoute.layouts().get("OPAL_C").route().source());
		assertEquals(1_062.5, ownRoute.layouts().get("OPAL_C").route().value());
		assertEquals("route_CRYS", Layouts.routeKey(DATA.layout("OPAL_C")));
		assertEquals(true, Layouts.isRouteKey("route_CRYS"));
		assertEquals(false, Layouts.isRouteKey("route_cole"));

		YourNumbers typed = learn(List.of(new SessionSummary(1, entries)), Map.of("efficiency", 0.7, "efficiency_PERI_2", 0.5));
		assertEquals(Source.CALCULATED, typed.layouts().get("JASP_1").efficiency().source());
		assertEquals(0.7, typed.layouts().get("JASP_1").efficiency().value(), 1e-9);
		assertEquals(shown, typed.layouts().get("JASP_1").efficiency().tracked(), 1e-9);
		assertEquals(0.5, typed.layouts().get("PERI_2").efficiency().value());

		// nothing mined: the starting value
		YourNumbers none = learn(List.of(new SessionSummary(1, skips(1, 12, 100_000))), Map.of());
		assertEquals(DATA.start("efficiency"), none.layouts().get("PERI_2").efficiency().value(), 1e-9);
		// 12 skips: 4 PERI_1, 8 JADE_1. peridot under 10 = even
		assertEquals(1.0 / 3, none.layouts().get("PERI_1").weight(), 1e-9);
		assertEquals(1.0, none.layouts().get("JADE_1").weight(), 1e-9);
		YourNumbers many = learn(List.of(new SessionSummary(1, skips(1, 30, 100_000))), Map.of());
		// 30 skips: 10 PERI_1, all his peridot shafts
		assertEquals(1.0, many.layouts().get("PERI_1").weight(), 1e-9);
		assertEquals(0.0, many.layouts().get("PERI_C").weight(), 1e-9);
		assertEquals(10, many.layouts().get("PERI_C").gemShafts());
	}

	private static YourNumbers learn(List<SessionSummary> sessions, Map<String, Double> typed, State state) {
		return Learner.learn(new Learner.Input(sessions, LearnerTest::value, e -> 100_000.0, typed, 0, TextPatterns.defaults(), state, 1_000_000.0),
				DATA);
	}

	// 760k scrap + glossy at 1M each
	// the list vs what the shafts made: gems + other drops, corpse loot out
	@Test
	void pastShaftsAgainstTheGuide() {
		List<JournalEntry> entries = new ArrayList<>();
		for (int i = 0; i < 3; i++) entries.add(mined(i * 1_000_000L, i, 0.15 + 0.01 * i));
		// skipped: not in
		entries.add(EntryBuilder.entry().sessionId(1).enteredAt(9_000_000L).shaftId("PERI_1").durationMillis(30_000).build());
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of());
		ShaftModel.Guide guide = ShaftModel.run(numbers, ShaftModelTest.PRICES, null, DATA);
		Function<JournalEntry, ShaftValue> made = e -> new ShaftValue(List.of(new Line(Part.MINED, "Jasper", 1, 10_000_000.0),
				new Line(Part.OTHER, "Coal", 1, 500_000.0), new Line(Part.CORPSES, "Loot", 1, 9_000_000.0)), List.of());
		PastShafts.Check check = PastShafts.check(entries, made, guide, null, DATA);
		assertEquals(3, check.lines().size());
		// newest first, its 2 looted corpses pick the 2l cell of its layout
		PastShafts.Line newest = check.lines().getFirst();
		assertEquals(2, newest.looted());
		assertEquals(guide.row("JASP", "1").lapis().get(2).coins(), newest.predicted(), 1e-6);
		assertEquals(10_500_000, newest.made(), 1e-6);
		assertEquals(10_500_000 / newest.predicted() - 1, newest.off(), 1e-9);
		assertEquals(check.lines().get(1).off(), check.medianOff(), 1e-9);
		// no prices = nothing to hold it against
		assertEquals(0, PastShafts.check(entries, made, null, null, DATA).lines().size());
	}

	private static ShaftValue value(JournalEntry entry) {
		long glossy = entry.otherDrops().getOrDefault(ShaftValue.GLOSSY, 0L);
		return new ShaftValue(List.of(new Line(Part.SCRAP, "Suspicious Scrap", 1, 760_000.0),
				new Line(Part.GLOSSY, ShaftValue.GLOSSY, glossy, glossy * 1_000_000.0)), List.of());
	}

	// like mined() w/ fiesta saved, drops over its 1,100 blocks
	private static JournalEntry fiesta(long at, long drops, Boolean on, Long flask, long glossy) {
		return EntryBuilder.from(mined(at, 0, 0)).gems(new GemYield("Jasper", drops, 0, 0, 0, 0)).fiesta(on, flask)
				.otherDrops(glossy == 0 ? Map.of() : Map.of(ShaftValue.GLOSSY, glossy)).build();
	}

	private static final long SHAFT = 760_000;

	@Test
	void eachFiestaStateFromItsOwnShafts() {
		List<JournalEntry> entries = List.of(fiesta(0, 1_100_000, false, 0L, 0), fiesta(1_000_000, 1_100_000, false, 0L, 0),
				fiesta(2_000_000, 1_650_000, true, SHAFT, 4));
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of(), State.FLASK);
		assertEquals(1_500, numbers.get(NumberId.GEMS_PER_BLOCK), 1e-6);
		assertEquals(Source.TRACKED, numbers.num(NumberId.GEMS_PER_BLOCK).source());
		assertEquals(1, numbers.num(NumberId.GEMS_PER_BLOCK).basis());
		Map<State, StateNum> gems = numbers.fiesta().of(NumberId.GEMS_PER_BLOCK);
		assertEquals(1_000, gems.get(State.NONE).num().value(), 1e-6);
		assertEquals(2, gems.get(State.NONE).num().basis());
		// no cole shaft: biggest state x 1.25
		assertEquals(1_250, gems.get(State.COLE).num().value(), 1e-6);
		assertEquals(Source.CALCULATED, gems.get(State.COLE).num().source());
		assertEquals(State.NONE, gems.get(State.COLE).from());
		// other drops a minute: scrap 760k + 4 glossy at 1M over 12:40
		assertEquals((760_000 + 4_000_000) / (760 / 60.0), numbers.get(NumberId.OTHER), 1e-6);
		Map<State, StateNum> other = numbers.fiesta().of(NumberId.OTHER);
		assertEquals(760_000 / (760 / 60.0), other.get(State.NONE).num().value(), 1e-6);
		// cole from no cole: scrap + 1,100 blocks x 9 / 5000 x 1M
		assertEquals((760_000 + 1_100 * 0.0018 * 1_000_000) / (760 / 60.0), other.get(State.COLE).num().value(), 1e-6);
		// cole in office: calculated one in use, still counts as ur pace
		YourNumbers cole = learn(List.of(new SessionSummary(1, entries)), Map.of(), State.COLE);
		assertEquals(1_250, cole.get(NumberId.GEMS_PER_BLOCK), 1e-6);
		assertEquals(true, cole.ownPace());
	}

	@Test
	void shaftsFromBeforeTheFiestaWasSavedAreAPool() {
		List<JournalEntry> old = List.of(fiesta(0, 1_100_000, null, null, 0), fiesta(1_000_000, 1_100_000, null, null, 0));
		YourNumbers numbers = learn(List.of(new SessionSummary(1, old)), Map.of(), State.COLE);
		StateNum gems = numbers.fiesta().of(NumberId.GEMS_PER_BLOCK).get(State.COLE);
		assertEquals(1_000, gems.num().value(), 1e-6, "as it is, nobody knows what state they were");
		assertEquals(Source.TRACKED, gems.num().source());
		assertEquals(true, gems.pool());
		// known shaft beats the pool, any state
		List<JournalEntry> both = new ArrayList<>(old);
		both.add(fiesta(2_000_000, 1_210_000, false, 0L, 0));
		numbers = learn(List.of(new SessionSummary(1, both)), Map.of(), State.COLE);
		assertEquals(1_100 * 1.25, numbers.get(NumberId.GEMS_PER_BLOCK), 1e-6);
		assertEquals(Source.CALCULATED, numbers.num(NumberId.GEMS_PER_BLOCK).source());
		// fiesta unknown: all shafts together
		numbers = learn(List.of(new SessionSummary(1, both)), Map.of(), null);
		assertEquals(1_000, numbers.get(NumberId.GEMS_PER_BLOCK), 1e-6);
		assertEquals(3, numbers.num(NumberId.GEMS_PER_BLOCK).basis());
	}

	@Test
	void aShaftWithHalfAFlaskCountsForNeitherState() {
		List<JournalEntry> entries = List.of(fiesta(0, 1_500_000, true, SHAFT / 2, 3));
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of(), State.COLE);
		assertEquals(Source.START, numbers.num(NumberId.GEMS_PER_BLOCK).source());
		assertEquals(96 * 1.25, numbers.get(NumberId.GEMS_PER_BLOCK), 1e-9);
		assertEquals(false, numbers.ownPace());
	}

	@Test
	void typedNumbersBelongToTheirState() {
		Map<String, Double> typed = Map.of("gems_per_block", 900.0, "other_cole", 300_000.0);
		YourNumbers none = learn(List.of(), typed, State.NONE);
		assertEquals(Source.SET, none.num(NumberId.GEMS_PER_BLOCK).source());
		assertEquals(900, none.get(NumberId.GEMS_PER_BLOCK));
		// typed for cole only: no cole takes it as is, no glossy to take out
		assertEquals(300_000, none.get(NumberId.OTHER));
		assertEquals(Source.CALCULATED, none.num(NumberId.OTHER).source());
		YourNumbers cole = learn(List.of(), typed, State.COLE);
		assertEquals(900 * 1.25, cole.get(NumberId.GEMS_PER_BLOCK), 1e-9);
		assertEquals(Source.CALCULATED, cole.num(NumberId.GEMS_PER_BLOCK).source());
		assertEquals(Source.SET, cole.num(NumberId.OTHER).source());
		// fiesta unknown: the plain key
		assertEquals(900, learn(List.of(), typed, null).get(NumberId.GEMS_PER_BLOCK));
	}

	@Test
	void coinsPerSpawnPerState() {
		long a = 1_000_000_000L;
		long b = 2_000_000_000L;
		List<JournalEntry> none = skips(a, 6, 100_000).stream().map(e -> EntryBuilder.from(e).fiesta(false, 0L).build()).toList();
		List<JournalEntry> cole = skips(b, 5, 100_000).stream().map(e -> EntryBuilder.from(e).fiesta(true, 0L).build()).toList();
		List<MiningEntry> noneMining = new ArrayList<>();
		for (int i = 0; i < 3; i++) noneMining.add(new MiningEntry(1, a, a + 1 + i, Map.of("Rough Jade Gemstone", 1L), false, Map.of()));
		List<MiningEntry> coleMining = new ArrayList<>();
		for (int i = 0; i < 10; i++) coleMining.add(new MiningEntry(1, b, b + 1 + i, Map.of("Rough Jade Gemstone", 1L), false, Map.of()));
		List<SessionSummary> sessions = List.of(new SessionSummary(b, cole, coleMining, List.of()), new SessionSummary(a, none, noneMining, List.of()));
		YourNumbers numbers = learn(sessions, Map.of(), State.COLE);
		// 10 msgs at 100k over 5 spawns
		assertEquals(200_000, numbers.get(NumberId.COINS_PER_SPAWN), 1e-6);
		assertEquals(5, numbers.num(NumberId.COINS_PER_SPAWN).basis());
		Map<State, StateNum> coins = numbers.fiesta().of(NumberId.COINS_PER_SPAWN);
		assertEquals(50_000, coins.get(State.NONE).num().value(), 1e-6);
		// no flask spawns: no cole (6 vs 5 spawns) x 1.5
		assertEquals(75_000, coins.get(State.FLASK).num().value(), 1e-6);
		assertEquals(State.NONE, coins.get(State.FLASK).from());
		// unknown: 13 msgs over 11 spawns
		assertEquals(1_300_000.0 / 11, learn(sessions, Map.of(), null).get(NumberId.COINS_PER_SPAWN), 1e-6);
	}

	// guide_model.py lost_ticks (11-26)
	@Test
	void lostTicksFromBlocksInBoost() {
		// rec 8 peridot crystal shaft, as RecordedBreaksTest replays it
		MiningStats recorded = new MiningStats(714_891, 39_675, 1054, 180, "green_stained_glass", 11.0, 4.0, 140_490, 55_500, 11.0, 373);
		// 55.5 s paused, out of the time outside boost
		assertEquals(2.8424660727399367, Learner.lostTicks(recorded), 1e-9);
		MiningStats made = new MiningStats(700_000, 60_000, 1_000, 100, "magenta_stained_glass", 11.0, 4.0, 140_000, 0, 11.0, 400);
		assertEquals(0.2, Learner.lostTicks(made), 1e-9);
		assertNull(Learner.lostTicks(new MiningStats(600_000, 0, 1_000, 0, "x", 13.0, 4.0, 200_000, 0, 13.0, 200)), "boost gave nothing");
		assertNull(Learner.lostTicks(new MiningStats(700_000, 60_000, 1_000, 100, "x", 11.0, 4.0, 140_000, 0, 11.0)), "saved before 0.27.0-alpha.3");
		assertNull(Learner.lostTicks(new MiningStats(700_000, 60_000, 1_000, 100, "x", 11.0, 4.0, 14_000, 0, 11.0, 40)), "too few blocks in boost");

		// old shafts: starting value, then median of the rest
		List<JournalEntry> entries = new ArrayList<>();
		for (int i = 0; i < 3; i++) entries.add(mined(i * 1_000_000L, 0, 0.15));
		assertEquals(Source.START, learn(List.of(new SessionSummary(1, entries)), Map.of()).num(NumberId.LOST_TICKS).source());
		for (MiningStats stats : List.of(recorded, made, made)) {
			// shaft must match its main blocks gem
			entries.add(EntryBuilder.from(mined(9_000_000L, 0, 0.15)).shaftId(stats == recorded ? "PERI_C" : "JASP_1").mining(stats).build());
		}
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of());
		assertEquals(Source.TRACKED, numbers.num(NumberId.LOST_TICKS).source());
		assertEquals(0.2, numbers.get(NumberId.LOST_TICKS), 1e-9);
		assertEquals(3, numbers.num(NumberId.LOST_TICKS).basis());
	}

	@Test
	void spawnGemFromTheSavedSpawnBlock() {
		long session = 1_000_000_000L;
		List<JournalEntry> skips = skips(session, 14, 100_000);
		List<JournalEntry> entries = new ArrayList<>();
		for (int i = 0; i < skips.size(); i++) {
			// 9 peridot (block or pane), 3 jade, 1 stone main block, 1 not timed
			String block = i < 9 ? (i % 2 == 0 ? "green_stained_glass" : "green_stained_glass_pane") : i < 12 ? "lime_stained_glass" : i == 12 ? "stone" : null;
			entries.add(EntryBuilder.from(skips.get(i)).spawnBlock(block).build());
		}
		// tunnels say jade, portals peridot
		List<MiningEntry> mining = List.of(new MiningEntry(1, session, session + 1, Map.of("Flawed Jade Gemstone", 1_000L), false, Map.of()));
		YourNumbers numbers = learn(List.of(new SessionSummary(session, entries, mining, List.of())), Map.of());
		assertEquals("PERI", numbers.spawnGem());
		// skips() makes every 3rd peridot: 0, 3, 6 of 9
		assertEquals(3.0 / 9, numbers.get(NumberId.SPAWN_GEM), 1e-9);
		assertEquals(9, numbers.num(NumberId.SPAWN_GEM).basis());
		// under 10 w/ a spawn block: old rule, gem mined most outside
		List<JournalEntry> few = new ArrayList<>(skips.subList(0, 6));
		for (int i = 6; i < 14; i++) few.add(entries.get(i));
		numbers = learn(List.of(new SessionSummary(session, few, mining, List.of())), Map.of());
		assertEquals("JADE", numbers.spawnGem());
		assertEquals(14, numbers.num(NumberId.SPAWN_GEM).basis());
	}

	@Test
	void everyGemHasItsGlass() {
		assertEquals("PERI", DATA.gemOfBlock("green_stained_glass").code());
		assertEquals("SAPP", DATA.gemOfBlock("light_blue_stained_glass_pane").code());
		assertNull(DATA.gemOfBlock("infested_stone"));
		assertNull(DATA.gemOfBlock(null));
		for (GameData.Gem gem : DATA.gems()) {
			long blocks = List.of("magenta", "black", "green", "brown", "blue", "yellow", "orange", "light_blue", "purple", "lime", "white", "red")
					.stream().filter(colour -> DATA.gemOfBlock(colour + "_stained_glass") == gem).count();
			assertEquals(1, blocks, gem.name());
		}
	}

	// before 0.26.0-alpha.17: missed blocks + stone counted (efficiency-design.md)
	@Test
	void badlySavedOldShaftsDontCount() {
		List<JournalEntry> entries = new ArrayList<>();
		for (int i = 0; i < 3; i++) entries.add(mined(i * 1_000_000L, 0, 0.15));
		// alpha.10: every kind averaged, no blockTicks. alpha.15: stone as the main block
		entries.add(EntryBuilder.from(mined(5_000_000L, 0, 0.15))
				.mining(new MiningStats(700_000, 60_000, 500, 300, "magenta_stained_glass", 10.8, 4.0, 140_000, 0)).build());
		entries.add(EntryBuilder.from(mined(6_000_000L, 0, 0.15)).mining(new MiningStats(700_000, 60_000, 1_949, 100, "stone", 7.7, 3.9, 140_000, 0, 7.7))
				.build());
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of());
		assertEquals(3, numbers.num(NumberId.EFFICIENCY).basis());
		assertEquals(0.1, numbers.get(NumberId.SPREAD), 1e-9);
		assertEquals(11, numbers.get(NumberId.TICKS_4000));
	}

	@Test
	void corpseOddsComeFromEveryShaft() {
		long recent = 2_000_000_000L;
		long old = 1_000_000_000L;
		// in range: 12 shafts, 1 lapis of 2, extra corpse every 4th
		List<JournalEntry> inRange = skips(recent, 12, 100_000);
		// older: 20 shafts, lapis only, no extra corpse
		List<JournalEntry> all = new ArrayList<>();
		for (JournalEntry entry : skips(old, 20, 100_000)) {
			all.add(EntryBuilder.from(entry).corpses(List.of(new Corpse("Lapis", true), new Corpse("Lapis", false))).extraCorpses(0).build());
		}
		all.addAll(inRange);
		YourNumbers numbers = Learner.learn(new Learner.Input(List.of(new SessionSummary(recent, inRange)), e -> null, e -> 100_000.0, Map.of(), 0,
				TextPatterns.defaults(), null, null, all), DATA);
		assertEquals(3.0 / 32, numbers.get(NumberId.EXTRA_CORPSE), 1e-9);
		assertEquals(32, numbers.num(NumberId.EXTRA_CORPSE).basis());
		assertEquals(52.0 / 64, numbers.get(NumberId.LAPIS_SHARE), 1e-9);
		// range decides the rest
		assertEquals(12, numbers.num(NumberId.SPAWN).basis());
	}

	// 6 old shafts at 15% flawed, 3 new ones at 20%, then a session w/ skips only
	@Test
	void miningNumbersFromTheNewestMinedShafts() {
		List<JournalEntry> old = new ArrayList<>();
		for (int i = 0; i < 6; i++) old.add(mined(i * 1_000_000L, 0, 0.15));
		for (int i = 6; i < 9; i++) old.add(mined(i * 1_000_000L, 0, 0.20));
		long later = 100_000_000L;
		List<JournalEntry> skips = skips(later, 8, 100_000);
		List<JournalEntry> all = new ArrayList<>(old);
		all.addAll(skips);
		List<SessionSummary> range = List.of(new SessionSummary(later, skips));
		// the range has no mined shaft: nothing tracked
		YourNumbers ranged = Learner.learn(new Learner.Input(range, e -> null, e -> 100_000.0, Map.of(), 0, TextPatterns.defaults(), null, null, all, 0), DATA);
		assertEquals(Source.START, ranged.num(NumberId.FLAWED).source());
		assertEquals(Source.START, ranged.num(NumberId.EFFICIENCY).source());
		// newest 5 whatever the session: 2 old + 3 new, the middle one is new
		YourNumbers newest = Learner.learn(new Learner.Input(range, e -> null, e -> 100_000.0, Map.of(), 0, TextPatterns.defaults(), null, null, all, 5), DATA);
		assertEquals(Source.TRACKED, newest.num(NumberId.FLAWED).source());
		assertEquals(0.20, newest.get(NumberId.FLAWED), 1e-5);
		assertEquals(5, newest.num(NumberId.FLAWED).basis());
		assertEquals(5, newest.num(NumberId.EFFICIENCY).basis());
		assertEquals(11, newest.get(NumberId.TICKS_4000));
		// 10: 6 old + 3 new, the middle is old
		YourNumbers ten = Learner.learn(new Learner.Input(range, e -> null, e -> 100_000.0, Map.of(), 0, TextPatterns.defaults(), null, null, all, 10), DATA);
		assertEquals(0.15, ten.get(NumberId.FLAWED), 1e-5);
		assertEquals(9, ten.num(NumberId.FLAWED).basis());
		// spawn numbers stay on the range
		assertEquals(8, newest.num(NumberId.SPAWN).basis());
		// the layout keeps its shafts under every rule, they sit outside the range
		assertEquals(Source.TRACKED, ranged.layouts().get("JASP_1").efficiency().source());
		assertEquals(Layouts.LAST_SHAFTS, ranged.layouts().get("JASP_1").efficiency().basis());
		assertEquals(Source.CALCULATED, ranged.layouts().get("PERI_1").efficiency().source());
	}

	// a layouts own numbers: its newest 5 shafts, the 6th back is out
	@Test
	void aLayoutKeepsItsNewestShafts() {
		List<JournalEntry> entries = new ArrayList<>();
		// 1st one mined 1,200 blocks, the 5 after it 1,000
		entries.add(EntryBuilder.from(mined(0, 0, 0.15))
				.mining(new MiningStats(700_000, 60_000, 1_200, 100, "magenta_stained_glass", 11.0, 4.0, 140_000, 0, 11.0)).build());
		for (int i = 1; i <= 5; i++) entries.add(mined(i * 1_000_000L, 0, 0.15));
		YourNumbers numbers = learn(List.of(new SessionSummary(1, entries)), Map.of());
		YourNumbers.LayoutNum jasper = numbers.layouts().get("JASP_1");
		assertEquals(5, jasper.efficiency().basis());
		assertEquals(Efficiency.shown(entries.getLast().mining()), jasper.efficiency().value(), 1e-9);
		// routes too: 6 left early, the oldest w/ 2,000 blocks
		List<JournalEntry> early = new ArrayList<>();
		early.add(leftEarly(0, 2_000, -60, "BYE"));
		for (int i = 1; i <= 5; i++) early.add(leftEarly(i * 1_000_000L, 700 + i, -60, "BYE"));
		YourNumbers routes = learn(List.of(new SessionSummary(1, early)), Map.of());
		assertEquals(5, routes.layouts().get("JASP_1").route().basis());
		assertEquals(703, routes.layouts().get("JASP_1").route().value());
	}

	@Test
	void theWayBackAfterAMinedShaft() {
		long session = 1_000_000_000L;
		List<JournalEntry> entries = new ArrayList<>();
		long at = session;
		// 760 s mined, 80 s back + 100 s spawn. 4th gap = 20 min break
		long[] back = {80_000, 60_000, 100_000, 1_200_000};
		for (int i = 0; i <= back.length; i++) {
			entries.add(EntryBuilder.from(mined(at, 0, 0.15)).sessionId(session).searchMillis(100_000L).build());
			if (i < back.length) at += 760_000 + back[i] + 100_000;
		}
		YourNumbers numbers = learn(List.of(new SessionSummary(session, entries)), Map.of());
		assertEquals(80, numbers.get(NumberId.BACK_MINED), 1e-9);
		assertEquals(3, numbers.num(NumberId.BACK_MINED).basis());
		// a saved break in the 3rd gap (100 s back): that gap says nothing. 2 left = under 3, the starting value
		long gapStart = entries.get(2).endedAt();
		List<BreakEntry> breaks = List.of(new BreakEntry(BreakEntry.VERSION, session, gapStart, gapStart + 90_000));
		numbers = learn(List.of(new SessionSummary(session, entries, List.of(), breaks)), Map.of());
		assertEquals(Source.START, numbers.num(NumberId.BACK_MINED).source());
		// one outside every gap changes nothing
		breaks = List.of(new BreakEntry(BreakEntry.VERSION, session, session - 500_000, session - 100_000));
		numbers = learn(List.of(new SessionSummary(session, entries, List.of(), breaks)), Map.of());
		assertEquals(80, numbers.get(NumberId.BACK_MINED), 1e-9);
		// skips only, nothing to learn from
		numbers = learn(List.of(new SessionSummary(session, skips(session, 8, 100_000))), Map.of());
		assertEquals(Source.START, numbers.num(NumberId.BACK_MINED).source());
		assertEquals(70, numbers.get(NumberId.BACK_MINED));
	}

	@Test
	void typedWinsAndKeepsTheTracked() {
		List<SessionSummary> sessions = List.of(new SessionSummary(1_000_000_000L, skips(1_000_000_000L, 6, 90_000)));
		YourNumbers numbers = learn(sessions, Map.of("spawn", 75.0, "ticks_3000", 6.0));
		assertEquals(Source.SET, numbers.num(NumberId.SPAWN).source());
		assertEquals(75, numbers.get(NumberId.SPAWN));
		assertEquals(90, numbers.num(NumberId.SPAWN).tracked());
		// typed bp7 ticks set the speed: 3000 x 30 / 6 = 15,000, jasper 8
		assertEquals(6, numbers.get(NumberId.TICKS_3000));
		assertEquals(8, numbers.get(NumberId.TICKS_4000));
	}

	@Test
	void typingParses() {
		assertEquals(104, NumberId.SPAWN.parse("1:44"));
		assertEquals(104, NumberId.SPAWN.parse("1m 44s"));
		assertEquals(104, NumberId.SPAWN.parse("104"));
		assertEquals(0.64, NumberId.EFFICIENCY.parse("64%"), 1e-9);
		assertEquals(1_400_000, NumberId.COINS_PER_SPAWN.parse("1.4M"), 1e-6);
		assertEquals(500_000, NumberId.COINS_PER_SPAWN.parse("500k"), 1e-6);
		assertNull(NumberId.EFFICIENCY.parse("150"), "over 100%");
		assertNull(NumberId.TICKS_4000.parse("0"));
		assertNull(NumberId.SPAWN.parse("abc"));
		// a comma w/ 3 digits after = thousands, else the decimal point (review 3.5)
		assertEquals(1_500_000, NumberId.COINS_PER_SPAWN.parse("1,500,000"), 1e-6);
		assertEquals(1_500_000, NumberId.COINS_PER_SPAWN.parse("1,5m"), 1e-6);
		assertEquals(1_500_000, NumberId.COINS_PER_SPAWN.parse("1,500k"), 1e-6);
		assertEquals(10.5, NumberId.TICKS_4000.parse("10,5"), 1e-9);
		assertEquals(0.645, NumberId.EFFICIENCY.parse("64,5%"), 1e-9);
		assertNull(NumberId.LOST_TICKS.parse("1,5,2"));
		assertNull(NumberId.LOST_TICKS.parse("1,5.2"));
		// a typo cant blow the table up
		assertNull(NumberId.COINS_PER_SPAWN.parse("1e300"));
		assertNull(NumberId.LOST_TICKS.parse("1000"));
		assertEquals("12:50", NumberId.FREEZE.format(770));
		assertEquals("1m 44s", NumberId.SPAWN.format(104));
	}
}
