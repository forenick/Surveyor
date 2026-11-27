package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;

final class AdvancedText {
	static final int TEXT_LIMIT = 256;
	static final int RULES_LIMIT = 512;
	static final String TEXT_HINT = "§8&e colours, {values}, maths like {coins / shafts}. Click the list to type them.";
	static final String RULES_HELP = "One rule a line: a {value}, a compare, a number or {value}, a colour. For example "
			+ "{cold} <= -80 &c. A rule colours that value in the text, the first match wins. Maths works on both sides: "
			+ "{coins / shafts} >= 2M &a. Numbers: 1:30, 90s, 2m next to a time, 2.5M, 1,5M, 150k.";

	private AdvancedText() {
	}

	static String oneLine(String typed) {
		return typed.replace("\r", "").replace('\n', ' ');
	}

	// trailing empty ones dropped
	static List<String> ruleLines(String typed) {
		List<String> lines = new ArrayList<>(List.of(typed.split("\n", -1)));
		while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
		return lines;
	}

	// rules + new line up to the compare
	static String ruleLead(String rules, String tag) {
		return (rules.isEmpty() || rules.endsWith("\n") ? rules : rules + "\n") + "{" + tag + "} > ";
	}

	// 1st problem only
	static String hint(List<String> problems, boolean noRoom) {
		if (noRoom) return "§cNo room for that, the text is " + TEXT_LIMIT + " and the rules " + RULES_LIMIT + " characters max.";
		if (problems.isEmpty()) return TEXT_HINT;
		String problem = problems.getFirst().replace("§", "") + (problems.size() > 1 ? " (and " + (problems.size() - 1) + " more)" : "");
		return "§c" + problem + (problem.endsWith("?") ? " " : ". ") + "It shows as typed.";
	}

	// for the 1st number in the text
	static String example(String text, HudExpr.Values values) {
		List<String> tags = HudBoxText.numberTags(text);
		return tags.isEmpty() ? "{coins} > 2M &a" : "{" + tags.getFirst() + "} > " + typedNumber(tags.getFirst(), values) + " &c";
	}

	static String typedNumber(String tag, HudExpr.Values values) {
		HudExpr.Parsed parsed = HudExpr.parse(tag);
		HudExpr.Result r;
		try {
			r = parsed.error() == null ? HudExpr.eval(parsed.node(), values) : null;
		} catch (HudExpr.Error e) {
			r = null;
		}
		if (r == null || r.value() == null) return "0";
		double v = r.value();
		return switch (r.type()) {
			case TIME -> HudFormat.thresholdText(HudValue.Kind.TIME, (long) v);
			case COINS -> HudFormat.format(HudValue.Kind.COINS, 0, v);
			case COLD -> Long.toString(Math.round(v));
			case INTEGER -> Math.abs(v) >= 10_000 ? HudFormat.format(HudValue.Kind.INTEGER, 1, Math.round(v)) : Long.toString(Math.round(v));
			default -> Math.abs(v - Math.rint(v)) < 1e-9 ? Long.toString(Math.round(v)) : HudFormat.format(HudValue.Kind.DECIMAL, 0, v);
		};
	}
}
