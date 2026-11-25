package dev.skysurveyor.settings;

// everything the settings page edits, all of it in config.json. new part = a record, a field here + a ConfigSection
public record Settings(MoneySettings money, JournalSettings journal, SummarySettings summary, PersonalBestSettings personalBests,
		DevSettings dev, EntryTitleSettings entryTitle, WaypointSettings waypoints) {
	public static final Settings DEFAULTS = new Settings(MoneySettings.DEFAULTS, JournalSettings.DEFAULTS, SummarySettings.DEFAULTS,
			PersonalBestSettings.DEFAULTS, DevSettings.DEFAULTS, EntryTitleSettings.DEFAULTS,
			WaypointSettings.DEFAULTS);

	public Settings withMoney(MoneySettings value) {
		return new Settings(value, journal, summary, personalBests, dev, entryTitle, waypoints);
	}

	public Settings withJournal(JournalSettings value) {
		return new Settings(money, value, summary, personalBests, dev, entryTitle, waypoints);
	}

	public Settings withSummary(SummarySettings value) {
		return new Settings(money, journal, value, personalBests, dev, entryTitle, waypoints);
	}

	public Settings withPersonalBests(PersonalBestSettings value) {
		return new Settings(money, journal, summary, value, dev, entryTitle, waypoints);
	}

	public Settings withEntryTitle(EntryTitleSettings value) {
		return new Settings(money, journal, summary, personalBests, dev, value, waypoints);
	}

	public Settings withWaypoints(WaypointSettings value) {
		return new Settings(money, journal, summary, personalBests, dev, entryTitle, value);
	}

	public Settings withDev(DevSettings value) {
		return new Settings(money, journal, summary, personalBests, value, entryTitle, waypoints);
	}
}
