package dev.skysurveyor.settings;

// "entryTitle" in config.json, names never rename
public record EntryTitleSettings(Boolean show, Double seconds, Boolean verdict) {
	public static final double MIN_SECONDS = 1;
	public static final double MAX_SECONDS = 30;
	// display choice, toast default
	// verdict on, nicks pick (calculator-design)
	public static final EntryTitleSettings DEFAULTS = new EntryTitleSettings(true, 5.0, true);

	// missing or weird = default
	public EntryTitleSettings repaired() {
		double time = seconds == null || !(seconds >= MIN_SECONDS && seconds <= MAX_SECONDS) ? DEFAULTS.seconds : seconds;
		return new EntryTitleSettings(show == null ? DEFAULTS.show : show, time, verdict == null ? DEFAULTS.verdict : verdict);
	}

	public boolean isShow() {
		return show;
	}

	public boolean isVerdict() {
		return verdict;
	}

	public EntryTitleSettings withShow(boolean value) {
		return new EntryTitleSettings(value, seconds, verdict);
	}

	public EntryTitleSettings withSeconds(double value) {
		return new EntryTitleSettings(show, value, verdict);
	}

	public EntryTitleSettings withVerdict(boolean value) {
		return new EntryTitleSettings(show, seconds, value);
	}
}
