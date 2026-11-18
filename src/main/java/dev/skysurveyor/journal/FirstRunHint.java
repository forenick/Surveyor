package dev.skysurveyor.journal;

// once per game start, only till the 1st saved shaft. nothing saved
public final class FirstRunHint {
	public static final String TEXT = "§b[Surveyor] §7Tracking your shafts. §f/sv §7opens the journal.";

	private boolean shown;

	// true once, caller prints it. not loaded yet = cant tell if they have shafts
	public boolean check(boolean inMines, boolean journalLoaded, int shafts) {
		if (shown || !inMines || !journalLoaded || shafts > 0) return false;
		shown = true;
		return true;
	}
}
