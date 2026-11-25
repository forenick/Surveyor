package dev.skysurveyor.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.skysurveyor.detection.RecordingReplay;
import dev.skysurveyor.detection.TextPatterns;

// replay --marks: every mark w/ the lines around it, what claude reads first
final class Marks {
	// lines before + after each mark
	static final int AROUND = 10;
	// action bar, scoreboard etc. every half second would push the useful lines out
	private static final Set<String> CONTEXT = Set.of("chat", "live", "live_entry", "error", "mark", "title", "subtitle", "world_change",
			"screen", "screen_close", "settings");
	private static final int MAX_TEXT = 160;

	private Marks() {
	}

	static void print(Path file, List<JsonObject> records) {
		List<JsonObject> lines = records.stream().filter(r -> CONTEXT.contains(RecordingReplay.type(r))).toList();
		List<Integer> marks = new ArrayList<>();
		for (int i = 0; i < lines.size(); i++) {
			if (RecordingReplay.type(lines.get(i)).equals("mark")) marks.add(i);
		}
		System.out.println(file.getFileName() + ", " + marks.size() + " marks");
		for (int index : marks) {
			JsonObject mark = lines.get(index);
			System.out.println();
			System.out.println("== " + time(mark) + " " + markText(mark));
			for (int i = Math.max(0, index - AROUND); i <= Math.min(lines.size() - 1, index + AROUND); i++) {
				if (i == index) continue;
				System.out.println("  " + time(lines.get(i)) + " " + describe(lines.get(i)));
			}
		}
	}

	static String markText(JsonObject mark) {
		String kind = mark.has("auto") ? mark.get("auto").getAsString() : "manual";
		String category = mark.has("category") ? " " + mark.get("category").getAsString() : "";
		String note = mark.has("note") ? mark.get("note").getAsString() : "";
		return "[" + kind + category + "] " + note;
	}

	static String describe(JsonObject record) {
		String type = RecordingReplay.type(record);
		String text = switch (type) {
			case "chat", "actionbar", "title", "subtitle" -> TextPatterns.clean(string(record, "legacy"));
			case "live" -> string(record, "event");
			case "error" -> string(record, "what") + ": " + string(record, "error");
			case "mark" -> markText(record);
			case "screen" -> TextPatterns.clean(string(record, "title"));
			default -> "";
		};
		text = type + (text.isEmpty() ? "" : ": " + text);
		return text.length() > MAX_TEXT ? text.substring(0, MAX_TEXT) + "…" : text;
	}

	private static String string(JsonObject record, String key) {
		JsonElement value = record.get(key);
		return value == null || value.isJsonNull() ? "" : value.getAsString();
	}

	private static String time(JsonObject record) {
		return Replay.time(RecordingReplay.time(record));
	}
}
