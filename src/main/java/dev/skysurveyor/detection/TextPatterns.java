package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.util.Resources;

public final class TextPatterns {
	private static final String RESOURCE = "/skysurveyor/patterns.txt";
	private static final Pattern FORMATTING = Pattern.compile("§(?:#[0-9A-Fa-f]{6}|.)");
	private static final Pattern LINE = Pattern.compile("^(\\S+)\\s+(.+)$");

	private static TextPatterns defaults;
	private static List<String> builtIn;
	private static List<String> overrides = List.of();

	// swapped whole by useOverrides, every parser holds this same object
	private volatile Map<String, List<Pattern>> patterns;

	private TextPatterns(Map<String, List<Pattern>> patterns) {
		this.patterns = patterns;
	}

	public static synchronized TextPatterns defaults() {
		if (defaults == null) defaults = new TextPatterns(read(builtIn()));
		return defaults;
	}

	private static synchronized List<String> builtIn() {
		if (builtIn == null) {
			List<String> lines = Resources.lines(RESOURCE);
			if (lines == null) throw new IllegalStateException("Missing " + RESOURCE);
			builtIn = List.copyOf(lines);
		}
		return builtIn;
	}

	static TextPatterns parse(List<String> lines) {
		return new TextPatterns(read(lines));
	}

	private static Map<String, List<Pattern>> read(List<String> lines) {
		Map<String, List<Pattern>> patterns = new HashMap<>();
		for (String line : lines) {
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			Matcher matcher = LINE.matcher(trimmed);
			if (!matcher.matches()) throw new IllegalArgumentException("Bad pattern line: " + line);
			patterns.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(Pattern.compile(matcher.group(2)));
		}
		return patterns;
	}

	// overrides 1st per key, so they win (first match per key wins). a bad line throws + changes nothing
	private static Map<String, List<Pattern>> merged(List<String> extra) {
		Map<String, List<Pattern>> merged = read(extra);
		Map<String, List<Pattern>> builtIn = read(builtIn());
		// code reads the built-in groups, a missing one throws on every match
		merged.forEach((key, list) -> {
			List<Pattern> own = builtIn.get(key);
			if (own == null) return;
			for (Pattern pattern : list) {
				for (String group : own.getFirst().namedGroups().keySet()) {
					if (!pattern.namedGroups().containsKey(group)) {
						throw new IllegalArgumentException(key + " needs the group (?<" + group + ">...)");
					}
				}
			}
		});
		builtIn.forEach((key, list) -> merged.computeIfAbsent(key, k -> new ArrayList<>()).addAll(list));
		return merged;
	}

	// patterns.txt in the data folder, f.e. a fix before the next release. returns how many patterns it has
	public static synchronized int useOverrides(List<String> lines) {
		Map<String, List<Pattern>> merged = merged(lines);
		defaults().patterns = merged;
		overrides = List.copyOf(lines.stream().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList());
		return overrides.size();
	}

	// for a recordings start line, so replay can use the same
	public static synchronized List<String> overrides() {
		return overrides;
	}

	// replay of a mid recording reload, never on defaults()
	public void replaceOverrides(List<String> lines) {
		if (this == defaults) throw new IllegalStateException("replay only");
		patterns = merged(lines);
	}

	// replay of a recording made w/ overrides, never touches defaults()
	public static TextPatterns builtInWith(List<String> lines) {
		return new TextPatterns(merged(lines));
	}

	// run text through clean() first
	public Matcher match(String key, String text) {
		List<Pattern> candidates = patterns.get(key);
		if (candidates == null) throw new IllegalArgumentException("No pattern named " + key);
		for (Pattern pattern : candidates) {
			Matcher matcher = pattern.matcher(text);
			if (matcher.find()) return matcher;
		}
		return null;
	}

	public boolean matches(String key, String text) {
		return match(key, text) != null;
	}

	// any pattern of the keys asked for
	public boolean matchesAny(String text, Predicate<String> keys) {
		for (Map.Entry<String, List<Pattern>> entry : patterns.entrySet()) {
			if (!keys.test(entry.getKey())) continue;
			for (Pattern pattern : entry.getValue()) {
				if (pattern.matcher(text).find()) return true;
			}
		}
		return false;
	}

	// also hypixels junk codes inside scoreboard words
	public static String strip(String legacy) {
		return FORMATTING.matcher(legacy).replaceAll("");
	}

	public static String clean(String legacy) {
		return strip(legacy).stripTrailing();
	}

	public static long number(String text) {
		return Long.parseLong(text.replace(",", ""));
	}
}
