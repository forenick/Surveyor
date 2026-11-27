package dev.skysurveyor.recorder;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.util.Resources;

// names -> Player1, Player2.., own -> You. same mask all file long
public final class NameMask {
	// valid mc names. hypixels fake tab entries (!A-a) + odd npc names fall out
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_]+");
	// line structure, not game text. a player named "mark" would break replay
	private static final Set<String> STRUCTURE = Set.of("type", "auto", "category", "version", "event", "sound", "source", "color", "item",
			"block");
	private static final List<String> VOCABULARY_FILES = List.of("/skysurveyor/patterns.txt", "/skysurveyor/skyblock_items.txt",
			"/skysurveyor/shaft_types.txt", "/skysurveyor/item_names.txt", "/skysurveyor/dev_watch.txt");

	private final Set<String> vocabulary;
	// recorder.ranked, overrides too
	private final TextPatterns patterns;
	private final Map<String, String> masks = new HashMap<>();
	// not names, online list check skips them fast
	private final Set<String> rejected = new HashSet<>();
	private int next = 1;

	NameMask(Set<String> vocabulary) {
		this(vocabulary, TextPatterns.defaults());
	}

	NameMask(Set<String> vocabulary, TextPatterns patterns) {
		this.vocabulary = vocabulary.stream().map(w -> w.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
		this.patterns = patterns;
	}

	// player named like a game word (Jasper, Umber) keeps it, else "Jasper Gemstone" breaks
	public static NameMask defaults() {
		Set<String> words = new HashSet<>();
		for (String file : VOCABULARY_FILES) {
			List<String> lines = Resources.lines(file);
			if (lines == null) continue;
			for (String line : lines) {
				if (line.strip().startsWith("#")) continue;
				Matcher matcher = NAME.matcher(line);
				while (matcher.find()) words.add(matcher.group());
			}
		}
		return new NameMask(words);
	}

	public void reset() {
		masks.clear();
		rejected.clear();
		next = 1;
	}

	// own null = unknown
	public void update(String own, Collection<String> names) {
		if (own != null && !masks.containsKey(own) && usable(own)) masks.put(own, "You");
		for (String name : names) add(name);
	}

	private void add(String name) {
		if (name == null || masks.containsKey(name) || rejected.contains(name)) return;
		if (usable(name)) masks.put(name, "Player" + next++);
		else rejected.add(name);
	}

	private boolean usable(String name) {
		return NAME.matcher(name).matches() && !vocabulary.contains(name.toLowerCase(Locale.ROOT));
	}

	public String apply(String text) {
		if (masks.isEmpty() || text == null) return text;
		Matcher matcher = TOKEN.matcher(text);
		StringBuilder out = null;
		int last = 0;
		while (matcher.find()) {
			int start = matcher.start();
			String mask = masks.get(matcher.group());
			// "§bName" is one token, try w/o the code
			int code = codeLength(text, start);
			if (mask == null && code > 0 && code < matcher.end() - start) {
				mask = masks.get(text.substring(start + code, matcher.end()));
				if (mask != null) start += code;
			}
			if (mask == null) continue;
			if (out == null) out = new StringBuilder(text.length());
			out.append(text, last, start).append(mask);
			last = matcher.end();
		}
		return out == null ? text : out.append(text, last, text.length()).toString();
	}

	// "§b" = 1, "§#a1b2c3" = 6, else 0
	private static int codeLength(String text, int i) {
		if (i >= 1 && text.charAt(i - 1) == '§' && "0123456789abcdefklmnorABCDEFKLMNOR".indexOf(text.charAt(i)) >= 0) return 1;
		if (i >= 2 && text.charAt(i - 1) == '#' && text.charAt(i - 2) == '§' && text.length() >= i + 6
				&& text.substring(i, i + 6).matches("[0-9A-Fa-f]{6}")) {
			return 6;
		}
		return 0;
	}

	// keys + structure stay. ranked names 1st so the whole line gets them
	public void apply(JsonObject json) {
		learn(json);
		if (masks.isEmpty()) return;
		mask(json);
	}

	private void learn(JsonElement element) {
		if (element.isJsonObject()) {
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) learn(entry.getValue());
		} else if (element.isJsonArray()) {
			for (JsonElement child : element.getAsJsonArray()) learn(child);
		} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			String text = element.getAsString();
			if (text.indexOf(']') < 0) return;
			Matcher matcher = patterns.match("recorder.ranked", TextPatterns.strip(text));
			if (matcher == null) return;
			do {
				add(matcher.group("name"));
			} while (matcher.find());
		}
	}

	private void mask(JsonObject json) {
		for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
			if (STRUCTURE.contains(entry.getKey()) && entry.getValue().isJsonPrimitive()) continue;
			entry.setValue(applyTo(entry.getValue()));
		}
	}

	private JsonElement applyTo(JsonElement element) {
		if (element.isJsonObject()) {
			mask(element.getAsJsonObject());
		} else if (element.isJsonArray()) {
			JsonArray array = element.getAsJsonArray();
			for (int i = 0; i < array.size(); i++) array.set(i, applyTo(array.get(i)));
		} else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
			String text = element.getAsString();
			String masked = apply(text);
			if (!masked.equals(text)) return new JsonPrimitive(masked);
		}
		return element;
	}
}
