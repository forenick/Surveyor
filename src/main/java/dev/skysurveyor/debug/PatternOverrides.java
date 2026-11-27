package dev.skysurveyor.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import dev.skysurveyor.detection.TextPatterns;

// skysurveyor/patterns.txt on top of the built-in one
public final class PatternOverrides {
	public static final String FILE = "patterns.txt";

	// lines null = no file, error = couldnt read it
	public record Read(List<String> lines, String error) {
	}

	private PatternOverrides() {
	}

	// io thread
	public static Read read(Path folder) {
		Path file = folder.resolve(FILE);
		if (!Files.isRegularFile(file)) return new Read(null, null);
		try {
			return new Read(Files.readAllLines(file, StandardCharsets.UTF_8), null);
		} catch (IOException | RuntimeException e) {
			return new Read(null, e.getMessage());
		}
	}

	// null = no file + none before. swaps patterns, client thread once chat comes
	public static String apply(Read read) {
		boolean had = !TextPatterns.overrides().isEmpty();
		String kept = had ? "kept the last overrides" : "using the built-in patterns";
		if (read.error() != null) return "§c[Surveyor] Could not read skysurveyor/" + FILE + ", " + kept + ": " + read.error();
		if (read.lines() == null) {
			if (!had) return null;
			TextPatterns.useOverrides(List.of());
			return "§7[Surveyor] No skysurveyor/" + FILE + " anymore, using the built-in patterns.";
		}
		try {
			int count = TextPatterns.useOverrides(read.lines());
			return "§e[Surveyor] Using " + count + " pattern override" + (count == 1 ? "" : "s") + " from skysurveyor/" + FILE + ".";
		} catch (RuntimeException e) {
			// bad regex, line or missing group: nothing changed
			return "§c[Surveyor] Could not use skysurveyor/" + FILE + ", " + kept + ": " + e.getMessage();
		}
	}

	// at start, before any chat
	public static String load(Path folder) {
		return apply(read(folder));
	}
}
