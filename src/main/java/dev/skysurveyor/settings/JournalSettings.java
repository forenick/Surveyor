package dev.skysurveyor.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

// "journal" in config.json, names never rename
public record JournalSettings(boolean byDay, ChartType spawnChart, ChartType coinChart) {
	// journal groups sessions by day, nicks pick (2026-11-12)
	public static final JournalSettings DEFAULTS = new JournalSettings(true, ChartType.BARS, ChartType.BARS);

	// stats tab charts
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

	// null = default. by hand not gson, a weird value is its default, never a failed load
	static JournalSettings read(JsonElement json) {
		if (!json.isJsonObject()) return null;
		JsonObject journal = json.getAsJsonObject();
		boolean byDay = journal.has("byDay") && journal.get("byDay").isJsonPrimitive() ? journal.get("byDay").getAsBoolean()
				: DEFAULTS.byDay;
		return new JournalSettings(byDay, ChartType.read(journal, "spawnChart", DEFAULTS.spawnChart),
				ChartType.read(journal, "coinChart", DEFAULTS.coinChart));
	}

	public JournalSettings withByDay(boolean value) {
		return new JournalSettings(value, spawnChart, coinChart);
	}

	public JournalSettings withSpawnChart(ChartType value) {
		return new JournalSettings(byDay, value, coinChart);
	}

	public JournalSettings withCoinChart(ChartType value) {
		return new JournalSettings(byDay, spawnChart, value);
	}
}
