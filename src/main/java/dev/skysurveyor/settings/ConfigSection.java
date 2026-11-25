package dev.skysurveyor.settings;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import com.google.gson.JsonElement;

// one object in config.json. read: null = default. whole: written whole + left out while default, so better defaults reach every1
record ConfigSection<T>(String key, Function<Settings, T> get, BiFunction<Settings, T, Settings> with, Function<JsonElement, T> read,
		boolean whole) {
	static final ConfigSection<MoneySettings> MONEY = gson("money", MoneySettings.class, Settings::money, Settings::withMoney,
			MoneySettings::repaired);
	static final ConfigSection<JournalSettings> JOURNAL = new ConfigSection<>("journal", Settings::journal, Settings::withJournal,
			JournalSettings::read, false);
	static final ConfigSection<SummarySettings> SUMMARY = gson("summary", SummarySettings.class, Settings::summary, Settings::withSummary,
			SummarySettings::repaired);
	static final ConfigSection<PersonalBestSettings> PERSONAL_BESTS = new ConfigSection<>("personalBests", Settings::personalBests,
			Settings::withPersonalBests, PersonalBestSettings::read, true);
	static final ConfigSection<DevSettings> DEV = gson("dev", DevSettings.class, Settings::dev, Settings::withDev, DevSettings::repaired);
	static final ConfigSection<EntryTitleSettings> ENTRY_TITLE = gson("entryTitle", EntryTitleSettings.class, Settings::entryTitle,
			Settings::withEntryTitle, EntryTitleSettings::repaired);
	static final ConfigSection<WaypointSettings> WAYPOINTS = gson("waypoints", WaypointSettings.class, Settings::waypoints,
			Settings::withWaypoints, WaypointSettings::repaired);
	// a new files key order + the load order, new ones go last
	static final List<ConfigSection<?>> ALL = List.of(MONEY, JOURNAL, SUMMARY, PERSONAL_BESTS, DEV, ENTRY_TITLE, WAYPOINTS);

	private static <T> ConfigSection<T> gson(String key, Class<T> type, Function<Settings, T> get, BiFunction<Settings, T, Settings> with,
			UnaryOperator<T> repaired) {
		return new ConfigSection<>(key, get, with, json -> {
			T loaded = ConfigFile.GSON.fromJson(json, type);
			return loaded == null ? null : repaired.apply(loaded);
		}, false);
	}

	boolean isDefault(Settings settings) {
		return of(settings).equals(of(Settings.DEFAULTS));
	}

	T of(Settings settings) {
		return get.apply(settings);
	}

	Settings in(Settings settings, T value) {
		return with.apply(settings, value);
	}
}
