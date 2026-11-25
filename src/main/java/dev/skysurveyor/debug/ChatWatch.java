package dev.skysurveyor.debug;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.util.Resources;

// chat w/ a watch word no pattern matched, so new game text shows up in recordings
public final class ChatWatch {
	private static final String RESOURCE = "/skysurveyor/dev_watch.txt";
	private static final Pattern DIGITS = Pattern.compile("\\d[\\d,.]*");
	// not chat: scoreboard, tab list, footer, screens, lore, action bar, the recorders rank finder.
	// effect.screen_name alone matches any line ending in II
	private static final List<String> NOT_CHAT = List.of("scoreboard.", "tab.", "effect.", "fossil.", "consumable.duration", "recorder.");

	private final List<String> words;
	private final TextPatterns patterns;

	ChatWatch(List<String> words, TextPatterns patterns) {
		this.words = words.stream().map(w -> w.toLowerCase(Locale.ROOT)).toList();
		this.patterns = patterns;
	}

	public static ChatWatch defaults(TextPatterns patterns) {
		List<String> lines = Resources.lines(RESOURCE);
		if (lines == null) throw new IllegalStateException("Missing " + RESOURCE);
		return new ChatWatch(lines.stream().map(String::strip).filter(l -> !l.isEmpty() && !l.startsWith("#")).toList(), patterns);
	}

	// null = nothing to note, else the clean text
	public String unmatched(String legacy) {
		String text = TextPatterns.clean(legacy);
		if (text.isBlank()) return null;
		String lower = text.toLowerCase(Locale.ROOT);
		// words 1st, cheap. chat keys cover chat.private too
		for (String word : words) {
			if (lower.contains(word)) return patterns.matchesAny(text, ChatWatch::chatKey) ? null : text;
		}
		return null;
	}

	static boolean chatKey(String key) {
		for (String prefix : NOT_CHAT) {
			if (key.startsWith(prefix)) return false;
		}
		return true;
	}

	// same msg w/ other numbers = one mark per file
	public static String key(String text) {
		return DIGITS.matcher(text).replaceAll("#");
	}
}
