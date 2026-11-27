package dev.skysurveyor.settings;

// new part = record + field here + ConfigSection
public record Settings(MoneySettings money, JournalSettings journal, SummarySettings summary, PersonalBestSettings personalBests,
		DevSettings dev, EntryTitleSettings entryTitle, WaypointSettings waypoints, CalculatorSettings calculator) {
	public static final Settings DEFAULTS = new Settings(MoneySettings.DEFAULTS, JournalSettings.DEFAULTS, SummarySettings.DEFAULTS,
			PersonalBestSettings.DEFAULTS, DevSettings.DEFAULTS, EntryTitleSettings.DEFAULTS,
			WaypointSettings.DEFAULTS, CalculatorSettings.DEFAULTS);

	public Settings withMoney(MoneySettings value) {
		return new Settings(value, journal, summary, personalBests, dev, entryTitle, waypoints, calculator);
	}

	public Settings withJournal(JournalSettings value) {
		return new Settings(money, value, summary, personalBests, dev, entryTitle, waypoints, calculator);
	}

	public Settings withSummary(SummarySettings value) {
		return new Settings(money, journal, value, personalBests, dev, entryTitle, waypoints, calculator);
	}

	public Settings withPersonalBests(PersonalBestSettings value) {
		return new Settings(money, journal, summary, value, dev, entryTitle, waypoints, calculator);
	}

	public Settings withEntryTitle(EntryTitleSettings value) {
		return new Settings(money, journal, summary, personalBests, dev, value, waypoints, calculator);
	}

	public Settings withWaypoints(WaypointSettings value) {
		return new Settings(money, journal, summary, personalBests, dev, entryTitle, value, calculator);
	}

	public Settings withDev(DevSettings value) {
		return new Settings(money, journal, summary, personalBests, value, entryTitle, waypoints, calculator);
	}

	public Settings withCalculator(CalculatorSettings value) {
		return new Settings(money, journal, summary, personalBests, dev, entryTitle, waypoints, value);
	}
}
