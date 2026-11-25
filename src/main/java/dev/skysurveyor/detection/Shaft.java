package dev.skysurveyor.detection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.ShaftEvent.CampfirePlaced;
import dev.skysurveyor.detection.ShaftEvent.CampfireUsed;
import dev.skysurveyor.detection.ShaftEvent.CaveIn;
import dev.skysurveyor.detection.ShaftEvent.CorpseLooted;
import dev.skysurveyor.detection.ShaftEvent.CrystalFound;
import dev.skysurveyor.detection.ShaftEvent.CrystalOwned;
import dev.skysurveyor.detection.ShaftEvent.ExitBonus;
import dev.skysurveyor.detection.ShaftEvent.ExtraCorpses;
import dev.skysurveyor.detection.ShaftEvent.FossilEssence;
import dev.skysurveyor.detection.ShaftEvent.FrozeToDeath;
import dev.skysurveyor.detection.ShaftEvent.KeySaved;
import dev.skysurveyor.detection.ShaftEvent.MayhemBuff;
import dev.skysurveyor.detection.ShaftEvent.Pristine;
import dev.skysurveyor.detection.ShaftEvent.RareDrop;
import dev.skysurveyor.detection.ShaftEvent.SacksChanged;
import dev.skysurveyor.detection.ShaftEvent.ScrapFound;
import dev.skysurveyor.detection.ShaftEvent.Supercraft;
import dev.skysurveyor.detection.ShaftEvent.WarpFailed;
import dev.skysurveyor.detection.ShaftEvent.Warping;

// only ShaftTracker changes it, final once exitReason is set
public final class Shaft {
	public enum ExitReason {
		BYE,
		WARP,
		DEATH,
		UNKNOWN
	}

	private final long enteredAt;
	private long leftAt = -1;
	private ExitReason exitReason;
	boolean confirmed;
	private ShaftId id;
	private ExitReason pendingExit;
	private long exitPowder;
	private Integer cold;
	private List<Corpse> corpses = List.of();
	private int extraCorpses;
	private final List<CorpseLoot> loots = new ArrayList<>();
	// loot indexes whose key resourceful saved
	private final List<Integer> keysSaved = new ArrayList<>();
	// msg before its loot block, -1 = none
	private long keySavedAt = -1;
	private long lastKeyLootAt = -1;
	private int lastKeyLoot = -1;
	private final List<String> mayhemBuffs = new ArrayList<>();
	private final List<Long> campfiresPlaced = new ArrayList<>();
	// scoreboard still shows the cold from before the -50 (recording 1)
	private final List<Integer> campfireColds = new ArrayList<>();
	private long caveInAt = -1;
	private int scrapFound;
	private final List<String> crystals = new ArrayList<>();
	private int pristineProcs;
	private final Map<String, Long> pristineItems = new LinkedHashMap<>();
	private final Map<String, Long> sacksAdded = new LinkedHashMap<>();
	private final Map<String, Long> sacksRemoved = new LinkedHashMap<>();
	private final Map<String, Long> sacksCompacted = new LinkedHashMap<>();
	private final Map<String, Long> craftedSinceSacks = new LinkedHashMap<>();
	private long lastSacksAt = -1;
	private final Map<String, Long> pristineSinceSacks = new LinkedHashMap<>();
	// counted from chat, like corpse loot
	private final Map<String, Long> rareDrops = new LinkedHashMap<>();
	// corpse loot, rare drops + supercrafts wait here until a sacks msg reports them
	private final Map<String, Long> notMinedPending = new LinkedHashMap<>();
	private final Map<String, Long> notMinedInSacks = new LinkedHashMap<>();
	// display choice, unconfirmed. loot block + msg come in the same click
	static final long KEY_SAVED_WINDOW = 3_000;
	// fossil blocks u broke, the bar only shows the last ones essence: essence = blocks x avg of the bars after a break
	private int fossilBlocks;
	private int essenceSamples;
	private long essenceShown;
	private boolean essencePending;
	// goes up on every change but cold, so callers can cache
	private int version;

	Shaft(long enteredAt) {
		this.enteredAt = enteredAt;
	}

	void apply(ShaftEvent event, long now) {
		version++;
		switch (event) {
			case ExitBonus bye -> {
				pendingExit = ExitReason.BYE;
				exitPowder = bye.powder();
			}
			case Warping warping -> pendingExit = ExitReason.WARP;
			case WarpFailed failed -> {
				if (pendingExit == ExitReason.WARP) pendingExit = null;
			}
			case FrozeToDeath death -> pendingExit = ExitReason.DEATH;
			case MayhemBuff mayhem -> mayhemBuffs.add(mayhem.buff());
			case ExtraCorpses extra -> extraCorpses += extra.count();
			case CaveIn caveIn -> caveInAt = now;
			case CorpseLooted looted -> {
				loots.add(looted.loot());
				looted.loot().items().forEach(item -> add(notMinedPending, item));
				if (CorpseType.key(looted.loot().type()) != null) {
					lastKeyLootAt = now;
					lastKeyLoot = loots.size() - 1;
					if (keySavedAt >= 0 && now - keySavedAt <= KEY_SAVED_WINDOW) keySaved();
				}
			}
			case KeySaved saved -> {
				// msg order vs the loot block unrecorded, so either way
				if (lastKeyLootAt >= 0 && now - lastKeyLootAt <= KEY_SAVED_WINDOW && !keysSaved.contains(lastKeyLoot)) {
					keySaved();
				} else {
					keySavedAt = now;
				}
			}
			case Supercraft craft -> {
				add(notMinedPending, craft.result());
				add(craftedSinceSacks, craft.result());
			}
			case RareDrop drop -> {
				add(rareDrops, drop.item());
				add(notMinedPending, drop.item());
			}
			case CampfirePlaced placed -> campfiresPlaced.add(now);
			case CampfireUsed used -> campfireColds.add(cold);
			case ScrapFound scrap -> scrapFound++;
			// ShaftTracker samples it, not here
			case FossilEssence essence -> {
			}
			case CrystalFound crystal -> crystals.add(crystal.item());
			// each click runs twice: find = FOUND + 1 owned, owned crystal = 2 owned (recordings)
			case CrystalOwned owned -> {
			}
			case Pristine pristine -> {
				pristineProcs++;
				add(pristineItems, pristine.gems());
				add(pristineSinceSacks, pristine.gems());
			}
			case SacksChanged sacks -> {
				sacks.added().forEach(item -> add(sacksAdded, item));
				sacks.removed().forEach(item -> add(sacksRemoved, item));
				Compaction.of(sacks, craftedSinceSacks).forEach((item, count) -> sacksCompacted.merge(item, count, Long::sum));
				craftedSinceSacks.clear();
				lastSacksAt = now;
				pristineSinceSacks.clear();
				reported(sacks);
			}
			default -> {
			}
		}
	}

	void addFossilBlocks(int count) {
		version++;
		fossilBlocks += count;
		essencePending = true;
	}

	boolean essencePending() {
		return essencePending;
	}

	// 1st bar after a new block, lingering bars would weigh the last block twice. version only on a sample
	void sampleEssence(int amount) {
		if (!essencePending) return;
		version++;
		essenceSamples++;
		essenceShown += amount;
		essencePending = false;
	}

	void setId(ShaftId id) {
		version++;
		this.id = id;
	}

	// no version bump, cold changes every few secs and never changes coins
	void setCold(Integer cold) {
		this.cold = cold;
	}

	void setCorpses(List<Corpse> corpses) {
		version++;
		this.corpses = corpses;
	}

	void leave(long now) {
		version++;
		leftAt = now;
		exitReason = pendingExit == null ? ExitReason.UNKNOWN : pendingExit;
	}

	// capped hover ends in "+N other items", loot in there never reached sacksAdded so it isnt taken off (recording 8 PERI_C)
	private void reported(SacksChanged sacks) {
		if (sacks.otherAdded() > 0) {
			Map<String, Long> listed = new LinkedHashMap<>();
			sacks.added().forEach(item -> add(listed, item));
			notMinedPending.replaceAll((item, count) -> Math.min(count, listed.getOrDefault(item, 0L)));
		}
		notMinedPending.forEach((item, count) -> {
			if (count > 0) notMinedInSacks.merge(item, count, Long::sum);
		});
		notMinedPending.clear();
	}

	private void keySaved() {
		keysSaved.add(lastKeyLoot);
		keySavedAt = -1;
		lastKeyLootAt = -1;
	}

	private static void add(Map<String, Long> totals, ItemCount item) {
		totals.merge(item.item(), item.count(), Long::sum);
	}

	public int version() {
		return version;
	}

	public long enteredAt() {
		return enteredAt;
	}

	// -1 = still inside
	public long leftAt() {
		return leftAt;
	}

	public long durationMillis() {
		return leftAt < 0 ? -1 : leftAt - enteredAt;
	}

	public ExitReason exitReason() {
		return exitReason;
	}

	public ShaftId id() {
		return id;
	}

	public long exitPowder() {
		return pendingExit == ExitReason.BYE ? exitPowder : 0;
	}

	public Integer cold() {
		return cold;
	}

	// last non empty widget state
	public List<Corpse> corpses() {
		return corpses;
	}

	// already in corpses() too
	public int extraCorpses() {
		return extraCorpses;
	}

	public List<CorpseLoot> loots() {
		return Collections.unmodifiableList(loots);
	}

	public List<Integer> keysSaved() {
		return Collections.unmodifiableList(keysSaved);
	}

	public List<String> mayhemBuffs() {
		return Collections.unmodifiableList(mayhemBuffs);
	}

	public List<Long> campfiresPlaced() {
		return Collections.unmodifiableList(campfiresPlaced);
	}

	// null = cold line wasnt there
	public List<Integer> campfireColds() {
		return Collections.unmodifiableList(campfireColds);
	}

	public long caveInAt() {
		return caveInAt;
	}

	public int scrapFound() {
		return scrapFound;
	}

	public List<String> crystals() {
		return Collections.unmodifiableList(crystals);
	}

	public int fossilBlocks() {
		return fossilBlocks;
	}

	public int essenceSamples() {
		return essenceSamples;
	}

	// sum of the sampled bars
	public long essenceShown() {
		return essenceShown;
	}

	public int pristineProcs() {
		return pristineProcs;
	}

	// misses silent gemstone spread procs, sacksAdded() has real totals
	public Map<String, Long> pristineItems() {
		return Collections.unmodifiableMap(pristineItems);
	}

	// last few secs before leaving never get reported. gross, compaction not taken off
	public Map<String, Long> sacksAdded() {
		return Collections.unmodifiableMap(sacksAdded);
	}

	public Map<String, Long> sacksRemoved() {
		return Collections.unmodifiableMap(sacksRemoved);
	}

	// sacksAdded - compaction, over the whole shaft so a shortfall in one msg is paid by the others
	public Map<String, Long> sacksNet() {
		return new Compaction().net(sacksAdded, sacksCompacted);
	}

	// compacted here but added before the shaft, outside mining pays it (OutsideMining)
	public Map<String, Long> compactionOwed() {
		Compaction compaction = new Compaction();
		compaction.net(sacksAdded, sacksCompacted);
		return compaction.owed();
	}

	// gross w/o what compaction made, the tier mix as mined
	public Map<String, Long> sacksBeforeCompacting() {
		return Compaction.beforeCompacting(sacksAdded, sacksCompacted);
	}

	// start of the unreported tail, everything if no sacks msg yet
	public Map<String, Long> pristineSinceSacks() {
		return Collections.unmodifiableMap(pristineSinceSacks);
	}

	public Map<String, Long> rareDrops() {
		return Collections.unmodifiableMap(rareDrops);
	}

	public Map<String, Long> notMinedInSacks() {
		return Collections.unmodifiableMap(notMinedInSacks);
	}

	public long lastSacksAt() {
		return lastSacksAt;
	}
}
