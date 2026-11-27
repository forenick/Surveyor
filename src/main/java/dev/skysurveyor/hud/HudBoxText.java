package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.hud.HudExpr.Node;
import dev.skysurveyor.hud.HudExpr.Val;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudLayout.ShowAs;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.DropList;

public final class HudBoxText {
	// anything in { }, HudExpr reads it
	private static final Pattern TAG = Pattern.compile("\\{([^{}]*)}");
	// &k left out on purpose
	private static final String STYLES = "lonm";
	// display choice, 3 HUD refreshes
	private static final long WAIT_STEP_MILLIS = 600;
	// font skips unknown § codes. between = measured, not drawn
	static final String HIDE_START = "§";
	static final String HIDE_END = "§";
	private static final Pattern HIDDEN = Pattern.compile(Pattern.quote(HIDE_START) + ".*?" + Pattern.quote(HIDE_END));
	// label already says /h. \b for w/helmet
	private static final Pattern SAYS_RATE = Pattern.compile("(?i)(/\\s*h(r|our)?\\b|per hour|hourly)");
	private static final String PER_HOUR = "/h";
	// items per source, display choice
	private static final int SUB_ITEMS = 2;

	private HudBoxText() {
	}

	// keeps the hidden dots
	public static List<String> lines(Box box, HudData data, boolean example) {
		List<String> lines = new ArrayList<>();
		for (HudRow row : rows(box, data, example)) {
			if (row.text() != null) lines.add(row.text());
		}
		return lines;
	}

	// keeps the hidden dots
	public static List<HudRow> rows(Box box, HudData data, boolean example) {
		List<HudRow> rows = new ArrayList<>();
		if (box.showTitle) {
			String style = box.titleBoldOrDefault() ? "§l" : "";
			String color = titleColor(box, data, example);
			String title = tags(box.title, data, example, color, style).strip();
			if (!title.isEmpty()) rows.add(HudRow.text("§" + color + style + title));
		}
		for (Line line : box.lines) {
			if (!line.shown) continue;
			if (line.hudValue() != null && line.hudValue().multiRow()) {
				rows.addAll(dropRows(line, data, example, !rows.isEmpty()));
				continue;
			}
			HudRow row = row(box, line, data, example);
			if (row != null) rows.add(row);
		}
		return rows;
	}

	// no data = the text line, so - still shows
	static HudRow row(Box box, Line line, HudData data, boolean example) {
		String text = rawLine(box, line, data, example);
		if (text == null) return null;
		ShowAs as = line.showAs();
		HudGraph graph = as == ShowAs.TEXT ? null : graph(box, line, data, example);
		if (graph == null) return HudRow.text(text);
		if (as == ShowAs.BOTH) return new HudRow(text, graph);
		String name = line.advancedMode() ? "" : graphLabel(line.range() == Range.SHAFT && !data.inShaft() ? lastShaft(line.prefix) : line.prefix);
		String label = "§" + (line.labelColor != null ? line.labelColor : box.labelColor) + (box.labelBold ? "§l" : "");
		return new HudRow(name.isEmpty() ? null : label + name, graph);
	}

	// rule above unless its the boxes 1st row
	static List<HudRow> dropRows(Line line, HudData data, boolean example, boolean rule) {
		List<HudRow> rows = dropRows(line, data, example);
		if (rule) rows.set(0, rows.getFirst().withRule());
		return rows;
	}

	private static List<HudRow> dropRows(Line line, HudData data, boolean example) {
		Range range = line.range();
		if (line.dash() && !data.inShaft() && HudValue.DROPS.followsShaft(range)) return note(HudFormat.MISSING);
		DropList list = data.drops(range, example);
		// worked out on the io thread
		if (list == null && range == Range.ALL_TIME && !example) return note(waiting(data.now()));
		if (list == null) list = DropList.NONE;
		if (list.noPrices()) {
			String none = ToastText.NO_PRICES;
			return note(Character.toUpperCase(none.charAt(0)) + none.substring(1));
		}
		int most = line.rowsOrDefault();
		List<HudRow> rows = new ArrayList<>();
		boolean amounts = !Boolean.TRUE.equals(line.hideAmounts);
		HudLayout.DropsView view = line.dropsView();
		if (view == HudLayout.DropsView.ITEMS) {
			for (DropList.Row row : list.shown(most)) {
				rows.add(HudRow.columns(dropText(row, amounts), CoinBreakdown.coins(row.coins()), false));
			}
		} else {
			for (DropList.Source source : list.shownSources(most)) {
				DropList.Row row = source.row();
				rows.add(HudRow.columns(dropText(row, amounts), CoinBreakdown.coins(row.coins()), false));
				// a single item says nothing new
				if (view != HudLayout.DropsView.BOTH || source.items().size() < 2) continue;
				for (DropList.Row item : source.items().subList(0, Math.min(SUB_ITEMS, source.items().size()))) {
					rows.add(HudRow.sub(dropText(item, amounts), "§7" + Coins.signed(item.coins())));
				}
			}
		}
		// only w/ all rows, or when its all there is
		if (list.unpriced() > 0 && (most == 0 || list.items().isEmpty())) {
			rows.add(HudRow.columns("§7" + CoinBreakdown.unpricedText(list.unpriced(), list.ironman()), "", false));
		}
		if (!Boolean.TRUE.equals(line.hideSpent)) {
			for (DropList.Row row : list.spent()) {
				rows.add(HudRow.columns(dropText(row, amounts), CoinBreakdown.coins(row.coins()), false));
			}
		}
		return rows.isEmpty() ? note("No drops yet") : rows;
	}

	private static List<HudRow> note(String text) {
		return new ArrayList<>(List.of(HudRow.columns("§7" + text, "", false)));
	}

	// like hypixel: rarity, gem icon, name, §8x amount
	static String dropText(DropList.Row row, boolean amounts) {
		String amount = amounts ? DropList.amount(row) : "";
		boolean item = row.kind() != DropList.Kind.SOURCE && row.kind() != DropList.Kind.MORE;
		// rarity now, item list may load later
		if (item) return ItemNames.current().styled(row.name(), amount);
		return "§" + row.color() + row.name() + ItemNames.amount(amount);
	}

	static String dropsName(Line line) {
		int rows = line.rowsOrDefault();
		return "Drops, " + (rows == 0 ? "all" : String.valueOf(rows)) + " rows";
	}

	static String dropsText(DropList list) {
		if (list == null || list.noPrices()) return HudFormat.MISSING;
		int items = list.items().size();
		return items + (items == 1 ? " item" : " items");
	}

	// "Pity: " -> "Pity"
	static String graphLabel(String prefix) {
		String name = prefix == null ? "" : prefix.strip();
		return name.endsWith(":") ? name.substring(0, name.length() - 1).strip() : name;
	}

	// null = nothing to draw right now
	static HudGraph graph(Box box, Line line, HudData data, boolean example) {
		HudValue value = line.hudValue();
		if (value == null || line.dash() && !data.inShaft() && value.followsShaft(line.range())) return null;
		return switch (value.graph(line.range())) {
			case BAR -> {
				Object now = value(value, line.range(), line.target(), data, example);
				Long max = data.max(value, example);
				if (!(now instanceof Number n) || max == null || max <= 0) yield null;
				yield new HudGraph.Bar(n.doubleValue() / max, HudLayout.argb(line.valueColor != null ? line.valueColor : box.valueColor));
			}
			case CHART -> {
				Object avg = value(value, line.range(), line.target(), data, example);
				yield spawnChart(data.recentSpawns(example), avg instanceof Number n ? n.doubleValue() : null, ShaftNames.defaults());
			}
			case LINE -> {
				List<Double> points = data.efficiencyTrack(example);
				yield points.size() < 2 ? null
						: HudGraph.Line.of(points, data.usualEfficiency(example), HudLayout.argb(line.valueColor != null ? line.valueColor : box.valueColor));
			}
			case NONE -> null;
		};
	}

	// longest shaft or avg, whichever is bigger
	static HudGraph.Chart spawnChart(List<HudData.Spawn> spawns, Double average, ShaftNames names) {
		if (spawns.isEmpty()) return null;
		double scale = average == null ? 0 : average;
		for (HudData.Spawn spawn : spawns) scale = Math.max(scale, spawn.millis());
		if (scale <= 0) scale = 1;
		List<HudGraph.Column> columns = new ArrayList<>();
		for (HudData.Spawn spawn : spawns) {
			int rgb = spawn.type() == null ? 0xFFFFFF : names.rgb(spawn.type());
			columns.add(new HudGraph.Column(spawn.millis() / scale, 0xFF000000 | rgb));
		}
		return new HudGraph.Chart(columns, average == null ? null : average / scale);
	}

	// SHAFT_COLOR = the shafts colour, yellow w/o one
	public static String titleColor(Box box, HudData data, boolean example) {
		String color = box.titleColorOrDefault();
		if (!HudLayout.SHAFT_COLOR.equals(color)) return color;
		Character shaft = data.shaftColor() != null ? data.shaftColor() : example ? HudData.EXAMPLE.shaftColor() : null;
		return shaft != null ? String.valueOf(shaft) : "e";
	}

	// no hidden dots, labels would draw them
	public static String line(Box box, Line line, HudData data, boolean example) {
		String raw = rawLine(box, line, data, example);
		return raw == null ? null : visible(raw);
	}

	private static String rawLine(Box box, Line line, HudData data, boolean example) {
		HudValue hudValue = line.hudValue();
		if (hudValue == null) return null;
		if (hudValue.multiRow()) return "§7" + dropsName(line);
		// off = no line, a box w/ none takes no room (nick, 2026-11-22)
		if (hudValue.consumable() != null && !example && data.get(hudValue, null) == null) return null;
		if (line.advancedMode()) {
			HudExpr.Values values = values(data, example);
			Map<String, String> colors = HudRules.colors(HudRules.parse(line.advancedRules()), values);
			return free(box, line, line.advancedText(box), colors, data, example);
		}
		String label = "§" + (line.labelColor != null ? line.labelColor : box.labelColor) + (box.labelBold ? "§l" : "");
		String color = line.valueColor != null ? line.valueColor : box.valueColor;
		String styles = box.valueBold ? "§l" : "";
		Range range = line.range();
		String prefix = range == Range.SHAFT && !data.inShaft() ? lastShaft(line.prefix) : line.prefix;
		String text = line.dash() && !data.inShaft() && hudValue.followsShaft(range) ? HudFormat.MISSING
				: oncePerHour(line.prefix, text(hudValue, range, line.target(), line.format, data, example));
		return label + prefix + "§" + color + styles + paint(text, color, styles);
	}

	static HudExpr.Values values(HudData data, boolean example) {
		return example ? data::getOrExample : data::get;
	}

	// shaft range shows the last one outside, labels say so
	static String lastShaft(String text) {
		return text.replace("This shaft", "Last shaft").replace("this shaft", "last shaft").replace("THIS SHAFT", "LAST SHAFT");
	}

	// styles kept, a colour code resets them
	static String paint(String text, String color, String styles) {
		if (text.indexOf(HudData.PAINT) < 0 && text.indexOf(HudData.BACK) < 0) return text;
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == HudData.PAINT && i + 1 < text.length()) {
				out.append('§').append(text.charAt(++i)).append(styles);
			} else if (c == HudData.BACK) {
				out.append('§').append(color).append(styles);
			} else {
				out.append(c);
			}
		}
		return out.toString();
	}

	static String tags(String text, HudData data, boolean example) {
		return tags(text, data, example, "e", "§l");
	}

	// color + styles = the titles. missing = left out
	static String tags(String text, HudData data, boolean example, String color, String styles) {
		if (text.indexOf('{') < 0) return text;
		Matcher matcher = TAG.matcher(text);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			HudExpr.Parsed parsed = parseTag(matcher.group(1));
			String replacement = matcher.group();
			if (parsed.error() == null && parsed.node() instanceof Val v && value(v.value(), v.range(), v.target(), data, example) == null) {
				replacement = "";
			} else if (parsed.error() == null) {
				String shown = tagText(parsed.node(), data, example);
				if (shown != null) replacement = shown.equals(HudFormat.MISSING) ? "" : paint(shown, color, styles);
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	// null = cant show (text in maths)
	static String tagText(Node node, HudData data, boolean example) {
		if (node instanceof Val v && (v.format() == null || HudFormat.names(v.value().kind()).contains(v.format()))) {
			return text(v.value(), v.range(), v.target(), HudExpr.index(v.value().kind(), v.format()), data, example);
		}
		try {
			HudExpr.Result r = HudExpr.eval(node, values(data, example));
			if (r.type() != HudExpr.Type.TEXT && r.value() == null && r.waiting()) return waiting(data.now());
			return HudExpr.text(r);
		} catch (HudExpr.Error e) {
			return null;
		}
	}

	// &r = label colour, &v = value colour, unknown tags stay as typed
	static String free(Box box, Line line, String text, Map<String, String> colors, HudData data, boolean example) {
		boolean shaftTag = false;
		String color = box.labelColor;
		StringBuilder styles = new StringBuilder(box.labelBold ? "l" : "");
		StringBuilder out = new StringBuilder("§").append(color).append(codes(styles));
		Matcher tags = TAG.matcher(text);
		int i = 0;
		int since = 0;
		while (i < text.length()) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length()) {
				// && and &{ = a plain & or {
				if (escaped(text.charAt(i + 1))) {
					out.append(text.charAt(i + 1));
					i += 2;
					continue;
				}
				char code = Character.toLowerCase(text.charAt(i + 1));
				if (HudLayout.COLOR_CODES.indexOf(code) >= 0 || code == 'r' || code == 'v') {
					// colour resets styles, like vanilla
					styles.setLength(0);
					if (code == 'r') {
						color = box.labelColor;
						if (box.labelBold) styles.append('l');
						out.append("§r");
					} else if (code == 'v') {
						color = box.valueColor;
						if (box.valueBold) styles.append('l');
					} else {
						color = String.valueOf(code);
					}
					out.append('§').append(color).append(codes(styles));
					i += 2;
					continue;
				}
				if (STYLES.indexOf(code) >= 0) {
					if (styles.indexOf(String.valueOf(code)) < 0) styles.append(code);
					out.append('§').append(code);
					i += 2;
					continue;
				}
			}
			if (c == '{' && tags.region(i, text.length()).lookingAt()) {
				HudExpr.Parsed parsed = parseTag(tags.group(1));
				if (parsed.error() == null && !shaftTag) {
					shaftTag = HudExpr.values(parsed.node()).stream().anyMatch(v -> v.range() == Range.SHAFT);
				}
				// outside a shaft w/ Outside "-"
				boolean dash = parsed.error() == null && line.dash() && !data.inShaft() && followsShaft(parsed.node());
				String shown = parsed.error() != null ? null : dash ? HudFormat.MISSING : tagText(parsed.node(), data, example);
				if (shown != null) {
					shown = oncePerHour(text.substring(since, i), shown);
					// a - gets no rule colour
					String rule = shown.equals(HudFormat.MISSING) ? null : colors.get(HudExpr.key(parsed.node()));
					if (rule != null) out.append('§').append(rule).append(codes(styles));
					out.append(paint(shown, rule != null ? rule : color, codes(styles)));
					if (rule != null) out.append('§').append(color).append(codes(styles));
					i = tags.end();
					since = i;
					continue;
				}
			}
			out.append(c);
			i++;
		}
		// values never say this shaft, safe to swap
		return shaftTag && !data.inShaft() ? lastShaft(out.toString()) : out.toString();
	}

	static boolean followsShaft(Node node) {
		return HudExpr.values(node).stream().anyMatch(v -> v.value().followsShaft(v.range()));
	}

	// no value = not a tag, old text had {5}
	private static HudExpr.Parsed parseTag(String inner) {
		HudExpr.Parsed parsed = HudExpr.parse(inner);
		return parsed.error() == null && HudExpr.literal(parsed.node()) ? new HudExpr.Parsed(null, "no value in it") : parsed;
	}

	// text in maths counts too
	public static List<String> problems(String text) {
		List<String> problems = new ArrayList<>();
		Matcher tags = TAG.matcher(text);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length() && escaped(text.charAt(i + 1))) {
				i++;
			} else if (c == '{') {
				if (!tags.region(i, text.length()).lookingAt()) {
					problems.add(text.substring(i, Math.min(text.length(), i + 12)) + ": a { has no }");
					break;
				}
				HudExpr.Parsed parsed = parseTag(tags.group(1));
				String error = parsed.error();
				if (error == null) {
					try {
						HudExpr.eval(parsed.node(), HudExpr.NONE);
					} catch (HudExpr.Error e) {
						error = e.getMessage();
					}
				}
				if (error != null) problems.add(tags.group() + ": " + error);
				i = tags.end() - 1;
			}
		}
		return problems;
	}

	// tags as written, each once: values + maths a rule can colour
	public static List<String> numberTags(String text) {
		List<String> found = new ArrayList<>();
		for (Node node : nodes(text)) {
			try {
				if (HudExpr.eval(node, HudExpr.NONE).type() == HudExpr.Type.TEXT) continue;
			} catch (HudExpr.Error e) {
				continue;
			}
			String plain = HudExpr.plain(node);
			if (!found.contains(plain)) found.add(plain);
		}
		return found;
	}

	static List<Node> nodes(String text) {
		List<Node> nodes = new ArrayList<>();
		Matcher tags = TAG.matcher(text);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '&' && i + 1 < text.length() && escaped(text.charAt(i + 1))) {
				i++;
			} else if (c == '{' && tags.region(i, text.length()).lookingAt()) {
				HudExpr.Parsed parsed = parseTag(tags.group(1));
				if (parsed.error() == null) nodes.add(parsed.node());
				i = tags.end() - 1;
			}
		}
		return nodes;
	}

	private static boolean escaped(char next) {
		return next == '&' || next == '{';
	}

	// whats in a whole "{...}", null = not one tag
	static String unbrace(String text) {
		Matcher matcher = TAG.matcher(text);
		return matcher.matches() ? matcher.group(1) : null;
	}

	// a tag an old colour rule on own can compare against
	public static boolean isLimitTag(String text, HudValue own) {
		String inner = unbrace(text.strip());
		if (inner == null) return false;
		HudExpr.Parsed parsed = HudExpr.parse(inner);
		return parsed.error() == null && parsed.node() instanceof Val v && v.value().hasNumber() && HudColors.comparable(own, v.value());
	}

	public static String tag(HudValue value, Range range, int format) {
		return tag(value, range, null, format);
	}

	// order: target, range, format
	public static String tag(HudValue value, Range range, String target, int format) {
		StringBuilder tag = new StringBuilder("{").append(value.tag());
		if (value.acceptsTarget(target) && (value.pinsTarget() || !target.equals(value.defaultTarget()))) tag.append(':').append(target);
		if (range != null && range != value.defaultRange()) tag.append(':').append(range.tagName());
		if (format > 0 && format < HudFormat.names(value.kind()).size()) {
			tag.append(':').append(HudFormat.names(value.kind()).get(format));
		}
		return tag.append('}').toString();
	}

	// pre 0.23 custom text: range of the first tag w/ the lines own value
	public static Range ruleRange(Line line) {
		HudValue value = line.hudValue();
		if (!line.advanced || line.text == null || value == null) return line.range();
		for (Node node : nodes(line.text)) {
			if (node instanceof Val v && v.value() == value) return v.range();
		}
		return line.range();
	}

	// own colours as codes, box colours stay live (&v)
	public static String basicText(Box box, Line line) {
		HudValue value = line.hudValue();
		String label = line.labelColor == null ? "" : "&" + line.labelColor + (box.labelBold ? "&l" : "");
		String valueCode = line.valueColor == null ? "&v" : "&" + line.valueColor + (box.valueBold ? "&l" : "");
		String prefix = line.prefix.replace("&", "&&").replace("{", "&{");
		return label + prefix + valueCode + tag(value, line.range(), line.target(), line.format);
	}

	private static String codes(CharSequence styles) {
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < styles.length(); i++) out.append('§').append(styles.charAt(i));
		return out.toString();
	}

	static String text(HudValue value, Range range, int format, HudData data, boolean example) {
		return text(value, range, null, format, data, example);
	}

	static String text(HudValue value, Range range, String target, int format, HudData data, boolean example) {
		if (value.multiRow()) return dropsText(data.drops(range, example));
		Object v = value(value, range, target, data, example);
		if (v == null && rate(value, range)) return waiting(data.now());
		String shown = formatted(value, range, format, v);
		Long max = v == null ? null : data.max(value, example);
		// 1,498/2,000, rules + maths use the 1st
		return max == null ? shown : shown + "/" + HudFormat.format(value.kind(), format, max);
	}

	// rates wait for 1 saved shaft + JournalStats.MIN_RATE_MILLIS
	static boolean rate(HudValue value, Range range) {
		return value == HudValue.SHAFTS_PER_HOUR || (range == Range.PER_HOUR && value.hasRange(range));
	}

	// dots hidden, not dropped, so the width stays
	static String waiting(long now) {
		int dots = 1 + (int) (now / WAIT_STEP_MILLIS % 3);
		return dots == 3 ? "..." : ".".repeat(dots) + HIDE_START + ".".repeat(3 - dots) + HIDE_END;
	}

	static String visible(String line) {
		return line.contains(HIDE_START) ? HIDDEN.matcher(line).replaceAll("") : line;
	}

	// drawn, hidden, drawn, ... each w/ the § codes active at its start
	public static List<String> pieces(String line) {
		List<String> pieces = new ArrayList<>();
		int pos = 0;
		String codes = "";
		while (true) {
			int start = line.indexOf(HIDE_START, pos);
			int end = start < 0 ? -1 : line.indexOf(HIDE_END, start);
			if (end < 0) {
				pieces.add(codes + line.substring(pos));
				return pieces;
			}
			pieces.add(codes + line.substring(pos, start));
			codes = activeCodes(line, start);
			pieces.add(codes + line.substring(start + HIDE_START.length(), end));
			pos = end + HIDE_END.length();
			codes = activeCodes(line, pos);
		}
	}

	private static String activeCodes(String line, int upTo) {
		String color = "";
		StringBuilder styles = new StringBuilder();
		for (int i = 0; i + 1 < upTo; i++) {
			if (line.charAt(i) != '§') continue;
			char code = Character.toLowerCase(line.charAt(++i));
			if (HudLayout.COLOR_CODES.indexOf(code) >= 0 || code == 'r') {
				color = "§" + code;
				styles.setLength(0);
			} else if ("klmno".indexOf(code) >= 0) {
				styles.append('§').append(code);
			}
		}
		return color + styles;
	}

	private static Object value(HudValue value, Range range, String target, HudData data, boolean example) {
		return example ? data.getOrExample(value, range, target) : data.get(value, range, target);
	}

	private static String formatted(HudValue value, Range range, int format, Object v) {
		String text = HudFormat.format(value.kind(), format, v);
		// gains +, like the entry title + key corpses
		if (value.signed() && v instanceof Number n && Math.round(n.doubleValue()) > 0) text = "+" + text;
		return v == null ? text : text + suffix(value, range);
	}

	public static String suffix(HudValue value, Range range) {
		return range == Range.PER_HOUR ? PER_HOUR : value.unit();
	}

	// as drawn after this label, /h shows once
	public static String suffix(HudValue value, Range range, String before) {
		String suffix = suffix(value, range);
		return suffix.equals(PER_HOUR) && saysRate(before) ? "" : suffix;
	}

	static boolean saysRate(String text) {
		return text != null && SAYS_RATE.matcher(text).find();
	}

	// "Coins/h: 46.8M", not 46.8M/h
	static String oncePerHour(String before, String shown) {
		return shown.endsWith(PER_HOUR) && saysRate(before) ? shown.substring(0, shown.length() - PER_HOUR.length()) : shown;
	}
}
