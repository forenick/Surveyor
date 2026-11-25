package dev.skysurveyor.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.util.Resources;

// fossil_spots.txt, layout code -> box around its fossil
public final class FossilSpots {
	private static final Pattern LINE = Pattern.compile(
			"^(?<code>[A-Z]{4}_\\w)\\s*=\\s*(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s+(-?\\d+)\\s*,\\s*(?<blocks>\\d+) blocks\\b");

	private static FossilSpots defaults;

	public record Spot(String code, int minX, int minY, int minZ, int maxX, int maxY, int maxZ, int blocks) {
		public double centerX() {
			return (minX + maxX + 1) / 2.0;
		}

		public double centerY() {
			return (minY + maxY + 1) / 2.0;
		}

		public double centerZ() {
			return (minZ + maxZ + 1) / 2.0;
		}
	}

	private final Map<String, Spot> spots = new LinkedHashMap<>();

	FossilSpots(List<String> lines) {
		for (String line : lines) {
			Matcher m = LINE.matcher(line.strip());
			if (!m.find()) continue;
			int[] n = new int[6];
			for (int i = 0; i < 6; i++) n[i] = Integer.parseInt(m.group(i + 2));
			String code = m.group("code");
			spots.put(code, new Spot(code, n[0], n[1], n[2], n[3], n[4], n[5], Integer.parseInt(m.group("blocks"))));
		}
	}

	public static synchronized FossilSpots defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/fossil_spots.txt");
			defaults = new FossilSpots(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	// null = no fossil known for that layout
	public Spot get(String code) {
		return code == null ? null : spots.get(code);
	}

	public boolean has(String code) {
		return get(code) != null;
	}

	public List<String> codes() {
		return List.copyOf(spots.keySet());
	}
}
