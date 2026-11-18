package dev.skysurveyor.screen;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// made up values, steps are the real ones
class ChartAxisTest {
	@Test
	void spawnPicksTheSmallestStepThatFitsFour() {
		// 47s: 5 needs 10 steps, 10 needs 5, 15 needs 4
		ChartAxis.Scale scale = ChartAxis.scale(47, 3, ChartAxis.SPAWN_STEPS);
		assertEquals(new ChartAxis.Scale(15, 60, 0), scale);
		assertEquals(List.of(0.0, 15.0, 30.0, 45.0, 60.0), scale.ticks());
	}

	@Test
	void negativeCoinsHangBelowZero() {
		// 3.2M up, 1.1M down: 1M needs 4 + 2, 2M needs 2 + 1
		ChartAxis.Scale scale = ChartAxis.scale(3_200_000, -1_100_000, ChartAxis.COIN_STEPS);
		assertEquals(new ChartAxis.Scale(2_000_000, 4_000_000, -2_000_000), scale);
		assertEquals(List.of(-2_000_000.0, 0.0, 2_000_000.0, 4_000_000.0), scale.ticks());
		assertTrue(scale.isZero(0));
		assertFalse(scale.isZero(2_000_000));
	}

	@Test
	void allZeroStillHasOneStep() {
		ChartAxis.Scale scale = ChartAxis.scale(0, 0, ChartAxis.COIN_STEPS);
		assertEquals(new ChartAxis.Scale(1, 1, 0), scale);
	}

	@Test
	void hugeValuesUseTheBiggestStep() {
		ChartAxis.Scale scale = ChartAxis.scale(100_000, 0, ChartAxis.SPAWN_STEPS);
		assertEquals(3600, scale.step());
		assertEquals(28 * 3600, scale.top());
	}

	@Test
	void coinStepsAre125() {
		assertEquals(39, ChartAxis.COIN_STEPS.length);
		assertEquals(List.of(1.0, 2.0, 5.0, 10.0, 20.0, 50.0), List.of(ChartAxis.COIN_STEPS[0], ChartAxis.COIN_STEPS[1],
				ChartAxis.COIN_STEPS[2], ChartAxis.COIN_STEPS[3], ChartAxis.COIN_STEPS[4], ChartAxis.COIN_STEPS[5]));
	}

	@Test
	void pixelRows() {
		// base 100, 80 rows for 60s
		ChartAxis.Scale scale = new ChartAxis.Scale(15, 60, 0);
		assertEquals(100, scale.y(0, 100, 80));
		assertEquals(20, scale.y(60, 100, 80));
		assertEquals(60, scale.y(30, 100, 80));
		// negative bottom: 0 sits a third up
		ChartAxis.Scale coins = new ChartAxis.Scale(2, 4, -2);
		assertEquals(70, coins.y(0, 100, 90));
	}

	@Test
	void barsShrinkThenStopAtThree() {
		// (200 - 2) * 4 / 5 / 10 = 15 wide, gap 3, 11 fit
		assertEquals(new ChartAxis.Bars(15, 3, 11), ChartAxis.bars(200, 10));
		// few bars cap at 24, gap 6
		assertEquals(new ChartAxis.Bars(24, 6, 6), ChartAxis.bars(200, 2));
		// many bars: 3 wide, gap 2, newest 39 show
		assertEquals(new ChartAxis.Bars(3, 2, 39), ChartAxis.bars(200, 500));
		assertEquals(2, new ChartAxis.Bars(15, 3, 11).x(0));
		assertEquals(38, new ChartAxis.Bars(15, 3, 11).x(2));
	}

	@Test
	void secondsLabels() {
		assertEquals("0", ChartAxis.seconds(0));
		assertEquals("45s", ChartAxis.seconds(45));
		assertEquals("2m", ChartAxis.seconds(120));
		assertEquals("1m 30s", ChartAxis.seconds(90));
	}
}
