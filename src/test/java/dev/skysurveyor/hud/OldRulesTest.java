package dev.skysurveyor.hud;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.OldSaves;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudData.SessionTotals;
import dev.skysurveyor.hud.HudData.Totals;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.ColorRule;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudValue.Kind;
import dev.skysurveyor.hud.HudValue.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// pre 0.23 rules turned into typed ones must colour exactly the same. oracle = the old HudColors.color, copied here
class OldRulesTest {
	@Test
	void everyOldRuleColoursTheSame() {
		List<Line> lines = new ArrayList<>();
		for (Path dir : OldSaves.withFile("hud.json")) {
			for (Box box : HudLayout.fromJson(OldSaves.read(dir.resolve("hud.json"))).boxes) {
				for (Line line : box.lines) {
					if (line.rules != null || line.readyRule) lines.add(line);
				}
			}
		}
		lines.addAll(madeUp());
		assertTrue(lines.size() >= 8, "the fixtures have old rules");
		int checked = 0;
		for (Line line : lines) {
			HudValue value = line.hudValue();
			Range range = HudBoxText.ruleRange(line);
			String key = HudExpr.key(new HudExpr.Val(value, range, null));
			List<HudRules.Rule> rules = HudRules.parse(line.advancedRules());
			for (Number number : sweep(line)) {
				HudData data = HudData.live(SessionTotals.NONE, Totals.NONE, null, null, null, 0, ShaftNames.defaults())
						.set(value, range, number).set(HudValue.AVERAGE_SPAWN, Range.SESSION, 85_000L)
						.set(HudValue.COINS_PER_SHAFT, Range.ALL_TIME, 2_000_000.0);
				String old = oldColor(line, range, data);
				String now = HudRules.colors(rules, HudBoxText.values(data, false)).get(key);
				assertEquals(old, now, value.id() + " at " + number + ", rules " + line.advancedRules());
				checked++;
			}
		}
		assertTrue(checked > 100);
	}

	private static List<Line> madeUp() {
		List<Line> lines = new ArrayList<>();
		Line cold = new Line(HudValue.SHAFT_COLD);
		cold.readyRule = true;
		lines.add(cold);
		Line spawn = new Line(HudValue.SPAWN_TIMER);
		spawn.readyRule = true;
		spawn.rules = new ArrayList<>(List.of(rule(ColorRule.When.UNDER, 60, null, "a")));
		lines.add(spawn);
		Line fine = new Line(HudValue.SESSION_CORPSE_FINE);
		fine.range = Range.PER_HOUR;
		fine.rules = new ArrayList<>(List.of(rule(ColorRule.When.OVER, 2.5, null, "9")));
		lines.add(fine);
		Line coins = new Line(HudValue.COINS);
		coins.rules = new ArrayList<>(List.of(rule(ColorRule.When.UNDER, 0, "{coins_per_shaft:all_time}", "c"),
				rule(ColorRule.When.OVER, 1_250_000, null, "a")));
		lines.add(coins);
		Line powder = new Line(HudValue.SESSION_POWDER);
		powder.advanced = true;
		powder.text = "Powder {session_powder:all_time:short}";
		powder.rules = new ArrayList<>(List.of(rule(ColorRule.When.OVER, 1_000_000, null, "e")));
		lines.add(powder);
		return lines;
	}

	private static ColorRule rule(ColorRule.When when, double value, String than, String color) {
		ColorRule rule = new ColorRule();
		rule.when = when;
		rule.value = value;
		rule.than = than;
		rule.color = color;
		return rule;
	}

	// around every limit, in what HudData holds (ms for times)
	private static List<Number> sweep(Line line) {
		Kind kind = line.hudValue().kind();
		TreeSet<Double> limits = new TreeSet<>(List.of(0.0, 1.0, -1.0, 85.0, 1_000.0, 2_000_000.0, 1e9));
		if (line.rules != null) line.rules.forEach(r -> limits.add(r.value));
		if (kind == Kind.COLD) {
			for (int c = -100; c <= 0; c++) limits.add((double) c);
		}
		List<Number> out = new ArrayList<>();
		for (double limit : limits) {
			for (double d : new double[] {-1, -0.5, -0.01, 0, 0.01, 0.5, 1}) {
				double v = limit + d;
				switch (kind) {
					case TIME -> {
						if (v >= 0) out.add(Math.round(v * 1000));
					}
					case INTEGER, COLD -> out.add(Math.round(v));
					default -> out.add(v);
				}
			}
		}
		return out;
	}

	// the old HudColors.color, as of 0.23.0-alpha.3
	private static String oldColor(Line line, Range range, HudData data) {
		HudValue value = line.hudValue();
		Double number = data.number(value, range, false);
		if (number == null) return null;
		double shown = shown(value.kind(), number);
		if (line.rules != null) {
			for (ColorRule rule : line.rules) {
				Double limit = rule.value;
				if (rule.than != null) {
					HudExpr.Val tag = (HudExpr.Val) HudExpr.parseOrThrow(rule.than.replaceAll("[{}]", ""));
					Double other = data.number(tag.value(), tag.range(), false);
					limit = other == null ? null : shown(tag.value().kind(), other);
				}
				if (limit != null && (rule.when == ColorRule.When.UNDER ? shown < limit : shown > limit)) return rule.color;
			}
		}
		if (!line.readyRule) return null;
		return switch (value) {
			case SPAWN_TIMER -> {
				Double average = data.number(HudValue.AVERAGE_SPAWN, Range.SESSION, false);
				yield average != null && shown > shown(Kind.TIME, average) ? "c" : null;
			}
			case SHAFT_COLD -> shown <= HudColors.COLD_RED ? "c" : shown <= HudColors.COLD_YELLOW ? "e" : null;
			default -> null;
		};
	}

	private static double shown(Kind kind, double number) {
		return kind == Kind.TIME ? Math.floor(number / 1000) : number;
	}
}
