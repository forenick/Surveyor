package dev.skysurveyor.hud;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.hud.HudValue.Range;

// what a click in the value list types at the cursor
public final class HudTyping {
	public record Edit(String text, int cursor) {
	}

	private HudTyping() {
	}

	// inside { } = maths, so no braces
	public static boolean insideBraces(String text, int cursor) {
		int open = text.lastIndexOf('{', cursor - 1);
		int close = text.lastIndexOf('}', cursor - 1);
		return open > close && (open == 0 || text.charAt(open - 1) != '&');
	}

	// tag w/o braces f.e. "coins:all_time"
	public static Edit type(String text, int cursor, String tag) {
		String typed = insideBraces(text, cursor) ? tag : "{" + tag + "}";
		return insert(text, cursor, typed);
	}

	public static Edit insert(String text, int cursor, String typed) {
		int at = Math.max(0, Math.min(cursor, text.length()));
		return new Edit(text.substring(0, at) + typed + text.substring(at), at + typed.length());
	}

	public static Edit option(String text, int cursor, HudValue value, String option) {
		int at = Math.max(0, Math.min(cursor, text.length()));
		String before = text.substring(0, at);
		Matcher m = Pattern.compile("(?<![a-z_])(" + Pattern.quote(value.tag()) + "|" + Pattern.quote(value.id())
				+ ")((?::[a-z_]+)*)(}?)$").matcher(before);
		if (!m.find()) return type(text, cursor, value.tag() + ":" + option);
		// range first, then the format
		String range = isRange(value, option) ? option : null;
		String format = range == null ? option : null;
		for (String o : m.group(2).split(":")) {
			if (o.isEmpty()) continue;
			if (isRange(value, o)) {
				if (range == null) range = o;
			} else if (format == null) {
				format = o;
			}
		}
		StringBuilder tag = new StringBuilder(m.group(1));
		if (range != null) tag.append(':').append(range);
		if (format != null) tag.append(':').append(format);
		tag.append(m.group(3));
		String out = before.substring(0, m.start()) + tag + text.substring(at);
		return new Edit(out, m.start() + tag.length());
	}

	private static boolean isRange(HudValue value, String option) {
		return value.hasRange(Range.byTagName(option));
	}
}
