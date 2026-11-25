package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import dev.skysurveyor.hud.HudValue.Kind;
import dev.skysurveyor.hud.HudValue.Range;

// times count in secs
public final class HudExpr {
	public enum Type {
		TIME, COINS, COLD, INTEGER, DECIMAL, NUMBER, TEXT
	}

	public static final class Error extends RuntimeException {
		Error(String message) {
			super(message, null, false, false);
		}
	}

	public sealed interface Node permits Num, Val, Group, Neg, Op {
	}

	// minutes = the secs if its "2m" next to a time. decimals = as typed, for =
	public record Num(double value, Type type, Integer decimals, Double minutes) implements Node {
	}

	// target = tag name, f.e. "jasper", null for values w/o one
	public record Val(HudValue value, Range range, String format, String target) implements Node {
		public Val(HudValue value, Range range, String format) {
			this(value, range, format, value.defaultTarget());
		}
	}

	public record Group(Node inner, String format) implements Node {
	}

	public record Neg(Node inner) implements Node {
	}

	public record Op(char op, Node a, Node b) implements Node {
	}

	// null value = missing. waiting = a rate thats not ready yet
	public record Result(Double value, Type type, String unit, String format, boolean waiting, String text, Double minutes) {
		Result with(Double v, Type t, String u) {
			return new Result(v, t, u, null, waiting, null, null);
		}
	}

	public interface Values {
		Object get(HudValue value, Range range, String target);
	}

	public record Parsed(Node node, String error) {
	}

	public static final Values NONE = (value, range, target) -> null;
	private static final List<String> NUMBER_FORMATS = Stream
			.concat(HudFormat.names(Kind.INTEGER).stream(), HudFormat.names(Kind.DECIMAL).stream()).toList();
	private static final Pattern CLOCK = Pattern.compile("(\\d+):(\\d{2})(?!\\d)");
	private static final Pattern MIN_SEC = Pattern.compile("(\\d+)m\\s*(\\d+)s(?![a-z_])", Pattern.CASE_INSENSITIVE);
	private static final Pattern SECS = Pattern.compile("(\\d+)s(?![a-z_])", Pattern.CASE_INSENSITIVE);
	private static final Pattern NUMBER = Pattern.compile("(\\d+(?:[.,]\\d+)*)([kmb])?(?![a-z_])\\s*❄?", Pattern.CASE_INSENSITIVE);
	private static final Pattern GROUPED = Pattern.compile("\\d{1,3}(,\\d{3})+(\\.\\d+)?");
	private static final Pattern NAME = Pattern.compile("([a-z_]+)((?::[a-z_]+)*)", Pattern.CASE_INSENSITIVE);
	private static final Pattern OPTIONS = Pattern.compile("(?::[a-z_]+)+", Pattern.CASE_INSENSITIVE);
	// display choice, not a game number. real tags are ~25 chars
	// parse + eval recurse, these keep a hand edit or share code far from a stack overflow
	static final int MAX_CHARS = 500;
	static final int MAX_TOKENS = 200;
	static final int MAX_DEPTH = 30;
	// guesses for ranges, f.e. {coins:hourly}
	private static final Map<String, String> RANGE_GUESS = Map.ofEntries(Map.entry("hourly", "per_hour"),
			Map.entry("hour", "per_hour"), Map.entry("h", "per_hour"), Map.entry("perhour", "per_hour"),
			Map.entry("alltime", "all_time"), Map.entry("total", "all_time"), Map.entry("all", "all_time"),
			Map.entry("ever", "all_time"), Map.entry("now", "shaft"), Map.entry("this", "shaft"),
			Map.entry("current", "shaft"), Map.entry("last", "shaft"), Map.entry("sess", "session"));
	// parsed once per text, the HUD redraws every 4 ticks. synced, hud.json repair parses on the IO thread
	private static final Map<String, Parsed> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Parsed> eldest) {
			return size() > 512;
		}
	};

	private HudExpr() {
	}

	public static Parsed parse(String src) {
		String key = cacheKey(src);
		synchronized (CACHE) {
			Parsed parsed = CACHE.get(key);
			if (parsed != null) return parsed;
		}
		Parsed parsed;
		try {
			parsed = new Parsed(new Parser(src).parse(), null);
		} catch (Error e) {
			parsed = new Parsed(null, e.getMessage());
		}
		synchronized (CACHE) {
			CACHE.put(key, parsed);
		}
		return parsed;
	}

	// bare {pb} = 1st pb in the list, which can change, so its part of the key
	static String cacheKey(String src) {
		return HudValue.PERSONAL_BEST.defaultTarget() + "\n" + src;
	}

	public static Node parseOrThrow(String src) {
		Parsed parsed = parse(src);
		if (parsed.error() != null) throw new Error(parsed.error());
		return parsed.node();
	}

	private record Token(char kind, int pos, Num num, String name, List<String> options, char op) {
	}

	private static final class Parser {
		private final String src;
		private final List<Token> tokens = new ArrayList<>();
		private int p;
		private int depth;
		// format + where it was, only the last token may have one
		private record Format(String name, int at, Node site) {
		}

		private final List<Format> formats = new ArrayList<>();

		Parser(String src) {
			if (src.length() > MAX_CHARS) throw new Error("too long");
			this.src = src;
			tokenize();
		}

		private void tokenize() {
			int i = 0;
			while (i < src.length()) {
				if (tokens.size() > MAX_TOKENS) throw new Error("too long");
				char c = src.charAt(i);
				if (Character.isWhitespace(c)) {
					i++;
					continue;
				}
				Matcher m;
				if (Character.isDigit(c) || (c == '.' && i + 1 < src.length() && Character.isDigit(src.charAt(i + 1)))) {
					Num num = null;
					int end;
					if ((m = at(CLOCK, i)) != null) {
						num = new Num(Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2)), Type.TIME, null, null);
					} else if ((m = at(MIN_SEC, i)) != null) {
						num = new Num(Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2)), Type.TIME, null, null);
					} else if ((m = at(SECS, i)) != null) {
						num = new Num(Long.parseLong(m.group(1)), Type.TIME, null, null);
					} else if ((m = at(NUMBER, i)) != null) {
						num = number(m.group(1), m.group(2));
					}
					if (num == null) throw new Error("can't read \"" + c + "\"");
					end = m.end();
					List<String> options = List.of();
					Matcher o = at(OPTIONS, end);
					if (o != null) {
						options = options(o.group());
						end = o.end();
					}
					tokens.add(new Token('n', i, num, null, options, ' '));
					i = end;
					continue;
				}
				if ((m = at(NAME, i)) != null) {
					tokens.add(new Token('v', i, null, m.group(1).toLowerCase(Locale.ROOT), options(m.group(2)), ' '));
					i = m.end();
					continue;
				}
				if ("+-*/".indexOf(c) >= 0) {
					tokens.add(new Token('o', i, null, null, List.of(), c));
					i++;
					continue;
				}
				if (c == '(') {
					tokens.add(new Token('(', i, null, null, List.of(), c));
					i++;
					continue;
				}
				if (c == ')') {
					Matcher o = at(OPTIONS, i + 1);
					tokens.add(new Token(')', i, null, null, o == null ? List.of() : options(o.group()), c));
					i = o == null ? i + 1 : o.end();
					continue;
				}
				throw new Error("can't read \"" + c + "\"");
			}
			if (tokens.size() > MAX_TOKENS) throw new Error("too long");
		}

		private Matcher at(Pattern pattern, int i) {
			Matcher m = pattern.matcher(src).region(i, src.length());
			return m.lookingAt() ? m : null;
		}

		private static List<String> options(String text) {
			List<String> out = new ArrayList<>();
			for (String o : text.split(":")) {
				if (!o.isEmpty()) out.add(o.toLowerCase(Locale.ROOT));
			}
			return out;
		}

		Node parse() {
			if (tokens.isEmpty()) throw new Error("empty");
			Node node = sum();
			if (p < tokens.size()) throw new Error("something's missing before " + shown(tokens.get(p)));
			for (Format f : formats) {
				if (f.at() != tokens.size() - 1) throw new Error("formats go at the end");
			}
			if (formats.isEmpty()) return node;
			String format = formats.getFirst().name();
			Node site = formats.getFirst().site();
			// a lone value keeps its own format
			if (node == site && node instanceof Val v) return new Val(v.value(), v.range(), format, v.target());
			if (node == site && node instanceof Group g) return new Group(g.inner(), format);
			return new Group(node, format);
		}

		private String shown(Token t) {
			return switch (t.kind()) {
				case 'v' -> t.name();
				case 'n' -> "a number";
				default -> String.valueOf(t.op());
			};
		}

		private Node sum() {
			Node a = product();
			while (p < tokens.size() && tokens.get(p).kind() == 'o' && "+-".indexOf(tokens.get(p).op()) >= 0) {
				char op = tokens.get(p++).op();
				a = new Op(op, a, product());
			}
			return a;
		}

		private Node product() {
			Node a = unary();
			while (p < tokens.size() && tokens.get(p).kind() == 'o' && "*/".indexOf(tokens.get(p).op()) >= 0) {
				char op = tokens.get(p++).op();
				a = new Op(op, a, unary());
			}
			return a;
		}

		private Node unary() {
			if (p < tokens.size() && tokens.get(p).kind() == 'o' && tokens.get(p).op() == '-') {
				p++;
				return new Neg(unary());
			}
			return primary();
		}

		private Node primary() {
			if (p >= tokens.size()) throw new Error("something's missing at the end");
			int index = p;
			Token t = tokens.get(p++);
			switch (t.kind()) {
				case 'n' -> {
					Node num = t.num();
					if (!t.options().isEmpty()) {
						String format = t.options().getLast();
						if (!NUMBER_FORMATS.contains(format) && !HudFormat.names(Kind.TIME).contains(format)) {
							throw new Error("no format called :" + format);
						}
						formats.add(new Format(format, index, num));
					}
					return num;
				}
				case 'v' -> {
					return value(t, index);
				}
				case '(' -> {
					if (++depth > MAX_DEPTH) throw new Error("too many ( inside each other");
					Node inner = sum();
					depth--;
					if (p >= tokens.size() || tokens.get(p).kind() != ')') throw new Error("a ( has no )");
					Token close = tokens.get(p++);
					Group group = new Group(inner, null);
					if (!close.options().isEmpty()) formats.add(new Format(close.options().getLast(), p - 1, group));
					return group;
				}
				default -> throw new Error("something's missing before " + shown(t));
			}
		}

		private Node value(Token t, int index) {
			HudValue value = HudValue.byTag(t.name());
			if (value == null) {
				String guess = near(t.name(), tags());
				throw new Error("not a value: " + t.name() + (guess != null ? ". Did you mean " + guess + "?" : ""));
			}
			Range range = value.defaultRange();
			String format = null;
			String target = value.defaultTarget();
			for (String o : t.options()) {
				Range r = Range.byTagName(o);
				if (value.hasRange(r)) {
					range = r;
				} else if (HudFormat.names(value.kind()).contains(o) || (value.hasNumber() && NUMBER_FORMATS.contains(o))) {
					format = o;
				} else if (value.acceptsTarget(o)) {
					target = o;
				} else {
					throw new Error(value.tag() + " has no :" + o + guessOption(value, o));
				}
			}
			Val val = new Val(value, range, null, target);
			if (format != null) {
				formats.add(new Format(format, index, val));
			}
			return val;
		}
	}

	private static Num number(String digits, String suffix) {
		String t = digits;
		if (GROUPED.matcher(t).matches()) t = t.replace(",", "");
		else if (t.indexOf(',') >= 0 && t.indexOf(',') == t.lastIndexOf(',') && t.indexOf('.') < 0) t = t.replace(',', '.');
		if (t.indexOf(',') >= 0 || t.indexOf('.') != t.lastIndexOf('.')) return null;
		double n;
		try {
			n = Double.parseDouble(t);
		} catch (NumberFormatException e) {
			return null;
		}
		if (suffix == null) {
			int dot = t.indexOf('.');
			return new Num(n, Type.NUMBER, dot < 0 ? 0 : t.length() - dot - 1, null);
		}
		String s = suffix.toLowerCase(Locale.ROOT);
		double unit = s.equals("k") ? 1e3 : s.equals("m") ? 1e6 : 1e9;
		return new Num(Math.round(n * unit * 1e6) / 1e6, Type.NUMBER, null, s.equals("m") ? n * 60 : null);
	}

	private static List<String> tags() {
		List<String> tags = new ArrayList<>();
		for (HudValue v : HudValue.values()) {
			if (v.isTag()) tags.add(v.tag());
		}
		return tags;
	}

	private static String guessOption(HudValue value, String option) {
		String guess = RANGE_GUESS.get(option);
		if (guess == null || !value.hasRange(Range.byTagName(guess))) {
			List<String> options = new ArrayList<>(value.targets());
			if (value.ranges().size() > 1) value.ranges().forEach(r -> options.add(r.tagName()));
			options.addAll(HudFormat.names(value.kind()));
			guess = near(option, options);
		}
		return guess == null ? "" : ". Did you mean :" + guess + "?";
	}

	// 2 typos at most, or the start of it
	static String near(String word, List<String> words) {
		String best = null;
		int bestDistance = 3;
		for (String w : words) {
			int d = w.startsWith(word) && word.length() >= 3 ? 1 : distance(word, w);
			if (d < bestDistance) {
				bestDistance = d;
				best = w;
			}
		}
		return best;
	}

	private static int distance(String a, String b) {
		int[] row = new int[b.length() + 1];
		for (int j = 0; j <= b.length(); j++) row[j] = j;
		for (int i = 1; i <= a.length(); i++) {
			int prev = row[0];
			row[0] = i;
			for (int j = 1; j <= b.length(); j++) {
				int t = row[j];
				row[j] = Math.min(Math.min(row[j] + 1, row[j - 1] + 1), prev + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
				prev = t;
			}
		}
		return row[b.length()];
	}

	public static Result eval(Node node, Values values) {
		return switch (node) {
			case Num n -> new Result(n.value(), n.type(), "", null, false, null, n.minutes());
			case Val v -> value(v, values);
			case Group g -> g.format() == null ? eval(g.inner(), values) : format(eval(g.inner(), values), g.format());
			case Neg n -> {
				Result r = eval(n.inner(), values);
				if (r.type() == Type.TEXT) throw new Error("no maths with text values");
				yield new Result(r.value() == null ? null : -r.value(), r.type(), r.unit(), null, r.waiting(), null, null);
			}
			case Op o -> op(o, values);
		};
	}

	private static Result value(Val v, Values values) {
		HudValue value = v.value();
		Object o = values.get(value, v.range(), v.target());
		String unit = HudBoxText.suffix(value, v.range());
		boolean waiting = o == null && HudBoxText.rate(value, v.range());
		Type type = type(value.kind());
		if (type == Type.TEXT) return new Result(null, type, "", v.format(), waiting, o == null ? null : o.toString(), null);
		Double number = o instanceof Number n ? n.doubleValue() : null;
		if (number != null && type == Type.TIME) number /= 1000;
		Result r = new Result(number, type, unit, null, waiting, null, null);
		return v.format() == null ? r : format(r, v.format());
	}

	static Type type(Kind kind) {
		return switch (kind) {
			case TIME -> Type.TIME;
			case INTEGER -> Type.INTEGER;
			case DECIMAL -> Type.DECIMAL;
			case COLD -> Type.COLD;
			case COINS -> Type.COINS;
			case TEXT -> Type.TEXT;
		};
	}

	static boolean plain(Type type) {
		return type == Type.INTEGER || type == Type.DECIMAL || type == Type.NUMBER;
	}

	// a 2m next to a time = 2 minutes
	static Result asTime(Result r, Result other) {
		if (r.minutes() == null || other.type() != Type.TIME) return r;
		return new Result(r.minutes(), Type.TIME, "", null, r.waiting(), null, null);
	}

	private static Result op(Op o, Values values) {
		Result a = eval(o.a(), values);
		Result b = eval(o.b(), values);
		if (a.type() == Type.TEXT || b.type() == Type.TEXT) throw new Error("no maths with text values");
		a = asTime(a, b);
		b = asTime(b, a);
		Double v = null;
		if (a.value() != null && b.value() != null) {
			double x = a.value();
			double y = b.value();
			v = switch (o.op()) {
				case '+' -> x + y;
				case '-' -> x - y;
				case '*' -> x * y;
				default -> y == 0 ? null : x / y;
			};
		}
		boolean waiting = a.waiting() || b.waiting();
		Result base = new Result(v, Type.NUMBER, "", null, waiting, null, null);
		Result r = switch (o.op()) {
			case '+', '-' -> {
				if (plain(a.type()) && plain(b.type())) yield base;
				if (plain(b.type())) yield base.with(v, a.type(), a.unit());
				if (plain(a.type())) yield base.with(v, b.type(), b.unit());
				if (a.type() == b.type()) yield base.with(v, a.type(), a.unit().equals(b.unit()) ? a.unit() : "");
				throw new Error("can't " + (o.op() == '+' ? "add " : "subtract ") + name(a.type()) + " and " + name(b.type()));
			}
			case '*' -> {
				if (plain(b.type())) yield base.with(v, a.type(), a.unit());
				if (plain(a.type())) yield base.with(v, b.type(), b.unit());
				yield base;
			}
			// same kind / same kind = a plain number
			default -> plain(b.type()) ? base.with(v, a.type(), a.unit()) : base;
		};
		// counts in maths show whole or w/ decimals, whatever comes out
		return plain(r.type()) ? r.with(v, Type.NUMBER, r.unit()) : r;
	}

	static String name(Type type) {
		return switch (type) {
			case TIME -> "a time";
			case COINS -> "coins";
			case COLD -> "cold";
			case TEXT -> "text";
			default -> "a number";
		};
	}

	// a number format on a time or coins = a plain number, times in secs
	static Result format(Result r, String format) {
		if (r.type() == Type.TEXT) throw new Error("text has no :" + format);
		List<String> own = switch (r.type()) {
			case TIME -> HudFormat.names(Kind.TIME);
			case COINS -> HudFormat.names(Kind.COINS);
			case INTEGER -> HudFormat.names(Kind.INTEGER);
			case DECIMAL -> HudFormat.names(Kind.DECIMAL);
			default -> List.of();
		};
		Type type = r.type();
		if (!own.contains(format)) {
			if (HudFormat.names(Kind.DECIMAL).contains(format)) type = Type.DECIMAL;
			else if (HudFormat.names(Kind.INTEGER).contains(format)) type = Type.INTEGER;
			else if (HudFormat.names(Kind.TIME).contains(format) && plain(r.type())) type = Type.TIME;
			else throw new Error(name(r.type()) + " has no :" + format);
		}
		return new Result(r.value(), type, r.unit(), format, r.waiting(), null, null);
	}

	// null value = "-", the caller shows waiting dots
	public static String text(Result r) {
		if (r.type() == Type.TEXT) return r.text() == null ? HudFormat.MISSING : r.text();
		if (r.value() == null || r.value().isNaN() || r.value().isInfinite()) return HudFormat.MISSING;
		double v = r.value();
		Type type = r.type();
		if (type == Type.NUMBER) type = Math.abs(v - Math.rint(v)) < 1e-9 ? Type.INTEGER : Type.DECIMAL;
		return switch (type) {
			case TIME -> HudFormat.format(Kind.TIME, index(Kind.TIME, r.format()), Math.round(v * 1000));
			case COINS -> HudFormat.format(Kind.COINS, index(Kind.COINS, r.format()), v) + r.unit();
			case COLD -> HudFormat.format(Kind.COLD, 0, Math.round(v));
			case INTEGER -> HudFormat.format(Kind.INTEGER, index(Kind.INTEGER, r.format()), Math.round(v)) + r.unit();
			default -> HudFormat.format(Kind.DECIMAL, index(Kind.DECIMAL, r.format()), v) + r.unit();
		};
	}

	static int index(Kind kind, String format) {
		return format == null ? 0 : Math.max(0, HudFormat.names(kind).indexOf(format));
	}

	// same key = same number, formats + brackets left out. what a rule matches on
	public static String key(Node node) {
		return switch (node) {
			case Num n -> Double.toString(n.value());
			case Val v -> v.value().tag() + (v.target() == null ? "" : ":" + v.target()) + (v.range() == null ? "" : ":" + v.range().tagName());
			case Group g -> key(g.inner());
			case Neg n -> "-" + key(n.inner());
			case Op o -> "(" + key(o.a()) + o.op() + key(o.b()) + ")";
		};
	}

	// as a player would type it, default range + formats left out
	public static String plain(Node node) {
		return switch (node) {
			case Num n -> n.type() == Type.TIME ? HudFormat.thresholdText(Kind.TIME, n.value()) : HudFormat.thresholdText(Kind.COINS, n.value());
			case Val v -> v.value().tag() + (v.target() == null || !v.value().pinsTarget() && v.target().equals(v.value().defaultTarget()) ? "" : ":" + v.target())
					+ (v.range() == null || v.range() == v.value().defaultRange() ? "" : ":" + v.range().tagName());
			case Group g -> plain(g.inner());
			case Neg n -> "-" + (bare(n.inner()) instanceof Op ? "(" + plain(n.inner()) + ")" : plain(n.inner()));
			case Op o -> {
				int p = precedence(o.op());
				boolean leftWrap = bare(o.a()) instanceof Op a && precedence(a.op()) < p;
				boolean rightWrap = bare(o.b()) instanceof Op b && (precedence(b.op()) < p || precedence(b.op()) == p && (o.op() == '-' || o.op() == '/'));
				yield (leftWrap ? "(" + plain(o.a()) + ")" : plain(o.a())) + " " + o.op() + " "
						+ (rightWrap ? "(" + plain(o.b()) + ")" : plain(o.b()));
			}
		};
	}

	private static Node bare(Node node) {
		return node instanceof Group g ? bare(g.inner()) : node;
	}

	private static int precedence(char op) {
		return op == '+' || op == '-' ? 1 : 2;
	}

	public static List<Val> values(Node node) {
		List<Val> out = new ArrayList<>();
		collect(node, out);
		return out;
	}

	private static void collect(Node node, List<Val> out) {
		switch (node) {
			case Val v -> out.add(v);
			case Group g -> collect(g.inner(), out);
			case Neg n -> collect(n.inner(), out);
			case Op o -> {
				collect(o.a(), out);
				collect(o.b(), out);
			}
			case Num n -> {
			}
		}
	}

	public static boolean literal(Node node) {
		return values(node).isEmpty();
	}

	// typed decimals of a number on its own, for =
	static Integer decimals(Node node) {
		return switch (node) {
			case Num n -> n.decimals();
			case Neg n -> decimals(n.inner());
			case Group g -> decimals(g.inner());
			default -> null;
		};
	}
}
