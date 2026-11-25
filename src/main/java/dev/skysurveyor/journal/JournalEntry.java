package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.mining.MiningStats;
import dev.skysurveyor.money.ItemPrice;

// times epoch ms. new fields at the end, bump VERSION if a meaning changes
// sessionId = session start time, campfiresAt relative to enteredAt
// searchMillis null = not timed (someone elses portal or no block mined)
// crystals, otherDrops, prices null in entries before 0.14.0
// prices = by skyblock id at the end of the shaft, empty when there were none
// campfireColds = cold before each campfire use, null in entries before 0.22.0, null items = no cold line
// metal = shafts own metal, null = none or before 0.23.0 (those keep it in otherDrops)
// keysSaved = loots indexes whose key resourceful saved, null = none or before 0.24.0
// sacksAdded, sacksRemoved = raw sacks msgs. gems, otherDrops, metal have compaction taken off since 0.25.0
// otherDrops has rare drops from chat since 0.25.0, also ones that never reached sacks
// mining = blocks, break times, boost (efficiency), null = no block broken or before 0.26.0
// fossil = fossil blocks + essence bars, null = none or before 0.26.0-alpha.22
public record JournalEntry(
		int version,
		long sessionId,
		String shaftId,
		long enteredAt,
		long durationMillis,
		String exit,
		long exitPowder,
		Long searchMillis,
		List<Corpse> corpses,
		int extraCorpses,
		List<CorpseLoot> loots,
		List<String> mayhemBuffs,
		List<Long> campfiresAt,
		Integer coldAtExit,
		int scrapFound,
		int pristineProcs,
		Map<String, Long> pristineItems,
		Map<String, Long> sacksAdded,
		Map<String, Long> sacksRemoved,
		GemYield gems,
		List<String> crystals,
		Map<String, Long> otherDrops,
		Map<String, ItemPrice> prices,
		List<Integer> campfireColds,
		MetalYield metal,
		List<Integer> keysSaved,
		MiningStats mining,
		FossilYield fossil) {
	public static final int VERSION = 1;

	public static JournalEntry of(Shaft shaft, long sessionId, Long searchMillis, TextPatterns patterns) {
		return new JournalEntry(VERSION, sessionId, shaft.id() == null ? null : shaft.id().id(),
				shaft.enteredAt(), shaft.durationMillis(),
				// null = still inside (live coins)
				shaft.exitReason() == null ? null : shaft.exitReason().name(), shaft.exitPowder(), searchMillis,
				List.copyOf(shaft.corpses()), shaft.extraCorpses(), List.copyOf(shaft.loots()),
				List.copyOf(shaft.mayhemBuffs()),
				shaft.campfiresPlaced().stream().map(at -> at - shaft.enteredAt()).toList(),
				shaft.cold(), shaft.scrapFound(), shaft.pristineProcs(),
				new LinkedHashMap<>(shaft.pristineItems()), new LinkedHashMap<>(shaft.sacksAdded()), new LinkedHashMap<>(shaft.sacksRemoved()),
				GemYield.of(shaft, patterns), List.copyOf(shaft.crystals()), otherDrops(shaft, patterns), null,
				// list.copyOf hates nulls
				Collections.unmodifiableList(new ArrayList<>(shaft.campfireColds())), MetalYield.of(shaft),
				shaft.keysSaved().isEmpty() ? null : List.copyOf(shaft.keysSaved()), null, FossilYield.of(shaft));
	}

	public JournalEntry withPrices(Map<String, ItemPrice> prices) {
		return with(prices, mining);
	}

	public JournalEntry withMining(MiningStats mining) {
		return with(prices, mining);
	}

	private JournalEntry with(Map<String, ItemPrice> prices, MiningStats mining) {
		return new JournalEntry(version, sessionId, shaftId, enteredAt, durationMillis, exit, exitPowder, searchMillis,
				corpses, extraCorpses, loots, mayhemBuffs, campfiresAt, coldAtExit, scrapFound, pristineProcs,
				pristineItems, sacksAdded, sacksRemoved, gems, crystals, otherDrops, prices, campfireColds, metal, keysSaved, mining,
				fossil);
	}

	public boolean keySaved(int loot) {
		return keysSaved != null && keysSaved.contains(loot);
	}

	// key item -> how many resourceful saved
	public Map<String, Integer> savedKeys() {
		Map<String, Integer> saved = new LinkedHashMap<>();
		for (int i = 0; i < loots.size(); i++) {
			String key = CorpseType.key(loots.get(i).type());
			if (key != null && keySaved(i)) saved.merge(key, 1, Integer::sum);
		}
		return saved;
	}

	// sacks minus compaction, corpse loot, rare drops, supercrafts and the shafts own gem + metal (GemYield, MetalYield)
	// plus rare drops from chat (since 0.25.0), own gem too bc its a mob drop
	static Map<String, Long> otherDrops(Shaft shaft, TextPatterns patterns) {
		Map<String, Long> drops = new LinkedHashMap<>(shaft.sacksNet());
		shaft.notMinedInSacks().forEach((item, count) -> drops.merge(item, -count, Long::sum));
		String type = shaft.id() == null ? null : shaft.id().type();
		drops.replaceAll((item, count) -> GemYield.isShaftGem(item, type, patterns) || MetalYield.isShaftMetal(item, type) ? 0
				: Math.max(0, count));
		shaft.rareDrops().forEach((item, count) -> drops.merge(item, count, Long::sum));
		drops.values().removeIf(count -> count <= 0);
		return drops;
	}

	// null = no id seen
	public long endedAt() {
		return enteredAt + durationMillis;
	}

	public String shaftType() {
		return idPart("type");
	}

	// f.e. "1", "2", "C" crystal, "L" littlefoot
	public String variant() {
		return idPart("variant");
	}

	private String idPart(String group) {
		if (shaftId == null) return null;
		Matcher m = TextPatterns.defaults().match("shaft.id", shaftId);
		return m == null ? null : m.group(group);
	}

	// null = still inside, unknown name = UNKNOWN
	public Shaft.ExitReason exitReason() {
		if (exit == null) return null;
		for (Shaft.ExitReason reason : Shaft.ExitReason.values()) {
			if (reason.name().equals(exit)) return reason;
		}
		return Shaft.ExitReason.UNKNOWN;
	}

	public long fossilEssence() {
		return fossil == null ? 0 : fossil.essence();
	}

	public long corpsePowder() {
		return loots.stream().mapToLong(CorpseLoot::powder).sum();
	}
}
