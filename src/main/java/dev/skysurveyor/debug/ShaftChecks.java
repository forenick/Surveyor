package dev.skysurveyor.debug;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;

// the mods own sanity checks on a shaft u left. a failed one = a "check" mark in the recording. one line per check
public final class ShaftChecks {
	private ShaftChecks() {
	}

	public static List<String> failed(Shaft shaft) {
		List<String> failed = new ArrayList<>();
		corpseLoots(shaft.corpses(), shaft.loots(), failed);
		return failed;
	}

	// tab list LOOTED vs loot msgs. leaving right after a loot = tab 1 short, thats normal (journal/CorpseCounts)
	static void corpseLoots(List<Corpse> corpses, List<CorpseLoot> loots, List<String> failed) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		for (Corpse corpse : corpses) {
			if (corpse.looted()) counts.computeIfAbsent(corpse.type(), t -> new int[2])[0]++;
		}
		for (CorpseLoot loot : loots) counts.computeIfAbsent(loot.type(), t -> new int[2])[1]++;
		counts.forEach((type, count) -> {
			if (count[0] > count[1] || count[1] > count[0] + 1) {
				failed.add("corpse loots: " + type + " tab list " + count[0] + " looted, chat " + count[1]);
			}
		});
	}
}
