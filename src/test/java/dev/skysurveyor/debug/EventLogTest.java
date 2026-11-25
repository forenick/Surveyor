package dev.skysurveyor.debug;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.debug.EventLog.Kind;
import dev.skysurveyor.detection.ShaftEvent.ColdChanged;
import dev.skysurveyor.detection.ShaftEvent.FossilEssence;
import dev.skysurveyor.detection.ShaftEvent.Warping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogTest {
	@Test
	void keepsTheLastAndCountsAll() {
		EventLog log = new EventLog();
		assertNull(log.last(Kind.EVENT));
		for (int i = 0; i < EventLog.SIZE + 5; i++) log.add(i, i % 2 == 0 ? Kind.EVENT : Kind.UNMATCHED, "line " + i);
		log.add(1000, Kind.ERROR, "boom");
		assertEquals(EventLog.SIZE, log.lines().size());
		assertEquals("boom", log.lines().getLast().text());
		assertEquals("line " + (EventLog.SIZE + 4), log.last(Kind.EVENT).text());
		assertEquals((EventLog.SIZE + 5) / 2, log.unmatched());
	}

	@Test
	void leavesOutTheFrequentOnes() {
		assertFalse(EventLog.keeps(new FossilEssence(5)));
		assertFalse(EventLog.keeps(new ColdChanged(-12)));
		assertTrue(EventLog.keeps(new Warping()));
	}
}
