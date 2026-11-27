package dev.skysurveyor.calc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// check_xlsx.py freeze() (11-25): cold res -> secs to 100, 150, 200 cold
class ColdModelTest {
	@Test
	void freezeTimesMatchThePrototype() {
		// research/10 s2: 757 s, 999 w/ 1 campfire, 1210 w/ 2
		check(128, 756.667, 999.0, 1210.0);
		// research/11 s4: 12:43, 16:48, 20:20
		check(131, 763.333, 1008.0, 1220.0);
		// workbook example, 132 + 2 mayhem: 12.8333 min
		check(134, 770.0, 1017.0, 1230.0);
		check(55, 571.429, 766.667, 935.0);
		check(0, 408.333, 557.143, 687.5);
		check(300, 1109.091, 1442.308, 1726.667);
	}

	private static void check(double coldResistance, double freeze, double oneCampfire, double twoCampfires) {
		double perCold = ColdModel.perCold(coldResistance);
		assertEquals(freeze, ColdModel.secondsUntil(100, perCold), 0.001);
		assertEquals(oneCampfire, ColdModel.secondsUntil(150, perCold), 0.001);
		assertEquals(twoCampfires, ColdModel.secondsUntil(200, perCold), 0.001);
	}

	@Test
	void secondsUndoesExposure() {
		for (double seconds : new double[] {0, 1, 149.9, 150, 150.1, 299, 300, 763.3, 1500}) {
			assertEquals(seconds, ColdModel.seconds(ColdModel.exposure(seconds)), 1e-9);
		}
		// 150 s at 1, then 13 s at 1.25
		assertEquals(166.25, ColdModel.exposure(163), 1e-9);
	}

	@Test
	void coldResistanceUndoesPerCold() {
		assertEquals(131, ColdModel.coldResistance(ColdModel.perCold(131)), 1e-9);
		assertEquals(5, ColdModel.perCold(0), 1e-9);
	}
}
