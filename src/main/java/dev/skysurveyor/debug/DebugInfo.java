package dev.skysurveyor.debug;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.skysurveyor.util.Guard;

// for a github issue. never a player name, uuid, chat or coords
public record DebugInfo(Map<String, String> versions, List<String> mods, List<String> changedSettings, List<Guard.Failure> errors,
		List<String> files, Map<String, Integer> skippedLines, String recording) {
	// display choice, a stack per kind would bury the rest
	static final int MAX_ERRORS = 10;

	public String text() {
		StringBuilder text = new StringBuilder("Surveyor debug info\n");
		versions.forEach((name, version) -> text.append(name).append(": ").append(version).append('\n'));
		text.append("\nSettings not at default:").append(changedSettings.isEmpty() ? " none\n" : "\n");
		changedSettings.forEach(line -> text.append("  ").append(line).append('\n'));
		text.append("\nErrors since start:").append(errors.isEmpty() ? " none\n" : "\n");
		errors.stream().limit(MAX_ERRORS).forEach(e -> text.append("  ").append(e.what()).append(" (").append(e.count()).append("x): ")
				.append(errorClass(e.error())).append("\n    at ").append(e.frame()).append('\n'));
		if (errors.size() > MAX_ERRORS) text.append("  and ").append(errors.size() - MAX_ERRORS).append(" more kinds\n");
		text.append("\nData files:").append(files.isEmpty() ? " none\n" : "\n");
		files.forEach(line -> text.append("  ").append(line).append('\n'));
		skippedLines.forEach((file, skipped) -> {
			if (skipped > 0) text.append("  ").append(file).append(": ").append(skipped).append(" lines couldn't be read\n");
		});
		text.append("\nRecording: ").append(recording).append('\n');
		text.append("\nOther mods (").append(mods.size()).append("):\n");
		mods.forEach(line -> text.append("  ").append(line).append('\n'));
		return text.toString();
	}

	// class only, a message can hold the game text it choked on
	static String errorClass(String error) {
		int colon = error.indexOf(':');
		return colon < 0 ? error : error.substring(0, colon);
	}

	// "money.gemTier: fine", config.jsons own shape
	public static List<String> changedSettings(JsonObject now, JsonObject defaults) {
		List<String> changed = new ArrayList<>();
		for (Map.Entry<String, JsonElement> section : now.entrySet()) {
			JsonElement old = defaults.get(section.getKey());
			if (section.getValue().isJsonObject() && old != null && old.isJsonObject()) {
				for (Map.Entry<String, JsonElement> value : section.getValue().getAsJsonObject().entrySet()) {
					if (!Objects.equals(old.getAsJsonObject().get(value.getKey()), value.getValue())) {
						changed.add(section.getKey() + "." + value.getKey() + ": " + value.getValue());
					}
				}
			} else if (!Objects.equals(old, section.getValue())) {
				// f.e. pb list, the whole thing
				changed.add(section.getKey() + ": changed");
			}
		}
		return changed;
	}

	// "journal.jsonl: 120 KB, 431 lines"
	public static String file(String name, long bytes, long lines) {
		return name + ": " + size(bytes) + (lines < 0 ? "" : ", " + lines + " lines");
	}

	public static String size(long bytes) {
		if (bytes < 1024) return bytes + " B";
		if (bytes < 1024 * 1024) return bytes / 1024 + " KB";
		return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
	}
}
