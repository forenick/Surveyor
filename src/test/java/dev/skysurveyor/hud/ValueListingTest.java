package dev.skysurveyor.hud;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudValue.Group;
import dev.skysurveyor.hud.HudValue.Range;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValueListingTest {
	@Test
	void addLineHidesPeriodsOfShafts() {
		List<HudValue> session = ValueListing.addLine(Group.SESSION);
		assertTrue(session.contains(HudValue.SESSION_SHAFTS));
		assertFalse(session.contains(HudValue.SHAFTS_TODAY), "a period of shafts, not its own row");
		assertEquals(HudValue.listed().stream().filter(v -> v.group() == Group.SESSION && v.inAddLine()).toList(), session,
				"listed order");
	}

	@Test
	void efficiencyListedUnderSpawn() {
		assertEquals(List.of(HudValue.SPAWN_TIMER, HudValue.AVERAGE_SPAWN, HudValue.EFFICIENCY),
				ValueListing.addLine(Group.SPAWN).subList(0, 3), "next to the spawn timer");
		assertFalse(ValueListing.addLine(Group.OTHER).contains(HudValue.EFFICIENCY));
		assertTrue(ValueListing.tags(Group.SPAWN, "").contains(HudValue.EFFICIENCY), "value list too");
		// only the list moves, not what it does
		assertEquals(Group.OTHER, HudValue.EFFICIENCY.group());
		assertFalse(HudValue.EFFICIENCY.followsShaft(Range.SESSION));
	}

	@Test
	void valueListHasTagsOnly() {
		assertFalse(ValueListing.tags(Group.MONEY, "").contains(HudValue.DROPS));
		assertTrue(ValueListing.tags(Group.MONEY, "").contains(HudValue.COINS));
		assertTrue(ValueListing.tags(Group.SESSION, "").contains(HudValue.SHAFTS_TODAY), "own tag in the list");
	}

	@Test
	void searchByTagIdOrName() {
		assertEquals(List.of(HudValue.SHAFT_COLD), ValueListing.tags(Group.SHAFT, "cold"));
		assertTrue(ValueListing.tags(Group.SHAFT, "  COLD ").contains(HudValue.SHAFT_COLD), "trimmed, any case");
		assertTrue(ValueListing.matches(HudValue.SHAFT_COLD, "shaft_cold"), "old id");
		assertTrue(ValueListing.matches(HudValue.SESSION_SHAFTS, "shafts"));
		assertFalse(ValueListing.matches(HudValue.TPS, "cold"));
		assertTrue(ValueListing.tags(Group.GAME, "nothing like this").isEmpty());
	}

	@Test
	void hasChecksValueAndPeriod() {
		Box box = new Box();
		box.lines.add(new Line(HudValue.TPS));
		box.lines.add(Line.of(HudValue.COINS.period(Range.ALL_TIME)));
		assertTrue(ValueListing.has(box, HudValue.TPS));
		assertTrue(ValueListing.has(box, HudValue.COINS.period(Range.ALL_TIME)));
		assertFalse(ValueListing.has(box, HudValue.COINS.period(Range.SESSION)));
	}

	@Test
	void periodsFitWholeWordsOrNone() {
		Box box = new Box();
		// 6 a letter, like the vanilla font
		assertTrue(ValueListing.periodsFit(box, 1000, 7, 10, s -> 6 * s.length()));
		assertFalse(ValueListing.periodsFit(box, 60, 7, 10, s -> 6 * s.length()));
		int widest = widestRow(box, 7, 10);
		assertTrue(ValueListing.periodsFit(box, widest, 7, 10, s -> 6 * s.length()));
		assertFalse(ValueListing.periodsFit(box, widest - 1, 7, 10, s -> 6 * s.length()));
	}

	@Test
	void ticksTakeRoom() {
		Box box = new Box();
		int widest = widestRow(box, 7, 10);
		for (HudValue value : HudValue.listed()) {
			if (value.inAddLine()) value.periods().forEach(p -> box.lines.add(Line.of(p)));
		}
		assertFalse(ValueListing.periodsFit(box, widest, 7, 10, s -> 6 * s.length()), "every period ticked");
	}

	private static int widestRow(Box box, int gap, int tick) {
		int widest = 0;
		for (HudValue value : HudValue.listed()) {
			List<HudValue.Period> periods = value.periods();
			if (!value.inAddLine() || periods.isEmpty()) continue;
			int w = (periods.size() - 1) * gap;
			for (HudValue.Period p : periods) w += 6 * p.name().length() + (ValueListing.has(box, p) ? tick : 0);
			widest = Math.max(widest, w);
		}
		return widest;
	}
}
