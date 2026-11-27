package dev.skysurveyor.data;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.skysurveyor.util.Resources;

public final class Patches {
	public record Patch(String name, long startMillis) {
	}

	private static Patches defaults;

	private final List<Patch> patches;

	private Patches(List<String> lines) {
		List<Patch> found = new ArrayList<>();
		for (String line : lines) {
			String trimmed = line.strip();
			if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
			String[] parts = trimmed.split("\\s+");
			if (parts.length < 2) throw new IllegalArgumentException("Bad patch line: " + line);
			found.add(new Patch(parts[0], start(parts[1])));
		}
		this.patches = List.copyOf(found);
	}

	public static synchronized Patches defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/patches.txt");
			defaults = parse(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	public static Patches parse(List<String> lines) {
		return new Patches(lines);
	}

	// day only = start of day, utc
	private static long start(String text) {
		try {
			return Instant.parse(text).toEpochMilli();
		} catch (DateTimeParseException e) {
			return LocalDate.parse(text).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
		}
	}

	// null = none
	public Patch newest() {
		return patches.stream().max(Comparator.comparingLong(Patch::startMillis)).orElse(null);
	}
}
