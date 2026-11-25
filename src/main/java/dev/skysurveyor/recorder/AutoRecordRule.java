package dev.skysurveyor.recorder;

// when auto record starts + stops. one file per journal session, manual recordings never touched
public final class AutoRecordRule {
	public enum Action {
		NONE, START, STOP
	}

	// u stopped by hand in this session, dont start again until the next one
	private Long skipped;

	// recordingAuto = session of the auto recording going on, null = none or manual
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
