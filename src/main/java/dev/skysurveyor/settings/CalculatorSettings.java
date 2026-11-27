package dev.skysurveyor.settings;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.annotations.SerializedName;

import dev.skysurveyor.calc.Fiesta;
import dev.skysurveyor.calc.Layouts;
import dev.skysurveyor.calc.NumberId;
import dev.skysurveyor.journal.StatsRange;

// "calculator" in config.json, keys never rename
// gems_per_block, other + coins_per_spawn = no cole, _cole + _flask since 0.27.0-alpha.4
// efficiency = every layout, efficiency_<LAYOUT> = that one. route_<LAYOUT> = own blocks on its route, 0 = no limit
// minedShafts since 0.27.0-alpha.25, missing = newest 5
// range since 0.27.0-alpha.39, missing or odd = last 5 sessions
public record CalculatorSettings(Map<String, Double> typed, KeyLoot keyLoot, MinedShafts minedShafts, StatsRange range) {
	// wiki table, nick 11-25 (research/16). newest 5, nick 11-27. ur play changes, so recent sessions
	public static final CalculatorSettings DEFAULTS = new CalculatorSettings(Map.of(), KeyLoot.WIKI, MinedShafts.NEWEST_5,
			StatsRange.LAST_5_SESSIONS);

	public enum KeyLoot {
		@SerializedName("wiki")
		WIKI,
		@SerializedName("own")
		OWN,
		@SerializedName("pristine_only")
		PRISTINE_ONLY
	}

	// mined shafts behind the mining numbers. display choices
	public enum MinedShafts {
		@SerializedName("newest_5")
		NEWEST_5(5),
		@SerializedName("newest_10")
		NEWEST_10(10),
		// what the guide's range holds
		@SerializedName("range")
		RANGE(0);

		private final int count;

		MinedShafts(int count) {
			this.count = count;
		}

		// 0 = the range
		public int count() {
			return count;
		}
	}

	public CalculatorSettings {
		// keeps the file's order, copyOf shuffles it every launch
		typed = Collections.unmodifiableMap(new LinkedHashMap<>(typed));
		if (keyLoot == null) keyLoot = KeyLoot.WIKI;
		if (minedShafts == null) minedShafts = MinedShafts.NEWEST_5;
		if (range == null) range = StatsRange.LAST_5_SESSIONS;
	}

	// null = default, bad number skipped
	static CalculatorSettings read(JsonElement json) {
		if (!json.isJsonObject()) return null;
		JsonElement typed = json.getAsJsonObject().get("typed");
		Map<String, Double> values = new LinkedHashMap<>();
		if (typed != null && typed.isJsonObject()) {
			for (Map.Entry<String, JsonElement> e : typed.getAsJsonObject().entrySet()) {
				JsonElement value = e.getValue();
				// 0 ticks would divide by 0. unknown keys kept
				NumberId id = Fiesta.idOf(e.getKey());
				// efficiency_PERI_2, per layout since 0.27.0-alpha.14
				if (id == null && Layouts.layoutOf(e.getKey()) != null) id = NumberId.EFFICIENCY;
				// route_CITR_1, per layout since 0.27.0-alpha.20
				boolean route = Layouts.isRouteKey(e.getKey());
				if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() && Double.isFinite(value.getAsDouble())
						&& (route ? Layouts.validRoute(value.getAsDouble()) : id == null || id.valid(value.getAsDouble()))) {
					// crystals per bp since alpha.19, a typed new key wins
					String moved = Layouts.movedCrystalKey(e.getKey());
					if (moved != null) values.putIfAbsent(moved, value.getAsDouble());
					else values.put(e.getKey(), value.getAsDouble());
				}
			}
		}
		return new CalculatorSettings(values, pick(json.getAsJsonObject().get("keyLoot"), KeyLoot.class),
				pick(json.getAsJsonObject().get("minedShafts"), MinedShafts.class),
				JournalSettings.range(json.getAsJsonObject().get("range"), DEFAULTS.range));
	}

	// a bad one throws, which would drop the typed numbers on the next save
	private static <T> T pick(JsonElement json, Class<T> type) {
		try {
			return ConfigFile.GSON.fromJson(json, type);
		} catch (RuntimeException e) {
			return null;
		}
	}

	public CalculatorSettings withTyped(String key, Double value) {
		Map<String, Double> map = new LinkedHashMap<>(typed);
		if (value == null) map.remove(key);
		else map.put(key, value);
		return new CalculatorSettings(map, keyLoot, minedShafts, range);
	}

	public CalculatorSettings withoutTyped() {
		return new CalculatorSettings(Map.of(), keyLoot, minedShafts, range);
	}

	public CalculatorSettings withKeyLoot(KeyLoot value) {
		return new CalculatorSettings(typed, value, minedShafts, range);
	}

	public CalculatorSettings withMinedShafts(MinedShafts value) {
		return new CalculatorSettings(typed, keyLoot, value, range);
	}

	public CalculatorSettings withRange(StatsRange value) {
		return new CalculatorSettings(typed, keyLoot, minedShafts, value);
	}
}
