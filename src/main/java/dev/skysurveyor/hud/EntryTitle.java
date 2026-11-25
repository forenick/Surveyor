package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// title on entering a shaft. reads the live shaft, mayhem can come before the scoreboard id. plain java
public final class EntryTitle {
	// display choices, not game numbers
	static final long FADE_IN_MILLIS = 250;
	static final long FADE_OUT_MILLIS = 500;

	// alpha 0 to 1
	public record Text(String title, List<String> lines, float alpha) {
	}

	private final ShaftNames names;
	private final Predicate<String> hasFossil;
	private Shaft shaft;
	private long shownAt;
	private long changedAt;
	private List<String> lastLines;

	public EntryTitle(ShaftNames names, Predicate<String> hasFossil) {
		this.names = names;
		this.hasFossil = hasFossil;
	}

	public void onEvent(ShaftEvent event, long now) {
		switch (event) {
			case ShaftEntered entered -> {
				shaft = entered.shaft();
				shownAt = now;
				changedAt = now;
				lastLines = null;
			}
			case ShaftLeft left -> shaft = null;
			default -> {
			}
		}
	}

	// null = nothing to show. stays seconds after the last change, then done for this shaft
	public Text text(long now, double seconds) {
		if (shaft == null || shaft.id() == null) return null;
		List<String> lines = lines(shaft);
		if (!lines.equals(lastLines)) {
			if (lastLines != null) changedAt = now;
			lastLines = lines;
		}
		long left = Math.round(seconds * 1000) - (now - changedAt);
		if (left <= 0) {
			shaft = null;
			return null;
		}
		float in = Math.min(1, (now - shownAt) / (float) FADE_IN_MILLIS);
		float out = Math.min(1, left / (float) FADE_OUT_MILLIS);
		return new Text(title(shaft), lines, Math.max(0, Math.min(in, out)));
	}

	String title(Shaft shaft) {
		String type = shaft.id().type();
		Character color = names.hudColor(type);
		String variant = shaft.id().variant();
		// _1 + littlefoot say nothing
		String suffix = variant.equals("2") || variant.equals(ShaftNames.CRYSTAL) ? " §7(" + ShaftNames.variantName(variant) + ")" : "";
		return (color == null ? "§f" : "§" + color) + names.name(type) + " Mineshaft" + suffix;
	}

	List<String> lines(Shaft shaft) {
		List<String> lines = new ArrayList<>();
		String corpses = corpses(shaft.corpses());
		if (corpses != null) lines.add(corpses);
		if (hasFossil.test(shaft.id().id())) lines.add("§dFossil");
		for (String buff : shaft.mayhemBuffs()) lines.add("§7Mayhem: §f" + buff);
		return lines;
	}

	// counts not looted, so a loot doesnt keep it up. null = tab list not there yet
	static String corpses(List<Corpse> corpses) {
		if (corpses.isEmpty()) return null;
		Map<String, Integer> counts = new LinkedHashMap<>();
		for (String type : CorpseType.ORDER) counts.put(type, 0);
		for (Corpse corpse : corpses) counts.merge(corpse.type(), 1, Integer::sum);
		List<String> parts = new ArrayList<>();
		counts.forEach((type, count) -> {
			if (count == 0) return;
			parts.add(CorpseType.color(type) + (count == 1 ? type : count + " " + type));
		});
		return "§f" + corpses.size() + (corpses.size() == 1 ? " corpse: " : " corpses: ") + String.join("§7, ", parts);
	}
}
