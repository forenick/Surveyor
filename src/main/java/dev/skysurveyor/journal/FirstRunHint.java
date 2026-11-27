package dev.skysurveyor.journal;

// once per game start, till the 1st shaft. nothing saved
public final class FirstRunHint {
	public static final String TEXT = "§b[Surveyor] §7Tracking your shafts. §f/sv §7opens the journal, §f/sv hud §7moves the boxes.";

	private boolean shown;

	// not loaded = cant tell
	public boolean check(boolean inMines, boolean journalLoaded, int shafts) {
		if (shown || !inMines || !journalLoaded || shafts > 0) return false;
		shown = true;
		return true;
	}
}
