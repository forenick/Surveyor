package dev.skysurveyor.screen;

// stats range header at 480 wide: drop "Range", then the dots. plain java
public final class RangeFit {
	public enum Fit {
		FULL, NO_WORD, NO_DOTS
	}

	// display choice, gap before the dots + slack for wider fonts
	static final int MIN_GAP = 6;

	private RangeFit() {
	}

	// room = row inside its padding, name = widest range name so cycling doesnt jump
	public static Fit fit(int room, int word, int name, int dots, int arrows) {
		if (word + name + MIN_GAP + dots + arrows <= room) return Fit.FULL;
		if (name + MIN_GAP + dots + arrows <= room) return Fit.NO_WORD;
		return Fit.NO_DOTS;
	}
}
