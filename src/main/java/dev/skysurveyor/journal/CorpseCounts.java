package dev.skysurveyor.journal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.journal.JournalStats.CorpseCount;

// tab list flips to LOOTED 0.7 to 3 s after the loot msg, loot + leave = saved not looted
// looted = max(tab, chat loots), total = max(tab, looted)
public final class CorpseCounts {
	private CorpseCounts() {
	}

	public static Map<String, CorpseCount> of(JournalEntry entry) {
		return of(entry.corpses(), entry.loots());
	}

	public static Map<String, CorpseCount> of(Shaft shaft) {
		return of(shaft.corpses(), shaft.loots());
	}

	// corpse type order, a type only in chat gets a row too
	public static Map<String, CorpseCount> of(List<Corpse> corpses, List<CorpseLoot> loots) {
		Map<String, int[]> counts = new LinkedHashMap<>();
		for (Corpse corpse : corpses) {
			int[] count = counts.computeIfAbsent(corpse.type(), t -> new int[3]);
			if (corpse.looted()) count[0]++;
			count[1]++;
		}
		for (CorpseLoot loot : loots) counts.computeIfAbsent(loot.type(), t -> new int[3])[2]++;
		Map<String, CorpseCount> result = new LinkedHashMap<>();
		for (String type : JournalStats.ordered(counts.keySet())) {
			int[] count = counts.get(type);
			int looted = Math.max(count[0], count[2]);
			result.put(type, new CorpseCount(looted, Math.max(count[1], looted)));
		}
		return result;
	}

	// like of(entry).get(type).looted(), w/o building every type
	public static int looted(JournalEntry entry, String type) {
		int tab = 0;
		for (Corpse corpse : entry.corpses()) {
			if (corpse.looted() && corpse.type().equals(type)) tab++;
		}
		int chat = 0;
		for (CorpseLoot loot : entry.loots()) {
			if (loot.type().equals(type)) chat++;
		}
		return Math.max(tab, chat);
	}

	public static int looted(Map<String, CorpseCount> counts) {
		return counts.values().stream().mapToInt(CorpseCount::looted).sum();
	}

	public static int total(Map<String, CorpseCount> counts) {
		return counts.values().stream().mapToInt(CorpseCount::total).sum();
	}
}
