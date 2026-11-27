package dev.skysurveyor.calc;

// research/05 (rec 1), checked on 9 freezes (research/10 s2)
// secs per cold = 5 x (1 + cold res / 100) / (1 + 0.25 x floor(secs inside / 150))
public final class ColdModel {
	// 0 cold res, first 150 s
	static final double BASE_SECONDS = 5;
	// 25% faster every 150 s, in steps
	static final double STEP_SECONDS = 150;
	static final double STEP_FASTER = 0.25;
	// scoreboard never shows it (99 then dead)
	public static final int FREEZE_COLD = 100;
	// less when u have less (research/05)
	public static final int CAMPFIRE_COLD = 50;

	private ColdModel() {
	}

	// secs at the cold pace: 1/s, 1.25 after 150 s...
	// cold = exposure / perCold, any cold res
	public static double exposure(double seconds) {
		if (seconds <= 0) return 0;
		double step = Math.floor(seconds / STEP_SECONDS);
		double whole = STEP_SECONDS * (step + STEP_FASTER * step * (step - 1) / 2);
		return whole + (seconds - step * STEP_SECONDS) * (1 + STEP_FASTER * step);
	}

	public static double seconds(double exposure) {
		if (exposure <= 0) return 0;
		double step = 0;
		double left = exposure;
		while (left > STEP_SECONDS * (1 + STEP_FASTER * step)) {
			left -= STEP_SECONDS * (1 + STEP_FASTER * step);
			step++;
		}
		return step * STEP_SECONDS + left / (1 + STEP_FASTER * step);
	}

	public static double perCold(double coldResistance) {
		return BASE_SECONDS * (1 + coldResistance / 100);
	}

	public static double coldResistance(double perCold) {
		return (perCold / BASE_SECONDS - 1) * 100;
	}

	// campfire = 50 more
	public static double secondsUntil(double cold, double perCold) {
		return seconds(cold * perCold);
	}

	// secs the first n campfires add to a shaft u freeze in at freeze secs. each adds less
	public static double campfireSeconds(double freeze, int campfires) {
		if (campfires <= 0) return 0;
		double perCold = exposure(freeze) / FREEZE_COLD;
		return secondsUntil(FREEZE_COLD + CAMPFIRE_COLD * campfires, perCold) - freeze;
	}
}
