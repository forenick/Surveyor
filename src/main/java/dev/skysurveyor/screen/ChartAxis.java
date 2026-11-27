package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;

final class ChartAxis {
	// display choice
	static final int MAX_STEPS = 4;
	// seconds
	static final double[] SPAWN_STEPS = {5, 10, 15, 30, 60, 120, 300, 600, 900, 1800, 3600};
	// 1, 2, 5 x 10^n coins
	static final double[] COIN_STEPS = coinSteps();
	// display choice
	static final int BAR_MAX = 24;
	static final int LEFT = 2;

	private ChartAxis() {
	}

	// under 0 hangs down from 0
	record Scale(double step, double top, double bottom) {
		// bottom up
		List<Double> ticks() {
			List<Double> ticks = new ArrayList<>();
			for (double value = bottom; value <= top + step / 2; value += step) ticks.add(value);
			return ticks;
		}

		boolean isZero(double tick) {
			return Math.abs(tick) < step / 2;
		}

		// base = bottom pixel row
		int y(double value, int base, int height) {
			return base - (int) (height * (value - bottom) / (top - bottom));
		}
	}

	// 0 always shows
	static Scale scale(double highest, double lowest, double[] steps) {
		highest = Math.max(0, highest);
		lowest = Math.min(0, lowest);
		double step = steps[steps.length - 1];
		for (double candidate : steps) {
			if (Math.ceil(highest / candidate) + Math.ceil(-lowest / candidate) <= MAX_STEPS) {
				step = candidate;
				break;
			}
		}
		double top = Math.ceil(highest / step) * step;
		double bottom = -Math.ceil(-lowest / step) * step;
		if (top == bottom) top = step;
		return new Scale(step, top, bottom);
	}

	// newest ones show
	record Bars(int width, int gap, int max) {
		int x(int index) {
			return LEFT + index * (width + gap);
		}
	}

	static Bars bars(int plotWidth, int count) {
		int width = Math.clamp((plotWidth - LEFT) * 4 / 5 / count, 3, BAR_MAX);
		int gap = Math.clamp(width / 4, 2, 6);
		return new Bars(width, gap, Math.max(1, (plotWidth - LEFT) / (width + gap)));
	}

	static String seconds(long seconds) {
		if (seconds == 0) return "0";
		if (seconds < 60) return seconds + "s";
		return seconds % 60 == 0 ? seconds / 60 + "m" : seconds / 60 + "m " + seconds % 60 + "s";
	}

	private static double[] coinSteps() {
		double[] steps = new double[3 * 13];
		for (int i = 0; i < steps.length; i++) steps[i] = new double[] {1, 2, 5}[i % 3] * Math.pow(10, i / 3);
		return steps;
	}
}
