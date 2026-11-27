package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.hud.HudExpr.Node;
import dev.skysurveyor.hud.HudExpr.Result;
import dev.skysurveyor.hud.HudExpr.Type;
import dev.skysurveyor.hud.HudExpr.Values;
import dev.skysurveyor.hud.HudLayout.ColorRule;
import dev.skysurveyor.hud.HudValue.Range;

// typed colour rules, one a line: {value} > number or {value} &c
public final class HudRules {
	// guide's close call, live hud keeps it current. 1M = its starting value
	private static volatile double closeCall = 1_000_000;
	private static final Pattern RULE = Pattern.compile("\\s*\\{([^{}]*)}\\s*(>=|<=|!=|==|≥|≤|≠|>|<|=)\\s*(.*?)\\s*((?:&.)*)\\s*",
			Pattern.CASE_INSENSITIVE);
	private static final String[] COLOR_WORDS = {"black", "dark blue", "dark green", "dark aqua", "dark red", "purple",
			"gold", "gray", "dark gray", "blue", "green", "aqua", "red", "pink", "yellow", "white"};
	private static final Map<String, List<Rule>> CACHE = new LinkedHashMap<>(16, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<Rule>> eldest) {
			return size() > 128;
		}
	};

	private HudRules() {
	}

	// n = line in the rules field, from 1. error = shown, colours nothing
	public record Rule(int n, String source, String error, Node left, String op, Node right, String color, String key) {
		Rule(int n, String source, String error, Node left, String op, Node right, String color) {
			this(n, source, error, left, op, right, color, left == null ? null : HudExpr.key(left));
		}

		// for messages, quotes the start
		public String start() {
			String s = source.strip();
			return s.length() > 22 ? s.substring(0, 20) + "…" : s;
		}
	}

	public static List<Rule> parse(List<String> lines) {
		if (lines == null || lines.isEmpty()) return List.of();
		String joined = String.join("\n", lines);
		synchronized (CACHE) {
			List<Rule> cached = CACHE.get(joined);
			if (cached != null) return cached;
		}
		List<Rule> rules = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (line != null && !line.isBlank()) rules.add(parse(i + 1, line));
		}
		rules = List.copyOf(rules);
		synchronized (CACHE) {
			CACHE.put(joined, rules);
		}
		return rules;
	}

	static Rule parse(int n, String line) {
		Matcher m = RULE.matcher(line);
		if (!m.matches()) return error(n, line, "start with a {value}, then a compare like > or <=");
		List<String> codes = new ArrayList<>();
		String c = m.group(4);
		for (int i = 0; i + 1 < c.length(); i += 2) codes.add(c.substring(i + 1, i + 2).toLowerCase(Locale.ROOT));
		if (codes.isEmpty()) return error(n, line, "add a colour at the end, like &c");
		if (codes.size() > 1 || HudLayout.COLOR_CODES.indexOf(codes.getFirst().charAt(0)) < 0) {
			return error(n, line, "one colour at the end, like &c. No styles");
		}
		HudExpr.Parsed left = HudExpr.parse(m.group(1));
		if (left.error() != null) return error(n, line, left.error());
		String rightText = m.group(3);
		if (rightText.isEmpty()) return error(n, line, "add a number or a {value} after the compare");
		String braced = HudBoxText.unbrace(rightText);
		boolean inBraces = braced != null;
		HudExpr.Parsed right = HudExpr.parse(inBraces ? braced : rightText);
		Result a;
		try {
			a = HudExpr.eval(left.node(), HudExpr.NONE);
		} catch (HudExpr.Error e) {
			return error(n, line, e.getMessage());
		}
		if (a.type() == Type.TEXT) return error(n, line, textName(left.node()) + " is text, rules need a number");
		if (!inBraces && (right.error() != null || !HudExpr.literal(right.node()))) {
			return error(n, line, "\"" + rightText + "\" isn't a number. Values go in { }");
		}
		if (right.error() != null) return error(n, line, right.error());
		Result b;
		try {
			b = HudExpr.eval(right.node(), HudExpr.NONE);
		} catch (HudExpr.Error e) {
			return error(n, line, e.getMessage());
		}
		if (b.type() == Type.TEXT) return error(n, line, "text can't be compared");
		a = HudExpr.asTime(a, b);
		b = HudExpr.asTime(b, a);
		if (!HudExpr.literal(right.node()) && family(a.type()) != family(b.type())) {
			return error(n, line, "can't compare " + HudExpr.name(a.type()) + " with " + HudExpr.name(b.type()));
		}
		if (HudExpr.literal(right.node()) && a.type() != Type.TIME && b.type() == Type.TIME) {
			return error(n, line, "can't compare " + HudExpr.name(a.type()) + " with a time");
		}
		return new Rule(n, line, null, left.node(), op(m.group(2)), right.node(), codes.getFirst());
	}

	private static Rule error(int n, String line, String error) {
		return new Rule(n, line, error, null, null, null, null);
	}

	private static String textName(Node node) {
		List<HudExpr.Val> values = HudExpr.values(node);
		return values.isEmpty() ? "That" : values.getFirst().value().displayName();
	}

	private static String op(String typed) {
		return switch (typed) {
			case ">=" -> "≥";
			case "<=" -> "≤";
			case "!=" -> "≠";
			case "==" -> "=";
			default -> typed;
		};
	}

	private static Type family(Type type) {
		return HudExpr.plain(type) ? Type.NUMBER : type;
	}

	// missing or waiting side = no match
	public static boolean matches(Rule rule, Values values) {
		if (rule.error() != null) return false;
		Result a;
		Result b;
		try {
			a = HudExpr.eval(rule.left(), values);
			b = HudExpr.eval(rule.right(), values);
		} catch (HudExpr.Error e) {
			return false;
		}
		a = HudExpr.asTime(a, b);
		b = HudExpr.asTime(b, a);
		if (a.value() == null || b.value() == null) return false;
		double x = a.value();
		double y = b.value();
		// times in the whole secs they show. typed limits as is, old rules had 90.5
		if (a.type() == Type.TIME || b.type() == Type.TIME) {
			x = (long) x;
			if (!HudExpr.literal(rule.right())) y = (long) y;
		}
		Integer decimals = HudExpr.decimals(rule.right());
		boolean equality = rule.op().equals("=") || rule.op().equals("≠");
		// {tps} = 20 matches 19.97
		if (equality && decimals != null) {
			double scale = Math.pow(10, decimals);
			x = Math.round(x * scale) / scale;
		}
		return switch (rule.op()) {
			case ">" -> x > y;
			case "≥" -> x >= y;
			case "<" -> x < y;
			case "≤" -> x <= y;
			case "=" -> x == y;
			default -> x != y;
		};
	}

	// key -> colour, first match per key wins
	public static Map<String, String> colors(List<Rule> rules, Values values) {
		if (rules.isEmpty()) return Map.of();
		Map<String, String> colors = new HashMap<>();
		for (Rule rule : rules) {
			if (rule.error() != null || colors.containsKey(rule.key())) continue;
			if (matches(rule, values)) colors.put(rule.key(), rule.color());
		}
		return colors;
	}

	// § coded: mistakes, then what colours now
	public static List<String> hints(List<String> lines, String text, Values values) {
		List<Rule> rules = parse(lines);
		List<Node> nodes = HudBoxText.nodes(text);
		Set<String> keys = new HashSet<>();
		for (Node node : nodes) keys.add(HudExpr.key(node));
		List<String> out = new ArrayList<>();
		for (Rule rule : rules) {
			if (rule.error() != null) {
				out.add("§cRule " + rule.n() + " (" + rule.start() + "): " + sentence(rule.error()));
			} else if (!keys.contains(rule.key())) {
				String guess = null;
				if (rule.left() instanceof HudExpr.Val v) {
					for (Node node : nodes) {
						if (node instanceof HudExpr.Val t && t.value() == v.value()) {
							guess = HudExpr.plain(t);
							break;
						}
					}
				}
				out.add("§cRule " + rule.n() + ": {" + HudExpr.plain(rule.left()) + "} isn't in the text, so it colours nothing."
						+ (guess != null ? " Did you mean {" + guess + "}?" : ""));
			}
		}
		if (rules.isEmpty()) return out;
		List<String> now = new ArrayList<>();
		Set<String> done = new HashSet<>();
		for (Rule rule : rules) {
			if (rule.error() != null || !keys.contains(rule.key()) || done.contains(rule.key()) || !matches(rule, values)) continue;
			done.add(rule.key());
			now.add("{" + HudExpr.plain(rule.left()) + "} §" + rule.color() + COLOR_WORDS[HudLayout.COLOR_CODES.indexOf(rule.color())]
					+ "§7 (rule " + rule.n() + ")");
		}
		out.add(now.isEmpty() ? "§7Now: no rule matches." : "§7Now: " + String.join(", ", now) + ".");
		return out;
	}

	private static String sentence(String text) {
		return text.endsWith("?") || text.endsWith(".") ? text : text + ".";
	}

	// what turning Advanced on starts w/. nicks picks (2026-11-12)
	public static List<String> preset(HudValue value) {
		return switch (value) {
			case SPAWN_TIMER -> List.of(tag(value) + " > " + tag(HudValue.AVERAGE_SPAWN) + " &c");
			case SHAFT_COLD -> List.of(tag(value) + " <= " + HudColors.COLD_RED + " &c", tag(value) + " <= " + HudColors.COLD_YELLOW + " &e");
			// last minute, display choice
			case FREEZE_IN -> List.of(tag(value) + " < 1:00 &c");
			// red < 0, yellow up to the typed close call band
			case SHAFT_GAIN, CAMPFIRE_GAIN -> {
				String band = HudFormat.format(HudValue.Kind.COINS, 2, closeCall());
				yield List.of(tag(value) + " >= " + band + " &a", tag(value) + " < 0 &c", tag(value) + " < " + band + " &e");
			}
			default -> List.of();
		};
	}

	private static double closeCall() {
		return closeCall;
	}

	public static void closeCall(double value) {
		closeCall = value;
	}

	private static String tag(HudValue value) {
		return HudBoxText.tag(value, value.defaultRange(), 0);
	}

	// pre 0.23 rules as typed ones: own rules, then the ready rule. range = what they coloured
	public static List<String> fromOld(HudValue value, Range range, List<ColorRule> rules, boolean readyRule) {
		List<String> out = new ArrayList<>();
		String own = HudBoxText.tag(value, range, 0);
		if (rules != null) {
			for (ColorRule rule : rules) {
				String limit = null;
				if (rule.than != null) {
					String inner = HudBoxText.unbrace(rule.than.strip());
					HudExpr.Parsed than = HudExpr.parse(inner != null ? inner : rule.than.strip());
					if (than.error() == null) limit = "{" + HudExpr.plain(than.node()) + "}";
				}
				if (limit == null) limit = HudFormat.thresholdText(value.kind(), rule.value);
				out.add(own + (rule.when == ColorRule.When.UNDER ? " < " : " > ") + limit + " &" + rule.color);
			}
		}
		if (readyRule) out.addAll(preset(value));
		return out;
	}
}
