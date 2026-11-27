package dev.skysurveyor.hud;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudExpr.Type;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.journal.PersonalBests;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// example data: spawn 1:42, avg 1:25, coins 14.82M, 6 shafts, time inside 2:14, ping 42
class HudExprTest {
	private static String show(String expr) {
		return HudBoxText.tagText(HudExpr.parseOrThrow(expr), HudData.EXAMPLE, false);
	}

	private static String error(String expr) {
		HudExpr.Parsed parsed = HudExpr.parse(expr);
		if (parsed.error() != null) return parsed.error();
		try {
			HudExpr.eval(parsed.node(), HudExpr.NONE);
			return null;
		} catch (HudExpr.Error e) {
			return e.getMessage();
		}
	}

	@Test
	void maths() {
		assertEquals("0:17", show("spawn - avg_spawn"));
		assertEquals("-0:43", show("spawn - avg_spawn - 60"), "sign on its own");
		assertEquals("2:12", show("spawn + 30"), "a number next to a time is secs");
		assertEquals("3:42", show("spawn + 2m"), "2m next to a time = 2 minutes");
		assertEquals("2.5M", show("coins / shafts"), "looks like its first value");
		assertEquals("2,470,000", show("coins / shafts:comma"), "a format at the end formats the result");
		assertEquals("2,470,000", show("(coins / shafts):comma"));
		assertEquals("120", show("spawn / avg_spawn * 100:whole"), "time / time = a plain number");
		assertEquals("120.0", show("spawn / avg_spawn * 100:tenths"));
		assertEquals("23.4M/h", show("coins:per_hour / 2"), "/h stays");
		assertEquals("84ms", show("ping * 2"));
		assertEquals("2.2", show("time_inside / 60:tenths"), "a number format turns a time into secs");
		assertEquals("7", show("shafts + 1"));
		assertEquals("-6❄", show("cold / 2"));
		assertEquals("-", show("coins / 0"));
		assertEquals("1:42", show("spawn"));
		assertEquals("102.0", show("spawn:tenths"), "a lone value takes a number format too");
		assertEquals("6", show("shafts"), "old path for a lone value");
	}

	@Test
	void mistakesSayWhatsWrong() {
		assertEquals("not a value: coin. Did you mean coins?", error("coin"));
		assertEquals("coins has no :hourly. Did you mean :per_hour?", error("coins:hourly"));
		assertEquals("formats go at the end", error("coins:comma / shafts"));
		assertEquals("can't add coins and a time", error("coins + time_inside"));
		assertEquals("no maths with text values", error("shaft_type + 1"));
		assertEquals("a ( has no )", error("(coins + 1"));
		assertEquals("something's missing at the end", error("coins +"));
		assertEquals("can't read \"1\"", error("1,2,3"));
		assertEquals("something's missing before shafts", error("coins shafts"));
		assertNull(error("average_spawn:shaft"), "old ids still read");
		assertNull(error("Spawn"), "names arent case sensitive");
	}

	@Test
	void numbersAsPlayersTypeThem() {
		assertEquals(1_500_000.0, number("1,5M"));
		assertEquals(43_436.0, number("43,436"));
		assertEquals(150_000.0, number("150k"));
		assertEquals(2.5, number("2,5"));
		assertEquals(-80.0, number("-80❄"));
		assertEquals(90.0, number("1:30"));
		assertEquals(90.0, number("90s"));
		assertEquals(90.0, number("1m 30s"));
		assertEquals(Type.TIME, HudExpr.eval(HudExpr.parseOrThrow("90s"), HudExpr.NONE).type());
		assertEquals(2_000_000.0, number("2m"), "2m alone is 2 million");
	}

	private static double number(String text) {
		return HudExpr.eval(HudExpr.parseOrThrow(text), HudExpr.NONE).value();
	}

	@Test
	void keysIgnoreFormatsAndDefaults() {
		assertEquals(HudExpr.key(HudExpr.parseOrThrow("coins / shafts")), HudExpr.key(HudExpr.parseOrThrow("(coins:session / shafts):comma")));
		assertFalse(HudExpr.key(HudExpr.parseOrThrow("coins")).equals(HudExpr.key(HudExpr.parseOrThrow("coins:per_hour"))));
		assertEquals("coins / shafts", HudExpr.plain(HudExpr.parseOrThrow("coins:session / session_shafts:session")));
		assertEquals("spawn / avg_spawn * 100", HudExpr.plain(HudExpr.parseOrThrow("(spawn / avg_spawn) * 100:whole")));
		assertEquals("coins - (shafts - 1)", HudExpr.plain(HudExpr.parseOrThrow("coins - (shafts - 1)")));
		assertEquals("cold", HudExpr.plain(HudExpr.parseOrThrow("shaft_cold")), "old ids come out as tags");
	}

	@Test
	void everyValueHasAShortUniqueTag() {
		List<String> tags = new java.util.ArrayList<>();
		for (HudValue value : HudValue.values()) {
			assertTrue(value.tag().matches("[a-z_]+"), value.tag());
			assertFalse(tags.contains(value.tag()), "tag twice: " + value.tag());
			tags.add(value.tag());
			if (!value.isTag()) {
				assertNull(HudValue.byTag(value.tag()), "rows, not a tag");
				continue;
			}
			assertEquals(value, HudValue.byTag(value.tag()));
			assertEquals(value, HudValue.byTag(value.id()), "old id still works");
		}
	}

	@Test
	void typedRules() {
		List<HudRules.Rule> rules = HudRules.parse(List.of("{coins} > lots &a", "{coins} > 1M", "{shaft_type} = Jade &a",
				"{coins:session} > 2M &a&l", "coins > 1M &a", "{coins} > 1:30 &a", "{spawn} > {coins} &a", "", "{cold} <= -80 &c"));
		assertEquals(List.of("\"lots\" isn't a number. Values go in { }", "add a colour at the end, like &c",
				"Shaft type is text, rules need a number", "one colour at the end, like &c. No styles",
				"start with a {value}, then a compare like > or <=", "can't compare coins with a time",
				"can't compare a time with coins"), rules.stream().limit(7).map(HudRules.Rule::error).toList());
		HudRules.Rule cold = rules.getLast();
		assertEquals(9, cold.n(), "n = line in the field, blank lines count");
		assertNull(cold.error());
		assertEquals("≤", cold.op());
		assertEquals("cold", cold.key());
	}

	@Test
	void rulesMatch() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, 102_000L, null, 0, ShaftNames.defaults())
				.set(HudValue.TPS, null, 19.97).set(HudValue.AVERAGE_SPAWN, Range.SESSION, 85_000L);
		HudExpr.Values values = HudBoxText.values(data, false);
		assertTrue(matches("{tps} = 20 &a", values), "= at the typed precision");
		assertFalse(matches("{tps} = 20.00 &a", values));
		assertTrue(matches("{tps} != 19.9 &a", values));
		assertFalse(matches("{spawn} > 2m &c", values), "1:42 isn't over 2 minutes");
		assertTrue(matches("{spawn} >= 1:42 &c", values));
		assertTrue(matches("{spawn - avg_spawn} > 15 &c", values));
		assertTrue(matches("{spawn / avg_spawn * 100} > 110 &c", values));
		assertFalse(matches("{coins} > 0 &c", values), "missing = no match");
	}

	private static boolean matches(String rule, HudExpr.Values values) {
		HudRules.Rule parsed = HudRules.parse(List.of(rule)).getFirst();
		assertNull(parsed.error(), rule);
		return HudRules.matches(parsed, values);
	}

	@Test
	void hintsUnderTheRules() {
		HudData data = HudFixtures.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults()).cold(-63)
				.set(HudValue.COINS, Range.PER_HOUR, 46_800_000.0);
		HudExpr.Values values = HudBoxText.values(data, false);
		assertEquals(List.of("§7Now: {cold} §eyellow§7 (rule 2)."),
				HudRules.hints(HudRules.preset(HudValue.SHAFT_COLD), "Cold: &b{cold}", values));
		assertEquals(List.of("§cRule 1: {coins} isn't in the text, so it colours nothing. Did you mean {coins:per_hour}?",
				"§cRule 2 ({fps} > nope &a): \"nope\" isn't a number. Values go in { }.", "§7Now: no rule matches."),
				HudRules.hints(List.of("{coins} > 1M &a", "{fps} > nope &a"), "{coins:per_hour}", values));
		assertEquals(List.of(), HudRules.hints(List.of(), "{coins}", values));
	}

	@Test
	void oldRulesAsTyped() {
		HudLayout.ColorRule over = new HudLayout.ColorRule();
		over.value = 40_000_000;
		over.color = "a";
		HudLayout.ColorRule under = new HudLayout.ColorRule();
		under.when = HudLayout.ColorRule.When.UNDER;
		under.value = 10_000_000;
		assertEquals(List.of("{coins:per_hour} > 40M &a", "{coins:per_hour} < 10M &c"),
				HudRules.fromOld(HudValue.COINS, Range.PER_HOUR, List.of(over, under), false));
		HudLayout.ColorRule than = new HudLayout.ColorRule();
		than.than = "{average_spawn}";
		assertEquals(List.of("{spawn} > {avg_spawn} &c", "{spawn} > {avg_spawn} &c"),
				HudRules.fromOld(HudValue.SPAWN_TIMER, null, List.of(than), true), "own rule, then the ready rule");
	}

	@Test
	void listClicksType() {
		assertEquals(new HudTyping.Edit("a {coins}", 9), HudTyping.type("a ", 2, "coins"));
		assertEquals(new HudTyping.Edit("{coins / shafts}", 15), HudTyping.type("{coins / }", 9, "shafts"), "inside { } = no braces");
		assertEquals(new HudTyping.Edit("&{x {coins}", 11), HudTyping.type("&{x ", 4, "coins"), "&{ is a plain {");
		assertEquals(new HudTyping.Edit("{coins:all_time}", 16), HudTyping.option("{coins}", 7, HudValue.COINS, "all_time"));
		assertEquals(new HudTyping.Edit("{coins:per_hour:comma}", 22),
				HudTyping.option("{coins:all_time:comma}", 22, HudValue.COINS, "per_hour"), "a range swaps the range");
		assertEquals(new HudTyping.Edit("{coins:all_time:plain} x", 22),
				HudTyping.option("{coins:all_time:comma} x", 22, HudValue.COINS, "plain"), "a format swaps the format");
		assertEquals(new HudTyping.Edit("x {coins:shaft}", 15), HudTyping.option("x ", 2, HudValue.COINS, "shaft"));
		assertEquals(new HudTyping.Edit("{spawn} {coins:plain}", 21), HudTyping.option("{spawn} ", 8, HudValue.COINS, "plain"));
		assertEquals(new HudTyping.Edit("{looted:umber:session}", 22),
				HudTyping.option("{looted:lapis:session}", 22, HudValue.CORPSES_LOOTED, "umber"), "a type swaps the type, goes first");
		assertEquals(new HudTyping.Edit("{dry:vanguard}", 14), HudTyping.option("{dry}", 5, HudValue.DRYSTREAK, "vanguard"));
	}

	// review 11-21: bare pb stuck to its 1st parse
	@Test
	void barePbFollowsTheListOrder() {
		List<PersonalBests.Def> list = PersonalBests.DEFAULTS;
		try {
			assertEquals("pb:session_rate", HudExpr.key(HudExpr.parseOrThrow("pb")));
			HudValue.personalBests(List.of(list.get(2), list.get(0)));
			assertEquals("pb:shaft_powder", HudExpr.key(HudExpr.parseOrThrow("pb")));
		} finally {
			HudValue.personalBests(list);
		}
	}

	@Test
	void personalBestTags() {
		HudData data = HudData.EXAMPLE;
		assertEquals("+10.0M", HudBoxText.tagText(HudExpr.parseOrThrow("pb:shaft_coins"), data, true));
		assertEquals("26.5", HudBoxText.tagText(HudExpr.parseOrThrow("pb"), data, true), "first pb in the list");
		assertEquals("-", HudBoxText.tagText(HudExpr.parseOrThrow("pb:shaft_cold_lowest"), data, true), "no made up example");
		assertNull(error("pb:session_powder_jasp"), "a removed pb isnt a mistake, it shows -");
		assertEquals("pb has no :shaft_powdr. Did you mean :shaft_powder?", error("pb:shaft_powdr"), "typo, not a pb key");
		assertEquals("pb:drystreak_jasp", HudExpr.plain(HudExpr.parseOrThrow("pb:drystreak_jasp")));
		assertEquals("Coins / shaft", HudValue.PERSONAL_BEST.targetName("shaft_coins"));
		assertEquals("PB Jasper drystreak: ", HudValue.PERSONAL_BEST.defaultPrefix(null, "drystreak_jasp"));
		assertEquals("9 (longest 18)", HudData.pbText(new dev.skysurveyor.journal.PersonalBests.Def("drystreak", "JASP", null, null),
				new dev.skysurveyor.journal.PersonalBests.Result(18.0, null, null, 9L), 0));
		assertEquals("1m 31s avg", HudData.pbText(new dev.skysurveyor.journal.PersonalBests.Def("session_spawn", null, null, null),
				new dev.skysurveyor.journal.PersonalBests.Result(91_000.0, null, null, null), 0));
	}

	@Test
	void shaftAndCorpseTypes() {
		assertEquals("12", show("dry"), "jasper example");
		assertEquals("-", show("dry:umber"), "no umber number");
		assertEquals("12", HudBoxText.tagText(HudExpr.parseOrThrow("dry:umber"), HudData.EXAMPLE, true), "editor shows jaspers example");
		assertEquals("dry:umber", HudExpr.plain(HudExpr.parseOrThrow("dry:umber")));
		assertEquals("dry", HudExpr.plain(HudExpr.parseOrThrow("dry:jasper")), "default type left out");
		assertEquals("looted:umber:all_time", HudExpr.plain(HudExpr.parseOrThrow("looted:all_time:umber")), "type, then range");
		assertEquals("172", show("looted:all_time"));
		assertEquals("looted has no :lapiz. Did you mean :lapis?", error("looted:lapiz"));
		assertEquals("spawn has no :jasper", error("spawn:jasper"));
		assertTrue(HudValue.DRYSTREAK.targets().containsAll(List.of("jasper", "vanguard", "jade")));
		assertEquals(List.of("lapis", "umber", "tungsten", "vanguard"), HudValue.CORPSES_LOOTED.targets());
	}

	// reviewer probe: both threw StackOverflowError every refresh. oops
	@Test
	void tooLongOrDeepIsAMistakeNotACrash() {
		assertEquals("too long", error("(".repeat(5000) + "coins" + ")".repeat(5000)));
		assertEquals("too long", error("coins" + "+1".repeat(2000)));
		assertEquals("too long", error("-".repeat(5000) + "coins"));

		String deepest = "(".repeat(HudExpr.MAX_DEPTH) + "coins" + ")".repeat(HudExpr.MAX_DEPTH);
		assertEquals(show("coins"), show(deepest));
		assertEquals("too many ( inside each other", error("(" + deepest + ")"));
		// coins + 2 tokens a term
		String longest = "coins" + "+1".repeat((HudExpr.MAX_TOKENS - 1) / 2);
		assertNull(error(longest));
		assertEquals("too long", error(longest + "+1"));
		String padded = "coins" + " ".repeat(HudExpr.MAX_CHARS - 5);
		assertNull(error(padded));
		assertEquals("too long", error(padded + " "));
		assertEquals("coins + 1 + 1", HudExpr.plain(HudExpr.parseOrThrow("coins+1+1")), "long but legal still works");
	}

	// worst case at the limits, 1/4 of the 1 MB stack
	@Test
	void limitsFitASmallStack() throws InterruptedException {
		List<String> worst = List.of(
				"(".repeat(HudExpr.MAX_DEPTH) + "coins" + "+1".repeat((HudExpr.MAX_TOKENS - 2 * HudExpr.MAX_DEPTH - 1) / 2)
						+ ")".repeat(HudExpr.MAX_DEPTH),
				"coins" + "*2".repeat((HudExpr.MAX_TOKENS - 1) / 2), "-".repeat(HudExpr.MAX_TOKENS - 1) + "coins",
				"(".repeat(HudExpr.MAX_DEPTH + 1) + "coins" + ")".repeat(HudExpr.MAX_DEPTH + 1), "coins" + "+1".repeat(2000));
		Runnable all = () -> {
			for (String expr : worst) {
				HudExpr.Parsed parsed = HudExpr.parse(expr);
				if (parsed.error() != null) continue;
				HudExpr.eval(parsed.node(), HudExpr.NONE);
				HudBoxText.tagText(parsed.node(), HudData.EXAMPLE, true);
				HudExpr.key(parsed.node());
				HudExpr.plain(parsed.node());
				HudExpr.values(parsed.node());
			}
		};
		// pattern switch bootstraps here, not on the small stack
		HudExpr.key(HudExpr.parseOrThrow("-(coins+1)*2"));
		HudExpr.plain(HudExpr.parseOrThrow("-(coins+1)*2"));
		Throwable[] thrown = new Throwable[1];
		Thread small = new Thread(null, () -> {
			try {
				all.run();
			} catch (Throwable e) {
				thrown[0] = e;
			}
		}, "small stack", 256 * 1024);
		small.start();
		small.join();
		assertNull(thrown[0]);
		for (String expr : worst) {
			HudExpr.Parsed parsed = HudExpr.parse(expr);
			assertTrue(parsed.error() == null || parsed.error().startsWith("too "), expr);
		}
	}
}
