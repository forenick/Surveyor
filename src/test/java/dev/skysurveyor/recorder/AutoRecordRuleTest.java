package dev.skysurveyor.recorder;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.recorder.AutoRecordRule.Action;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AutoRecordRuleTest {
	@Test
	void startsInSessionInArea() {
		AutoRecordRule rule = new AutoRecordRule();
		assertEquals(Action.START, rule.next(true, false, null, 5L, true));
		assertEquals(Action.NONE, rule.next(true, false, null, 5L, false), "warp through w/o area");
		assertEquals(Action.NONE, rule.next(true, false, null, null, true), "no session yet");
		assertEquals(Action.NONE, rule.next(false, false, null, 5L, true), "setting off");
	}

	@Test
	void keepsGoingOutsideAreaUntilSessionEnds() {
		AutoRecordRule rule = new AutoRecordRule();
		assertEquals(Action.NONE, rule.next(true, true, 5L, 5L, false));
		assertEquals(Action.STOP, rule.next(true, true, 5L, null, false), "session ended");
		assertEquals(Action.STOP, rule.next(true, true, 5L, 6L, true), "new session, new file");
		assertEquals(Action.STOP, rule.next(false, true, 5L, 5L, true), "turned off");
	}

	@Test
	void neverTouchesManual() {
		AutoRecordRule rule = new AutoRecordRule();
		assertEquals(Action.NONE, rule.next(true, true, null, 5L, true));
		assertEquals(Action.NONE, rule.next(true, true, null, null, false));
	}

	@Test
	void stoppedByHandWaitsForNextSession() {
		AutoRecordRule rule = new AutoRecordRule();
		rule.stoppedByHand(5L);
		assertEquals(Action.NONE, rule.next(true, false, null, 5L, true));
		assertEquals(Action.START, rule.next(true, false, null, 6L, true));
	}
}
