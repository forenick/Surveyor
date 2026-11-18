package dev.skysurveyor.screen;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.screen.RangeFit.Fit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// made up widths: word 30, name 75, dots 21, arrows 18
class RangeFitTest {
	private static final int GAP = RangeFit.MIN_GAP;

	@Test
	void fullWhenEverythingFits() {
		assertEquals(Fit.FULL, RangeFit.fit(30 + 75 + GAP + 21 + 18, 30, 75, 21, 18));
	}

	@Test
	void dropsWordFirst() {
		assertEquals(Fit.NO_WORD, RangeFit.fit(30 + 75 + GAP + 21 + 18 - 1, 30, 75, 21, 18));
		assertEquals(Fit.NO_WORD, RangeFit.fit(75 + GAP + 21 + 18, 30, 75, 21, 18));
	}

	@Test
	void thenDots() {
		assertEquals(Fit.NO_DOTS, RangeFit.fit(75 + GAP + 21 + 18 - 1, 30, 75, 21, 18));
		assertEquals(Fit.NO_DOTS, RangeFit.fit(10, 30, 75, 21, 18));
	}
}
