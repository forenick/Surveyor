package dev.skysurveyor.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import dev.skysurveyor.journal.StatsRange;

// "journal" in config.json, names never rename
// statsRange since 0.27.0-alpha.39, missing or odd = all-time
public record JournalSettings(boolean byDay, ChartType spawnChart, ChartType coinChart, StatsRange statsRange) {
	// by day, nick 11-12. all-time = what stats always opened on
	public static final JournalSettings DEFAULTS = new JournalSettings(true, ChartType.BARS, ChartType.BARS, StatsRange.ALL_TIME);

	public JournalSettings {
		if (statsRange == null) statsRange = StatsRange.ALL_TIME;
	}

	public enum ChartType {
		@SerializedName("bars")
		BARS("Bars"),
		@SerializedName("line")
		LINE("Line");

		private final String displayName;

		ChartType(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}

		public ChartType next(int step) {
			return values()[Math.floorMod(ordinal() + step, values().length)];
		}

		static ChartType read(JsonObject json, String key, ChartType fallback) {
			if (!json.has(key) || !json.get(key).isJsonPrimitive()) return fallback;
			String saved = json.get(key).getAsString();
			for (ChartType type : values()) {
				if (type.name().equalsIgnoreCase(saved)) return type;
			}
			return fallback;
		}
	}

	// by hand not gson, weird = default
	static JournalSettings read(JsonElement json) {
		if (!json.isJsonObject()) return null;
		JsonObject journal = json.getAsJsonObject();
		boolean byDay = journal.has("byDay") && journal.get("byDay").isJsonPrimitive() ? journal.get("byDay").getAsBoolean()
				: DEFAULTS.byDay;
		return new JournalSettings(byDay, ChartType.read(journal, "spawnChart", DEFAULTS.spawnChart),
				ChartType.read(journal, "coinChart", DEFAULTS.coinChart), range(journal.get("statsRange"), DEFAULTS.statsRange));
	}

	// saved by enum name. missing, odd or a later mod's range = fallback
	static StatsRange range(JsonElement json, StatsRange fallback) {
		if (json == null || !json.isJsonPrimitive()) return fallback;
		StatsRange saved = StatsRange.ofSaved(json.getAsString());
		return saved == null ? fallback : saved;
	}

	public JournalSettings withByDay(boolean value) {
		return new JournalSettings(value, spawnChart, coinChart, statsRange);
	}

	public JournalSettings withSpawnChart(ChartType value) {
		return new JournalSettings(byDay, value, coinChart, statsRange);
	}

	public JournalSettings withCoinChart(ChartType value) {
		return new JournalSettings(byDay, spawnChart, value, statsRange);
	}

	public JournalSettings withStatsRange(StatsRange value) {
		return new JournalSettings(byDay, spawnChart, coinChart, value);
	}
}
