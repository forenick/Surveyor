package dev.skysurveyor.settings;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.journal.PersonalBests.Def;

// "personalBests" in config.json, the Stats tabs list in order. names never rename
public record PersonalBestSettings(List<Def> list) {
	public static final PersonalBestSettings DEFAULTS = new PersonalBestSettings(PersonalBests.DEFAULTS);

	public PersonalBestSettings {
		list = List.copyOf(list);
	}

	// null = default. by hand, a broken entry is skipped, the rest still load
	static PersonalBestSettings read(JsonElement json) {
		if (!json.isJsonObject() || !json.getAsJsonObject().has("list") || !json.getAsJsonObject().get("list").isJsonArray()) return null;
		List<Def> list = new ArrayList<>();
		for (JsonElement item : json.getAsJsonObject().getAsJsonArray("list")) {
			if (!item.isJsonObject()) continue;
			JsonObject pb = item.getAsJsonObject();
			String stat = text(pb, "stat");
			if (stat == null || PersonalBests.Stat.byId(stat) == null) continue;
			String best = text(pb, "best");
			if (best != null && !best.equals(Def.LOWEST) && !best.equals(Def.HIGHEST)) best = null;
			list.add(new Def(stat, text(pb, "shaft"), best, text(pb, "name")));
		}
		return new PersonalBestSettings(list);
	}

	private static String text(JsonObject json, String key) {
		JsonElement value = json.get(key);
		return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
	}

	public PersonalBestSettings withList(List<Def> value) {
		return new PersonalBestSettings(value);
	}
}
