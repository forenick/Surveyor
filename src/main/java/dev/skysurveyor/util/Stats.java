package dev.skysurveyor.util;

import java.util.List;

public final class Stats {
	private Stats() {
	}

	// even count = middle two
	public static double median(List<Double> values) {
		List<Double> sorted = values.stream().sorted().toList();
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
	}
}
