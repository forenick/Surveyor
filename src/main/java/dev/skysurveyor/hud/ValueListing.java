package dev.skysurveyor.hud;

import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

import dev.skysurveyor.hud.HudLayout.Box;

// what the add line page + the value list show, in HudValue.listed() order
final class ValueListing {
	private ValueListing() {
	}

	// one row per value, shafts today + per hour are periods of shafts
	static List<HudValue> addLine(HudValue.Group group) {
		return HudValue.listed().stream().filter(value -> value.group() == group && value.inAddLine()).toList();
	}

	// tags only, drops has none
	static List<HudValue> tags(HudValue.Group group, String search) {
		String query = search.strip().toLowerCase(Locale.ROOT);
		return HudValue.listed().stream().filter(value -> value.group() == group && value.isTag() && matches(value, query)).toList();
	}

	// query lowercase + stripped
	static boolean matches(HudValue value, String query) {
		return query.isEmpty() || value.tag().contains(query) || value.id().contains(query)
				|| value.displayName().toLowerCase(Locale.ROOT).contains(query);
	}

	// periodless value already in the box
	static boolean has(Box box, HudValue value) {
		return box.lines.stream().anyMatch(line -> line.hudValue() == value);
	}

	static boolean has(Box box, HudValue.Period period) {
		return box.lines.stream().anyMatch(line -> period.shows(line.hudValue(), line.range()));
	}

	// full words for every value or short ones for all, mixed looked messy. tick = width of the ✔ a period in the box gets
	static boolean periodsFit(Box box, int room, int gap, int tick, ToIntFunction<String> width) {
		for (HudValue value : HudValue.listed()) {
			List<HudValue.Period> periods = value.periods();
			if (!value.inAddLine() || periods.isEmpty()) continue;
			int total = (periods.size() - 1) * gap;
			for (HudValue.Period period : periods) {
				total += width.applyAsInt(period.name().toLowerCase(Locale.ROOT));
				if (has(box, period)) total += tick;
			}
			if (total > room) return false;
		}
		return true;
	}
}
