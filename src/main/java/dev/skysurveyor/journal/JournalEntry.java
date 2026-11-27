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

// epoch ms. new fields at the end, bump VERSION if a meaning changes
// sessionId = session start, campfiresAt relative to enteredAt
// searchMillis null = not timed (not ur portal or no block mined)
// crystals, otherDrops, prices null before 0.14.0
// prices = by skyblock id at shaft end, empty = none
// campfireColds = cold before each use, null before 0.22.0, null item = no cold line
// metal = shafts own, null = none or before 0.23.0 (then in otherDrops)
// keysSaved = loot indexes resourceful saved, null = none or before 0.24.0
// sacksAdded/Removed = raw msgs. gems, otherDrops, metal net of compaction since 0.25.0
// otherDrops has chat rare drops since 0.25.0, sacks or not
// mining = blocks, break times, boost. null = no block broken or before 0.26.0
// fossil = blocks + essence bars, null = none or before 0.26.0-alpha.22
// fiesta = on when u left, null = unknown or before 0.27.0-alpha.3
// flaskMillis = time inside w/ flask running, null = before 0.27.0-alpha.3
// spawnBlock = loop main block at spawn, f.e. green_stained_glass. null = not ur portal or before 0.27.0-alpha.3
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
		FossilYield fossil,
		Boolean fiesta,
		Long flaskMillis,
		String spawnBlock) {
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
				shaft.keysSaved().isEmpty() ? null : List.copyOf(shaft.keysSaved()), null, FossilYield.of(shaft), null, null, null);
	}

	public JournalEntry withPrices(Map<String, ItemPrice> prices) {
		return with(prices, mining, fiesta, flaskMillis, spawnBlock);
	}

	public JournalEntry withMining(MiningStats mining) {
		return with(prices, mining, fiesta, flaskMillis, spawnBlock);
	}

	public JournalEntry withFiesta(Boolean fiesta, Long flaskMillis) {
		return with(prices, mining, fiesta, flaskMillis, spawnBlock);
	}

	public JournalEntry withSpawnBlock(String spawnBlock) {
		return with(prices, mining, fiesta, flaskMillis, spawnBlock);
	}

	private JournalEntry with(Map<String, ItemPrice> prices, MiningStats mining, Boolean fiesta, Long flaskMillis, String spawnBlock) {
		return new JournalEntry(version, sessionId, shaftId, enteredAt, durationMillis, exit, exitPowder, searchMillis,
				corpses, extraCorpses, loots, mayhemBuffs, campfiresAt, coldAtExit, scrapFound, pristineProcs,
				pristineItems, sacksAdded, sacksRemoved, gems, crystals, otherDrops, prices, campfireColds, metal, keysSaved, mining,
				fossil, fiesta, flaskMillis, spawnBlock);
	}

	public boolean keySaved(int loot) {
		return keysSaved != null && keysSaved.contains(loot);
	}

	public Map<String, Integer> savedKeys() {
		Map<String, Integer> saved = new LinkedHashMap<>();
		for (int i = 0; i < loots.size(); i++) {
			String key = CorpseType.key(loots.get(i).type());
			if (key != null && keySaved(i)) saved.merge(key, 1, Integer::sum);
		}
		return saved;
	}

	// sacks net minus corpse loot, rare drops, supercrafts, own gem + metal
	// plus chat rare drops (since 0.25.0), own gem too bc mob drop
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
