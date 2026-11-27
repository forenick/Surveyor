package dev.skysurveyor.screen;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.screen.RangeFit.Fit;

import static org.junit.jupiter.api.Assertions.assertEquals;

// made up widths: 4 chips, gap 3, word 32 + extra 80 w/ their gaps
class RangeFitTest {
	private static final List<Integer> CHIPS = List.of(48, 66, 86, 100);
	private static final int GAP = 3;
	private static final int ALL = 48 + 66 + 86 + 100 + 3 * GAP;
	private static final int WORD = 32;
	private static final int EXTRA = 80;

	@Test
	void oneRowWhenItFits() {
		assertEquals(new Fit(true, List.of(4), false), RangeFit.fit(WORD + ALL + EXTRA, WORD, CHIPS, GAP, EXTRA));
		assertEquals(new Fit(true, List.of(4), false), RangeFit.fit(WORD + ALL, WORD, CHIPS, GAP, 0), "stats has no extra");
	}

	@Test
	void wordGoesFirst() {
		assertEquals(new Fit(false, List.of(4), false), RangeFit.fit(WORD + ALL + EXTRA - 1, WORD, CHIPS, GAP, EXTRA));
		assertEquals(new Fit(false, List.of(4), false), RangeFit.fit(ALL + EXTRA, WORD, CHIPS, GAP, EXTRA));
		assertEquals(new Fit(false, List.of(4), false), RangeFit.fit(ALL, WORD, CHIPS, GAP, 0));
	}

	// the word comes back once the extra has its own row
	@Test
	void thenTheExtraGetsItsOwnRow() {
		assertEquals(new Fit(true, List.of(4), true), RangeFit.fit(ALL + EXTRA - 1, WORD, CHIPS, GAP, EXTRA));
		assertEquals(new Fit(false, List.of(4), true), RangeFit.fit(ALL, WORD, CHIPS, GAP, EXTRA));
	}

	@Test
	void thenChipsWrap() {
		assertEquals(new Fit(false, List.of(3, 1), false), RangeFit.fit(ALL - 1, WORD, CHIPS, GAP, 0));
		// 100 + 80 fits under 3 chips
		assertEquals(new Fit(false, List.of(3, 1), false), RangeFit.fit(ALL - 1, WORD, CHIPS, GAP, EXTRA));
		assertEquals(new Fit(false, List.of(2, 1, 1), true), RangeFit.fit(48 + 66 + GAP, WORD, CHIPS, GAP, EXTRA));
	}

	// never drops a range
	@Test
	void everyChipStaysOnANarrowScreen() {
		assertEquals(new Fit(false, List.of(1, 1, 1, 1), false), RangeFit.fit(10, WORD, CHIPS, GAP, 0));
		assertEquals(new Fit(false, List.of(1, 1, 1, 1), true), RangeFit.fit(10, WORD, CHIPS, GAP, EXTRA));
	}
}
