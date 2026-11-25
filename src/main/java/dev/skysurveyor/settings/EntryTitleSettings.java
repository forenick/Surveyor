package dev.skysurveyor.settings;

// "entryTitle" in config.json, names never rename
public record EntryTitleSettings(Boolean show, Double seconds) {
	public static final double MIN_SECONDS = 1;
	public static final double MAX_SECONDS = 30;
	// display choice, 5 s = the toasts default
	public static final EntryTitleSettings DEFAULTS = new EntryTitleSettings(true, 5.0);

	// hand edited file, anything missing or weird = default
	public EntryTitleSettings repaired() {
		double time = seconds == null || !(seconds >= MIN_SECONDS && seconds <= MAX_SECONDS) ? DEFAULTS.seconds : seconds;
		return new EntryTitleSettings(show == null ? DEFAULTS.show : show, time);
	}

	public boolean isShow() {
		return show;
	}

	public EntryTitleSettings withShow(boolean value) {
		return new EntryTitleSettings(value, seconds);
	}

	public EntryTitleSettings withSeconds(double value) {
		return new EntryTitleSettings(show, value);
	}
}
