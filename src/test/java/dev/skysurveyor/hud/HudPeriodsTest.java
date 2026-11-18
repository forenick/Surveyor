package dev.skysurveyor.hud;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudValue.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HudPeriodsTest {
	private static final HudData OUTSIDE = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults());

	// docs/hud-periods-plan.md 1a: shaft, session, per hour, all-time. null = value has no such range
	private static final Map<HudValue, List<String>> TABLE = Map.ofEntries(
			Map.entry(HudValue.SESSION_SHAFTS, Arrays.asList(null, "Shafts: ", null, "Shafts all-time: ")),
			Map.entry(HudValue.AVERAGE_SPAWN, Arrays.asList("Spawn time this shaft: ", "Avg spawn: ", null, "Avg spawn all-time: ")),
			Map.entry(HudValue.SESSION_POWDER,
					List.of("Powder this shaft: ", "Shaft powder: ", "Shaft powder/h: ", "Shaft powder all-time: ")),
			Map.entry(HudValue.SESSION_CORPSE_FINE,
					List.of("Corpse Fine this shaft: ", "Corpse Fine: ", "Corpse Fine/h: ", "Corpse Fine all-time: ")),
			Map.entry(HudValue.MINED_METAL,
					List.of("Mined metal this shaft: ", "Mined metal: ", "Mined metal/h: ", "Mined metal all-time: ")),
			Map.entry(HudValue.COINS, List.of("Coins this shaft: ", "Coins: ", "Coins/h: ", "Coins all-time: ")),
			Map.entry(HudValue.COINS_PER_SHAFT, Arrays.asList(null, "Coins/shaft: ", null, "Coins/shaft all-time: ")),
			Map.entry(HudValue.OUTSIDE_COINS, Arrays.asList(null, "Outside coins: ", "Outside coins/h: ", "Outside coins all-time: ")),
			// rows, no label
			Map.entry(HudValue.DROPS, Arrays.asList("", "", null, "")));

	private static final Map<HudValue, String> NO_RANGE = Map.ofEntries(Map.entry(HudValue.SHAFTS_PER_HOUR, "Shafts/h: "),
			Map.entry(HudValue.SHAFTS_TODAY, "Shafts today: "), Map.entry(HudValue.SPAWN_TIMER, "Spawn: "), Map.entry(HudValue.PITY, "Pity: "),
			Map.entry(HudValue.SINCE_LAST_SHAFT, "Since last shaft: "), Map.entry(HudValue.SESSION_TIME, "Session: "),
			Map.entry(HudValue.SHAFT_TYPE, "Shaft: "), Map.entry(HudValue.SHAFT_TIME, "Time inside: "),
			Map.entry(HudValue.SHAFT_CORPSES, "Corpses: "), Map.entry(HudValue.SHAFT_COLD, "Cold: "),
			Map.entry(HudValue.SHAFT_CAMPFIRES, "Campfires: "), Map.entry(HudValue.FPS, "FPS: "), Map.entry(HudValue.PING, "Ping: "),
			Map.entry(HudValue.TPS, "TPS: "), Map.entry(HudValue.CLOCK, "Clock: "), Map.entry(HudValue.MOD_VERSION, "Surveyor "),
			Map.entry(HudValue.MINECRAFT_VERSION, "Minecraft "));

	@Test
	void defaultLabels() {
		for (Map.Entry<HudValue, List<String>> row : TABLE.entrySet()) {
			HudValue value = row.getKey();
			for (Range range : Range.values()) {
				String want = row.getValue().get(range.ordinal());
				assertEquals(want != null, value.hasRange(range), value + " " + range);
				if (want != null) assertEquals(want, value.defaultPrefix(range), value + " " + range);
			}
			assertEquals(row.getValue().get(1), value.defaultPrefix(null), value + " w/o a range = session");
			assertEquals(row.getValue().get(1), value.defaultPrefix(), value + " plain label = session");
		}
		for (Map.Entry<HudValue, String> row : NO_RANGE.entrySet()) {
			assertTrue(row.getKey().ranges().isEmpty());
			assertEquals(row.getValue(), row.getKey().defaultPrefix(null), row.getKey() + " unchanged");
		}
		HudValue looted = HudValue.CORPSES_LOOTED;
		assertEquals(List.of("Umber corpses this shaft: ", "Umber corpses: ", "Umber corpses/h: ", "Umber corpses all-time: "),
				Arrays.stream(Range.values()).map(r -> looted.defaultPrefix(r, "umber")).toList());
		assertEquals("Lapis corpses: ", looted.defaultPrefix(Range.SESSION));
		assertEquals("Jasper drystreak: ", HudValue.DRYSTREAK.defaultPrefix(null));
		assertEquals("Jade drystreak: ", HudValue.DRYSTREAK.defaultPrefix(null, "jade"));
		assertEquals("PB Jasper dry: ", HudValue.PERSONAL_BEST.defaultPrefix(null, "drystreak_jasp"), "pb names unchanged");
		assertEquals(TABLE.size() + NO_RANGE.size() + 3, HudValue.values().length, "every value checked");
		assertEquals("Shaft powder", HudValue.SESSION_POWDER.displayName());
		assertEquals("Coins per shaft", HudValue.COINS_PER_SHAFT.displayName());
	}

	@Test
	void oldLabelsCountAsUntyped() {
		Map<HudValue, List<String>> old = Map.of(HudValue.SESSION_SHAFTS, List.of("All-time shafts: "), HudValue.SESSION_POWDER,
				List.of("All-time shaft powder: ", "Powder: ", "All-time powder: "), HudValue.AVERAGE_SPAWN,
				List.of("All-time avg spawn: ", "This shaft's spawn: "), HudValue.SESSION_CORPSE_FINE,
				List.of("All-time corpse Fine: ", "Fine: "), HudValue.MINED_METAL, List.of("All-time mined metal: ", "Metal this shaft: "),
				HudValue.COINS, List.of("All-time coins: "), HudValue.COINS_PER_SHAFT,
				List.of("Avg coins/shaft: ", "All-time avg coins/shaft: ", "All-time avg/shaft: "), HudValue.OUTSIDE_COINS,
				List.of("Outside shafts: ", "All-time outside shafts: "));
		for (Map.Entry<HudValue, List<String>> row : old.entrySet()) {
			HudValue value = row.getKey();
			for (String label : row.getValue()) {
				assertTrue(value.isDefaultPrefix(label), label);
				Line line = new Line(value, label, true, 0);
				line.setRange(Range.ALL_TIME);
				assertEquals(value.defaultPrefix(Range.ALL_TIME), line.prefix, label + " follows the period");
			}
		}
		// saved as "Coins: " + PER_HOUR before 0.25
		Line hour = new Line(HudValue.COINS, "Coins: ", true, 0);
		hour.range = Range.PER_HOUR;
		hour.setRange(Range.ALL_TIME);
		assertEquals("Coins all-time: ", hour.prefix);

		for (String type : List.of("Lapis", "Umber", "Tungsten", "Vanguard")) {
			for (String label : List.of(type + " looted: ", "All-time " + type + " looted: ", type + " looted this shaft: ")) {
				assertTrue(HudValue.CORPSES_LOOTED.isDefaultPrefix(label), label);
				Line line = new Line(HudValue.CORPSES_LOOTED, label, true, 0);
				line.setTarget("tungsten");
				assertEquals("Tungsten corpses: ", line.prefix, label + " follows the type");
				line.setRange(Range.PER_HOUR);
				assertEquals("Tungsten corpses/h: ", line.prefix);
			}
		}
		for (String label : List.of("Jasper dry: ", "Jade dry: ", "Jade drystreak: ")) {
			Line dry = new Line(HudValue.DRYSTREAK, label, true, 0);
			dry.setTarget("ruby");
			assertEquals("Ruby drystreak: ", dry.prefix, label);
		}
		Line typed = new Line(HudValue.COINS, "Money: ", true, 0);
		typed.setRange(Range.PER_HOUR);
		assertEquals("Money: ", typed.prefix, "typed stays");
		assertFalse(HudValue.COINS.isDefaultPrefix("Shafts: "), "another values label is typed");
		Line umber = new Line(HudValue.CORPSES_LOOTED, "Umb: ", true, 0);
		umber.setTarget("umber");
		assertEquals("Umb: ", umber.prefix, "typed stays");
	}

	@Test
	void perHourShowsOnce() {
		Box box = new Box();
		assertEquals("§7Fine/h: §949.3", line(box, HudValue.SESSION_CORPSE_FINE, "Fine/h: ", Range.PER_HOUR));
		assertEquals("§7Coins: §646.8M/h", line(box, HudValue.COINS, "Coins: ", Range.PER_HOUR), "plain label keeps the /h");
		assertEquals("§7w/helmet: §646.8M/h", line(box, HudValue.COINS, "w/helmet: ", Range.PER_HOUR));
		assertEquals("§7Coins per hour: §646.8M", line(box, HudValue.COINS, "Coins per hour: ", Range.PER_HOUR));
		assertEquals("§7Hourly: §646.8M", line(box, HudValue.COINS, "Hourly: ", Range.PER_HOUR));
		assertEquals("§7Coins/hr §646.8M", line(box, HudValue.COINS, "Coins/hr ", Range.PER_HOUR));
		assertEquals("§7Coins/h: §614.8M", line(box, HudValue.COINS, "Coins/h: ", Range.SESSION), "nothing to drop");

		Line free = new Line(HudValue.COINS);
		free.advanced = true;
		free.text = "&7Coins/h &6{coins:per_hour} &7and {shaft_powder:per_hour}";
		box.lines.add(free);
		assertEquals("Coins/h 46.8M and 137,166/h", strip(HudBoxText.line(box, free, HudData.EXAMPLE, true)), "only the 1st");
		free.text = "Coins/h {coins:per_hour / 2}";
		assertEquals("Coins/h 23.4M", strip(HudBoxText.line(box, free, HudData.EXAMPLE, true)), "maths too");
		free.text = "{coins:per_hour} /h";
		assertEquals("46.8M/h /h", strip(HudBoxText.line(box, free, HudData.EXAMPLE, true)), "only text before counts");

		assertEquals("", HudBoxText.suffix(HudValue.COINS, Range.PER_HOUR, "Coins/h: "), "format tooltip");
		assertEquals("/h", HudBoxText.suffix(HudValue.COINS, Range.PER_HOUR, "Coins: "));
		assertEquals("ms", HudBoxText.suffix(HudValue.PING, null, "Ping/h: "));
	}

	private static String line(Box box, HudValue value, String prefix, Range range) {
		Line line = new Line(value, prefix, true, 0);
		line.range = range;
		return HudBoxText.line(box, line, HudData.EXAMPLE, true);
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "");
	}

	// turning Advanced on never changes the look, /h rule included
	@Test
	void advancedLooksTheSameForEveryDefault() {
		for (HudValue value : HudValue.values()) {
			List<Range> ranges = value.ranges().isEmpty() ? java.util.Collections.singletonList(null) : value.ranges();
			List<String> targets = switch (value.target()) {
				case NONE -> java.util.Collections.singletonList(null);
				case PERSONAL_BEST -> List.of(value.defaultTarget());
				default -> List.of(value.defaultTarget(), value.targets().getLast());
			};
			for (Range range : ranges) {
				for (String target : targets) {
					for (HudData data : List.of(HudData.EXAMPLE, OUTSIDE)) {
						Box box = new Box();
						Line line = new Line(value);
						if (target != null) line.setTarget(target);
						line.setRange(range);
						assertEquals(value.defaultPrefix(range, line.target()), line.prefix);
						box.lines.add(line);
						String easy = HudBoxText.line(box, line, data, true);
						line.settle(box);
						line.advanced = true;
						// preset rules colour on their own
						line.colorRules = new ArrayList<>();
						String advanced = HudBoxText.line(box, line, data, true);
						assertEquals(HudTest.visible(easy), HudTest.visible(advanced), value + " " + range + " " + target + ": " + line.text);
						assertTrue(count(strip(easy), "/h") <= 1, easy);
					}
				}
			}
		}
	}

	@Test
	void oldLinesShowPerHourOnce() {
		int rates = 0;
		for (Path dir : OldSaves.withFile("hud.json")) {
			for (Box box : HudLayout.fromJson(OldSaves.read(dir.resolve("hud.json"))).boxes) {
				for (Line line : box.lines) {
					if (line.advancedMode()) continue;
					String shown = strip(HudBoxText.line(box, line, HudData.EXAMPLE, true));
					String where = dir.getFileName() + " " + line.value + " " + line.prefix;
					assertTrue(count(shown, "/h") <= 1, where + ": " + shown);
					HudValue value = line.hudValue();
					if (line.range() != Range.PER_HOUR || !value.hasRange(Range.PER_HOUR)) continue;
					rates++;
					if (!HudBoxText.saysRate(line.prefix)) assertTrue(shown.endsWith("/h"), where + ": " + shown);
				}
			}
		}
		assertTrue(rates > 0, "the fixtures have per hour lines");
	}

	@Test
	void periodsOfEveryValue() {
		for (HudValue value : HudValue.values()) {
			List<HudValue.Period> periods = value.periods();
			if (value.family() == HudValue.SESSION_SHAFTS) {
				assertEquals(List.of("Session", "Today", "Per hour", "All-time"), periods.stream().map(HudValue.Period::name).toList());
				assertEquals(List.of("session", "today", "/h", "all-time"), periods.stream().map(HudValue.Period::shortName).toList());
				assertEquals(List.of(HudValue.SESSION_SHAFTS, HudValue.SHAFTS_TODAY, HudValue.SHAFTS_PER_HOUR, HudValue.SESSION_SHAFTS),
						periods.stream().map(HudValue.Period::value).toList());
				assertEquals(Arrays.asList(Range.SESSION, null, null, Range.ALL_TIME), periods.stream().map(HudValue.Period::range).toList());
				assertEquals(value == HudValue.SESSION_SHAFTS, value.inAddLine(), value + " folds into Shafts");
				continue;
			}
			assertTrue(value.inAddLine());
			if (value.ranges().size() < 2) {
				assertTrue(periods.isEmpty(), value + " has no periods");
				continue;
			}
			assertEquals(value.ranges(), periods.stream().map(HudValue.Period::range).toList(), value + " in Range order");
			for (HudValue.Period period : periods) {
				assertEquals(value, period.value());
				assertEquals(period.range().displayName(), period.name());
				assertEquals(period, value.period(period.range()));
			}
		}
		assertEquals(List.of("This shaft", "Session", "Per hour", "All-time"),
				HudValue.COINS.periods().stream().map(HudValue.Period::name).toList());
		assertEquals(List.of("shaft", "session", "/h", "all-time"),
				HudValue.COINS.periods().stream().map(HudValue.Period::shortName).toList());
		assertEquals(HudValue.SHAFTS_TODAY.periods(), HudValue.SESSION_SHAFTS.periods(), "same family both ways");
		assertEquals("Today", HudValue.SHAFTS_TODAY.period(null).name());
		assertEquals("Per hour", HudValue.SHAFTS_PER_HOUR.period(null).name());
		assertEquals(27, HudValue.listed().stream().filter(HudValue::inAddLine).count(), "27 rows in Add line");
		assertEquals(HudValue.values().length, HudValue.listed().size(), "every value listed once");
	}

	@Test
	void lineNames() {
		assertEquals("Coins per hour", HudValue.COINS.lineName(Range.PER_HOUR, null));
		assertEquals("Drops this shaft", HudValue.DROPS.lineName(Range.SHAFT, null));
		assertEquals("Drops this session", HudValue.DROPS.lineName(Range.SESSION, null));
		assertEquals("Drops all-time", HudValue.DROPS.lineName(Range.ALL_TIME, null));
		assertEquals("Coins this shaft", HudValue.COINS.lineName(Range.SHAFT, null));
		assertEquals("Spawn time of this shaft", HudValue.AVERAGE_SPAWN.lineName(Range.SHAFT, null));
		assertEquals("Average spawn this session", HudValue.AVERAGE_SPAWN.lineName(Range.SESSION, null));
		assertEquals("Powder from this shaft", HudValue.SESSION_POWDER.lineName(Range.SHAFT, null));
		assertEquals("Shaft powder all-time", HudValue.SESSION_POWDER.lineName(Range.ALL_TIME, null));
		assertEquals("Corpse Fine per hour", HudValue.SESSION_CORPSE_FINE.lineName(Range.PER_HOUR, null));
		assertEquals("Lapis corpses all-time", HudValue.CORPSES_LOOTED.lineName(Range.ALL_TIME, null));
		assertEquals("Umber corpses this shaft", HudValue.CORPSES_LOOTED.lineName(Range.SHAFT, "umber"));
		assertEquals("Jasper drystreak", HudValue.DRYSTREAK.lineName(null, null));
		assertEquals("Shafts this session", HudValue.SESSION_SHAFTS.lineName(Range.SESSION, null));
		assertEquals("Shafts today", HudValue.SHAFTS_TODAY.lineName(null, null));
		assertEquals("Shafts per hour", HudValue.SHAFTS_PER_HOUR.lineName(null, null));
		assertEquals("Coins per shaft all-time", HudValue.COINS_PER_SHAFT.lineName(Range.ALL_TIME, null));
		assertEquals("Coins outside shafts this session", HudValue.OUTSIDE_COINS.lineName(Range.SESSION, null));
		assertEquals("Corpses in this shaft", HudValue.SHAFT_CORPSES.lineName(null, null));
		assertEquals("Spawn timer", HudValue.SPAWN_TIMER.lineName(null, null));
		assertEquals("FPS", HudValue.FPS.lineName(null, null));
	}

	@Test
	void addingByPeriodGivesItsLabel() {
		for (HudValue value : HudValue.values()) {
			for (HudValue.Period period : value.periods()) {
				Line line = Line.of(period);
				assertEquals(period.value().id(), line.value);
				assertEquals(period.range(), line.range());
				assertEquals(period.value().defaultPrefix(period.range()), line.prefix, period.toString());
			}
		}
		HudValue.Period today = HudValue.SESSION_SHAFTS.periods().get(1);
		assertEquals("Shafts today: ", Line.of(today).prefix);
		assertEquals("Shafts/h: ", Line.of(HudValue.SESSION_SHAFTS.periods().get(2)).prefix);
		assertEquals("Shafts all-time: ", Line.of(HudValue.SESSION_SHAFTS.periods().get(3)).prefix);
		assertEquals("Coins/h: ", Line.of(HudValue.COINS.period(Range.PER_HOUR)).prefix);
	}

	@Test
	void periodSwitchesInsideTheShaftsFamily() {
		List<HudValue.Period> shafts = HudValue.SESSION_SHAFTS.periods();
		Line line = new Line(HudValue.SESSION_SHAFTS);
		line.format = 2;
		line.text = "{shafts} kept";
		line.colorRules = new ArrayList<>(List.of("{shafts} > 5 &a"));
		line.dashOutside = true;
		assertEquals(shafts.get(0), line.period());
		line.setPeriod(shafts.get(2));
		assertEquals("shafts_per_hour", line.value);
		assertEquals(null, line.range, "shafts per hour has no ranges");
		assertEquals("Shafts/h: ", line.prefix);
		assertEquals(0, line.format, "integer format index means nothing for a decimal");
		assertEquals(shafts.get(2), line.period());
		assertEquals("{shafts} kept", line.text);
		assertEquals(List.of("{shafts} > 5 &a"), line.colorRules);
		assertTrue(line.dash());
		line.format = 1;
		line.setPeriod(shafts.get(1));
		assertEquals("shafts_today", line.value);
		assertEquals("Shafts today: ", line.prefix);
		assertEquals(0, line.format);
		line.setPeriod(shafts.get(3));
		assertEquals("session_shafts", line.value);
		assertEquals(Range.ALL_TIME, line.range);
		assertEquals("Shafts all-time: ", line.prefix);

		Line typed = new Line(HudValue.SHAFTS_PER_HOUR, "Rate: ", true, 0);
		typed.valueColor = "a";
		typed.setPeriod(shafts.get(0));
		assertEquals("Rate: ", typed.prefix, "typed stays");
		assertEquals("a", typed.valueColor, "own colour stays");
		assertEquals(Range.SESSION, typed.range());

		Line coins = new Line(HudValue.COINS);
		coins.format = 1;
		coins.setPeriod(HudValue.COINS.period(Range.PER_HOUR));
		assertEquals("Coins/h: ", coins.prefix);
		assertEquals(1, coins.format, "same value, format stays");
		assertEquals("6", coins.valueColor);

		// ctrl+z = whole layout snapshots
		HudLayout layout = new HudLayout();
		Box box = new Box();
		box.id = "b";
		box.lines.add(new Line(HudValue.SHAFTS_PER_HOUR));
		layout.boxes.add(box);
		HudHistory history = new HudHistory(layout.toJson());
		box.lines.getFirst().setPeriod(shafts.get(3));
		history.record(layout.toJson());
		Line back = HudLayout.fromJson(history.undo()).boxes.getFirst().lines.getFirst();
		assertEquals("shafts_per_hour", back.value);
		assertEquals("Shafts/h: ", back.prefix);
	}

	private static int count(String text, String part) {
		int n = 0;
		for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) n++;
		return n;
	}
}
