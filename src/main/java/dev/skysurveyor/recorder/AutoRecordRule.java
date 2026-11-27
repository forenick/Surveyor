package dev.skysurveyor.recorder;

// one file per journal session, manual recordings never touched
public final class AutoRecordRule {
	public enum Action {
		NONE, START, STOP
	}

	// stopped by hand, no restart till the next session
	private Long skipped;

	// recordingAuto null = none or manual
	public Action next(boolean on, boolean recording, Long recordingAuto, Long session, boolean inArea) {
		if (recording && recordingAuto == null) return Action.NONE;
		if (recordingAuto != null) return on && recordingAuto.equals(session) ? Action.NONE : Action.STOP;
		if (!on || session == null || !inArea || session.equals(skipped)) return Action.NONE;
		return Action.START;
	}

	public void stoppedByHand(Long session) {
		skipped = session;
	}
}
