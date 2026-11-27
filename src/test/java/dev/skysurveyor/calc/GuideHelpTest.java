package dev.skysurveyor.calc;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.calc.YourNumbers.Source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GuideHelpTest {
	@Test
	void everySourceIsExplainedOnce() {
		Set<Source> seen = EnumSet.noneOf(Source.class);
		for (GuideHelp.Block block : GuideHelp.blocks(5)) {
			for (GuideHelp.Line line : block.lines()) {
				if (line.icon() != null) assertFalse(!seen.add(line.icon()), line.icon() + " twice");
			}
		}
		assertEquals(EnumSet.allOf(Source.class), seen);
	}

	// nicks text rules: no dot separators, no em dashes
	@Test
	void plainText() {
		for (int mined : new int[] {0, 5, 10}) {
			for (GuideHelp.Block block : GuideHelp.blocks(mined)) {
				assertFalse(block.title().isBlank());
				assertFalse(block.lines().isEmpty(), block.title());
				for (GuideHelp.Line line : block.lines()) {
					assertFalse(line.text().isBlank(), block.title());
					assertFalse(line.text().contains("·") || line.text().contains("—"), line.text());
				}
			}
		}
	}

	@Test
	void theGearLineFollowsTheSetting() {
		String five = GuideHelp.blocks(5).toString();
		assertEquals(true, five.contains("newest 5 mined shafts") && five.contains("after about 3 mined shafts"));
		assertEquals(true, GuideHelp.blocks(10).toString().contains("after about 6 mined shafts"));
		assertEquals(true, GuideHelp.blocks(0).toString().contains("outnumber the older ones in the range"));
	}
}
