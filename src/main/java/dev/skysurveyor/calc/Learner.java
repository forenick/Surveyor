package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import dev.skysurveyor.calc.Fiesta.State;
import dev.skysurveyor.calc.YourNumbers.Num;
import dev.skysurveyor.calc.YourNumbers.PerState;
import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.calc.YourNumbers.StateNum;
import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.journal.StatsRange;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.mining.MiningStats;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.money.ShaftValue.Part;
import dev.skysurveyor.util.Stats;

// typed on top (calculator-design.md)
public final class Learner {
	// display choices, min before tracked
	public static final int MIN_SPAWNS = 5;
	static final int MIN_SHAFTS = 10;
	static final int MIN_CORPSES = 5;
	static final int MIN_LOOTS = 20;
	// own avg from 20 corpses of the type (nick 11-25)
	static final int MIN_KEY_CORPSES = 20;
	static final int MIN_BACK = 3;
	// display choice
	static final int MIN_SESSION_SPAWNS = 5;
	// under 2 min = skipped, journals rule (research/10 s1)
	static final long SKIPPED_MILLIS = 120_000;
	// longer = a break, not a cycle. display choice
	static final long MAX_CYCLE_MILLIS = 600_000;
	// display choice, keeps a bad fit sane
	// min boost blocks for lost per block, + its cap. display choices
	public static final int MIN_BOOST_BLOCKS = 50;
	static final double MAX_LOST_TICKS = 10;

	private Learner() {
	}

	// sessions newest first. values + outside at the players price settings
	// allTime = every shaft, for corpse odds + layouts. null = the range
	// state null = unknown. glossy = glossy price, null = none
	// minedShafts = mining numbers from this many newest mined shafts of allTime, 0 = the ones in the range
	public record Input(List<SessionSummary> sessions, Function<JournalEntry, ShaftValue> values, Function<MiningEntry, Double> outside,
			Map<String, Double> typed, long patchStart, TextPatterns patterns, State state, Double glossy, List<JournalEntry> allTime,
			int minedShafts) {
		public Input(List<SessionSummary> sessions, Function<JournalEntry, ShaftValue> values, Function<MiningEntry, Double> outside,
				Map<String, Double> typed, long patchStart, TextPatterns patterns) {
			this(sessions, values, outside, typed, patchStart, patterns, null, null, null, 0);
		}

		public Input(List<SessionSummary> sessions, Function<JournalEntry, ShaftValue> values, Function<MiningEntry, Double> outside,
				Map<String, Double> typed, long patchStart, TextPatterns patterns, State state, Double glossy) {
			this(sessions, values, outside, typed, patchStart, patterns, state, glossy, null, 0);
		}

		public Input(List<SessionSummary> sessions, Function<JournalEntry, ShaftValue> values, Function<MiningEntry, Double> outside,
				Map<String, Double> typed, long patchStart, TextPatterns patterns, State state, Double glossy, List<JournalEntry> allTime) {
			this(sessions, values, outside, typed, patchStart, patterns, state, glossy, allTime, 0);
		}
	}

	private record Tracked(double value, int basis, String unit) {
	}

	// from mined shafts (or freezes), so they follow Input.minedShafts, not the range
	public static boolean fromMinedShafts(NumberId id) {
		return switch (id) {
			case EFFICIENCY, SETUP, BOOST, SPREAD, LOST_TICKS, GEMS_PER_BLOCK, OTHER, FLAWED, FREEZE, BOOST_TICKS, TICKS_4000, TICKS_3500, TICKS_3000,
					TICKS_2500 -> true;
			default -> false;
		};
	}

	public static YourNumbers learn(Input in, GameData data) {
		List<JournalEntry> entries = StatsRange.flat(in.sessions(), SessionSummary::entries);
		// perks rarely change + 20 shafts are off by 10 pts, so every shaft
		List<JournalEntry> allTime = in.allTime() == null ? entries : in.allTime();
		List<JournalEntry> everMined = allTime.stream().filter(entry -> mined(entry, data)).sorted(Comparator.comparingLong(JournalEntry::enteredAt))
				.toList();
		// newest few whatever the session: 5 short sessions dont empty it + new gear shows after 3 (nick 11-27)
		List<JournalEntry> mined = in.minedShafts() > 0 ? everMined.subList(Math.max(0, everMined.size() - in.minedShafts()), everMined.size())
				: entries.stream().filter(entry -> mined(entry, data)).toList();
		Map<NumberId, Tracked> tracked = new EnumMap<>(NumberId.class);
		put(tracked, NumberId.SPAWN, spawn(in.sessions()));
		put(tracked, NumberId.NOT_MINING, notMining(in.sessions()));
		put(tracked, NumberId.BACK_MINED, backMined(in.sessions()));
		// fiesta unknown: all shafts together, else the 3 below via perState
		put(tracked, NumberId.COINS_PER_SPAWN, coinsPerSpawn(in));
		if (!mined.isEmpty()) {
			int n = mined.size();
			put(tracked, NumberId.EFFICIENCY, new Tracked(median(mined, e -> Efficiency.shown(e.mining())), n, "shafts"));
			put(tracked, NumberId.SETUP, new Tracked(median(mined, e -> e.mining().setupMillis() / 1000.0), n, "shafts"));
			put(tracked, NumberId.BOOST, new Tracked(median(mined, e -> share(e.mining().boostMillis(), e.mining().millis())), n, "shafts"));
			put(tracked, NumberId.SPREAD, new Tracked(median(mined, e -> share(e.mining().spread(), e.mining().blocks())), n, "shafts"));
			List<Double> lost = mined.stream().map(e -> lostTicks(e.mining())).filter(d -> d != null).toList();
			if (!lost.isEmpty()) put(tracked, NumberId.LOST_TICKS, new Tracked(Stats.median(lost), lost.size(), "shafts"));
			put(tracked, NumberId.GEMS_PER_BLOCK, roughPerBlock(mined));
			put(tracked, NumberId.OTHER, otherPerMinute(mined, in.values()));
		}
		put(tracked, NumberId.LAPIS_LOOT, corpseLoot(entries, in.values(), type -> type.equals(CorpseType.LAPIS.label()), MIN_CORPSES));
		// rare drops need lots of corpses
		put(tracked, NumberId.KEY_LOOT, corpseLoot(entries, in.values(), Learner::isKeyCorpse, MIN_KEY_CORPSES));
		put(tracked, NumberId.EXTRA_ROLL, extraRoll(allTime));
		// cold resistance is gear too
		FreezeClock.Prior prior = in.minedShafts() > 0 ? FreezeClock.prior(allTime, in.minedShafts()) : FreezeClock.prior(entries, Integer.MAX_VALUE);
		if (prior != null) {
			put(tracked, NumberId.FREEZE, new Tracked(ColdModel.secondsUntil(ColdModel.FREEZE_COLD, prior.perCold()), prior.freezes(), "freezes"));
		}
		SpawnGem spawned = spawnGem(entries, data);
		String spawnGem = spawned != null ? spawned.code() : spawnGem(StatsRange.flat(in.sessions(), SessionSummary::mining), in.patterns(), data);
		if (spawned != null) {
			put(tracked, NumberId.SPAWN_GEM, spawned.share());
		} else if (spawnGem != null && entries.size() >= MIN_SHAFTS) {
			long of = entries.stream().filter(e -> spawnGem.equals(e.shaftType())).count();
			put(tracked, NumberId.SPAWN_GEM, new Tracked((double) of / entries.size(), entries.size(), "shafts"));
		}
		corpseRules(allTime, tracked);

		Map<NumberId, Map<State, StateNum>> states = perState(in, mined, data);
		Map<NumberId, Num> out = new EnumMap<>(NumberId.class);
		for (NumberId id : NumberId.values()) {
			// set below
			if (id == NumberId.CAMPFIRE || id == NumberId.FLAWED || id.kind() == NumberId.Kind.TICKS) continue;
			if (in.state() != null && Fiesta.perState(id)) out.put(id, states.get(id).get(in.state()).num());
			else out.put(id, resolve(id, tracked.get(id), in.typed(), data.start(id.key())));
		}
		ticks(mined, in.patchStart(), in.typed(), data, out);
		flawed(mined, in.typed(), data, out);
		out.put(NumberId.CAMPFIRE, new Num(ColdModel.campfireSeconds(out.get(NumberId.FREEZE).value(), 1), Source.CALCULATED, null, 0, null));
		YourNumbers numbers = new YourNumbers(out, spawnGem, new PerState(in.state(), states));
		return numbers.withLayouts(Layouts.learn(numbers, mined, everMined, allTime, in.typed(), data));
	}

	// per fiesta state (calculator-design.md "Next slice")
	// pre-fiesta shafts (null) go in the pool, part-flask ones nowhere
	private static Map<NumberId, Map<State, StateNum>> perState(Input in, List<JournalEntry> mined, GameData data) {
		Map<State, List<JournalEntry>> shafts = new EnumMap<>(State.class);
		for (State state : State.values()) shafts.put(state, new ArrayList<>());
		List<JournalEntry> pool = new ArrayList<>();
		for (JournalEntry entry : mined) {
			State state = stateOf(entry);
			if (entry.fiesta() == null) pool.add(entry);
			else if (state != null) shafts.get(state).add(entry);
		}
		Map<State, Tracked> gems = new EnumMap<>(State.class);
		Map<State, Tracked> other = new EnumMap<>(State.class);
		Map<State, Tracked> coins = new EnumMap<>(State.class);
		for (State state : State.values()) {
			put(gems, state, roughPerBlock(shafts.get(state)));
			put(other, state, otherPerMinute(shafts.get(state), in.values()));
			put(coins, state, coinsPerSpawn(in, entry -> entry.fiesta() != null && stateOf(entry) == state));
		}
		Map<NumberId, Map<State, StateNum>> out = new EnumMap<>(NumberId.class);
		out.put(NumberId.GEMS_PER_BLOCK, resolveStates(NumberId.GEMS_PER_BLOCK, gems, finite(roughPerBlock(pool)), in.typed(),
				state -> data.start(NumberId.GEMS_PER_BLOCK.key()) * FiestaScale.fortune(state, data),
				(from, to, value, typed) -> FiestaScale.byFortune(value, from, to, data)));
		out.put(NumberId.OTHER, resolveStates(NumberId.OTHER, other, finite(otherPerMinute(pool, in.values())), in.typed(),
				state -> data.start(NumberId.OTHER.key()), (from, to, value, typed) -> {
					// typed has no glossy part to take out
					Tracked scaled = typed ? null : finite(otherScaled(shafts.get(from), in.values(), to, in.glossy(), data));
					return scaled == null ? value : scaled.value();
				}));
		out.put(NumberId.COINS_PER_SPAWN, resolveStates(NumberId.COINS_PER_SPAWN, coins, coinsPerSpawn(in, entry -> entry.fiesta() == null),
				in.typed(), state -> data.start(NumberId.COINS_PER_SPAWN.key()) * FiestaScale.fortune(state, data),
				(from, to, value, typed) -> FiestaScale.byFortune(value, from, to, data)));
		return out;
	}

	static State stateOf(JournalEntry entry) {
		return Fiesta.of(entry.fiesta(), entry.flaskMillis(), entry.durationMillis());
	}

	private interface Scale {
		// typed = donor value typed, not tracked
		double to(State from, State to, double value, boolean typed);
	}

	// per state, first w/ a value: typed, tracked, scaled from another (typed first, else
	// most shafts), the pool, the starting value
	private static Map<State, StateNum> resolveStates(NumberId id, Map<State, Tracked> own, Tracked pool, Map<String, Double> typed,
			ToDoubleFunction<State> start, Scale scale) {
		Map<State, StateNum> out = new EnumMap<>(State.class);
		for (State state : State.values()) {
			Tracked mine = own.get(state);
			Double set = typed(typed, id, state);
			if (set != null) {
				out.put(state, new StateNum(num(set, Source.SET, mine), null, false));
				continue;
			}
			if (mine != null) {
				out.put(state, new StateNum(num(mine.value(), Source.TRACKED, mine), null, false));
				continue;
			}
			State donor = null;
			boolean donorTyped = false;
			for (State other : State.values()) {
				if (other != state && donor == null && typed(typed, id, other) != null) {
					donor = other;
					donorTyped = true;
				}
			}
			if (donor == null) {
				for (State other : State.values()) {
					if (other != state && own.get(other) != null && (donor == null || own.get(other).basis() > own.get(donor).basis())) donor = other;
				}
			}
			if (donor != null) {
				Tracked from = own.get(donor);
				double value = donorTyped ? typed(typed, id, donor) : from.value();
				out.put(state, new StateNum(new Num(scale.to(donor, state, value, donorTyped), Source.CALCULATED, null,
						donorTyped ? 0 : from.basis(), donorTyped ? null : from.unit()), donor, false));
			} else if (pool != null) {
				out.put(state, new StateNum(num(pool.value(), Source.TRACKED, pool), null, true));
			} else {
				out.put(state, new StateNum(new Num(start.applyAsDouble(state), Source.START, null, 0, null), null, false));
			}
		}
		return out;
	}

	private static Double typed(Map<String, Double> typed, NumberId id, State state) {
		Double value = typed.get(Fiesta.typedKey(id, state));
		return value != null && Double.isFinite(value) ? value : null;
	}

	private static Tracked finite(Tracked value) {
		return value != null && Double.isFinite(value.value()) ? value : null;
	}

	private static void put(Map<State, Tracked> tracked, State state, Tracked value) {
		if (finite(value) != null) tracked.put(state, value);
	}

	// tracked = what the shafts say, kept next to a typed value
	private static Num num(double value, Source source, Tracked tracked) {
		return tracked == null ? new Num(value, source, null, 0, null) : new Num(value, source, tracked.value(), tracked.basis(), tracked.unit());
	}

	// whole shaft of mining (Efficiency.RECORD_MILLIS), own gem as main block w/ its own ticks
	// before 0.26.0-alpha.17 ~11% blocks missed, spread counted every kind, stone in ticks
	// (efficiency-design.md)
	static boolean mined(JournalEntry entry, GameData data) {
		MiningStats mining = entry.mining();
		if (entry.gems() == null || Efficiency.record(mining) == null || mining.blocks() + mining.spread() <= 0) return false;
		GameData.Gem gem = data.gemOfBlock(mining.block());
		return mining.blockTicks() != null && gem != null && gem.code().equals(entry.shaftType());
	}

	private static void put(Map<NumberId, Tracked> tracked, NumberId id, Tracked value) {
		if (finite(value) != null) tracked.put(id, value);
	}

	// typed, else tracked, else the fallback
	private static Num resolve(NumberId id, Tracked tracked, Map<String, Double> typed, double fallback, Source fallbackSource) {
		Double set = typed.get(id.key());
		if (set != null && Double.isFinite(set)) return num(set, Source.SET, tracked);
		if (tracked != null) return num(tracked.value(), Source.TRACKED, tracked);
		return new Num(fallback, fallbackSource, null, 0, null);
	}

	private static Num resolve(NumberId id, Tracked tracked, Map<String, Double> typed, double start) {
		return resolve(id, tracked, typed, start, Source.START);
	}

	// median per session, then over sessions. all spawns if no session has enough
	private static Tracked spawn(List<SessionSummary> sessions) {
		List<Double> perSession = new ArrayList<>();
		List<Double> all = new ArrayList<>();
		int counted = 0;
		for (SessionSummary session : sessions) {
			List<Double> spawns = new ArrayList<>();
			for (JournalEntry entry : session.entries()) {
				if (entry.searchMillis() != null) spawns.add(entry.searchMillis() / 1000.0);
			}
			all.addAll(spawns);
			if (spawns.size() >= MIN_SESSION_SPAWNS) {
				perSession.add(Stats.median(spawns));
				counted += spawns.size();
			}
		}
		if (!perSession.isEmpty()) return new Tracked(Stats.median(perSession), counted, "spawns");
		return all.size() >= MIN_SPAWNS ? new Tracked(Stats.median(all), all.size(), "spawns") : null;
	}

	// a saved break covers last mining or exit till the next block, so a gap w/ one says nothing
	private static boolean paused(SessionSummary session, long from, long to) {
		for (BreakEntry pause : session.breaks()) {
			if (pause.from() < to && pause.to() > from) return true;
		}
		return false;
	}

	// after a skip, minus the spawn time
	private static Tracked notMining(List<SessionSummary> sessions) {
		List<Double> found = new ArrayList<>();
		for (SessionSummary session : sessions) {
			List<JournalEntry> entries = session.entries();
			for (int i = 1; i < entries.size(); i++) {
				JournalEntry before = entries.get(i - 1);
				JournalEntry entry = entries.get(i);
				if (before.durationMillis() >= SKIPPED_MILLIS || entry.searchMillis() == null) continue;
				if (paused(session, before.endedAt(), entry.enteredAt())) continue;
				long cycle = entry.enteredAt() - before.enteredAt();
				long rest = cycle - entry.searchMillis();
				if (cycle <= MAX_CYCLE_MILLIS && rest >= 0) found.add(rest / 1000.0);
			}
		}
		return found.size() >= MIN_SPAWNS ? new Tracked(Stats.median(found), found.size(), "spawns") : null;
	}

	// mined shaft exit to next entry, minus spawn. walk back from respawn is way longer than after a skip
	// gaps w/ a saved break out (8 of nicks 18, one made it 8m 51s), unsaved ones cut by the cycle cap
	private static Tracked backMined(List<SessionSummary> sessions) {
		List<Double> found = new ArrayList<>();
		for (SessionSummary session : sessions) {
			List<JournalEntry> entries = session.entries();
			for (int i = 1; i < entries.size(); i++) {
				JournalEntry before = entries.get(i - 1);
				JournalEntry entry = entries.get(i);
				if (before.durationMillis() < Efficiency.RECORD_MILLIS || entry.searchMillis() == null) continue;
				if (paused(session, before.endedAt(), entry.enteredAt())) continue;
				long rest = entry.enteredAt() - before.endedAt() - entry.searchMillis();
				if (rest >= 0 && rest <= MAX_CYCLE_MILLIS) found.add(rest / 1000.0);
			}
		}
		return found.size() >= MIN_BACK ? new Tracked(Stats.median(found), found.size(), "shafts") : null;
	}

	// sessions w/ outside coins saved (0.24.0-alpha.11 on)
	private static Tracked coinsPerSpawn(Input in) {
		double coins = 0;
		int spawns = 0;
		for (SessionSummary session : in.sessions()) {
			if (session.mining().isEmpty()) continue;
			long last = session.entries().stream().mapToLong(JournalEntry::enteredAt).max().orElse(Long.MIN_VALUE);
			for (MiningEntry entry : session.mining()) {
				// after the last shaft = a spawn that never came
				if (entry.at() > last) continue;
				Double value = in.outside().apply(entry);
				if (value != null) coins += value;
			}
			for (JournalEntry entry : session.entries()) {
				if (entry.searchMillis() != null) spawns++;
			}
		}
		return spawns >= MIN_SPAWNS ? new Tracked(coins / spawns, spawns, "spawns") : null;
	}

	// spawns ending in a counted shaft + coins mined on the way, none after the last shaft
	private static Tracked coinsPerSpawn(Input in, Predicate<JournalEntry> counts) {
		double coins = 0;
		int spawns = 0;
		for (SessionSummary session : in.sessions()) {
			if (session.mining().isEmpty() || session.entries().isEmpty()) continue;
			List<JournalEntry> shafts = session.entries().stream().sorted(Comparator.comparingLong(JournalEntry::enteredAt)).toList();
			for (MiningEntry entry : session.mining()) {
				JournalEntry ended = null;
				for (JournalEntry shaft : shafts) {
					if (shaft.enteredAt() >= entry.at()) {
						ended = shaft;
						break;
					}
				}
				if (ended == null || !counts.test(ended)) continue;
				Double value = in.outside().apply(entry);
				if (value != null) coins += value;
			}
			for (JournalEntry entry : shafts) {
				if (entry.searchMillis() != null && counts.test(entry)) spawns++;
			}
		}
		return spawns >= MIN_SPAWNS ? new Tracked(coins / spawns, spawns, "spawns") : null;
	}

	// rough before pristine = fine x 6400 / (blocks x (1 + 79 x flawed share)). own + spread
	private static Tracked roughPerBlock(List<JournalEntry> mined) {
		if (mined.isEmpty()) return null;
		double perTier = GemYield.RECIPE;
		List<Double> found = new ArrayList<>();
		for (JournalEntry entry : mined) {
			double p = flawedShare(entry.gems());
			int blocks = entry.mining().blocks() + entry.mining().spread();
			found.add(entry.gems().fineEquivalent() * perTier * perTier / (blocks * (1 + (perTier - 1) * p)));
		}
		return new Tracked(Stats.median(found), mined.size(), "shafts");
	}

	static double flawedShare(GemYield gems) {
		double rough = gems.rough() + gems.tailRough();
		double flawed = gems.flawed() + gems.tailFlawed();
		return rough + flawed <= 0 ? 0 : flawed / (rough + flawed);
	}

	// glossy, scrap, fossil + others per min inside. no crystals (crystal shafts only)
	private static Tracked otherPerMinute(List<JournalEntry> mined, Function<JournalEntry, ShaftValue> values) {
		List<Double> found = new ArrayList<>();
		for (JournalEntry entry : mined) {
			ShaftValue value = values.apply(entry);
			if (value == null) continue;
			double coins = value.total(Part.OTHER) + value.total(Part.GLOSSY) + value.total(Part.SCRAP) + value.total(Part.FOSSIL);
			found.add(coins / (entry.durationMillis() / 60_000.0));
		}
		return found.isEmpty() ? null : new Tracked(Stats.median(found), found.size(), "shafts");
	}

	// as if mined in another state: their glossy out, game-rate glossy in
	private static Tracked otherScaled(List<JournalEntry> donor, Function<JournalEntry, ShaftValue> values, State to, Double glossy,
			GameData data) {
		List<Double> found = new ArrayList<>();
		for (JournalEntry entry : donor) {
			ShaftValue value = values.apply(entry);
			if (value == null) continue;
			double coins = value.total(Part.OTHER) + value.total(Part.SCRAP) + value.total(Part.FOSSIL);
			GameData.Gem gem = data.gem(entry.shaftType());
			if (gem != null && glossy != null) {
				coins += (entry.mining().blocks() + entry.mining().spread()) * FiestaScale.glossyPerBlock(gem, to, data) * glossy;
			}
			found.add(coins / (entry.durationMillis() / 60_000.0));
		}
		return found.isEmpty() ? null : new Tracked(Stats.median(found), found.size(), "shafts");
	}

	// avg loot of the corpse types that pass, pristine not in
	private static Tracked corpseLoot(List<JournalEntry> entries, Function<JournalEntry, ShaftValue> values, Predicate<String> type, int min) {
		double sum = 0;
		int count = 0;
		for (JournalEntry entry : entries) {
			if (entry.loots().stream().noneMatch(l -> type.test(l.type()))) continue;
			ShaftValue value = values.apply(entry);
			if (value == null) continue;
			for (ShaftValue.CorpseValue corpse : value.corpses()) {
				if (!type.test(corpse.type())) continue;
				sum += corpse.loot();
				count++;
			}
		}
		return count >= min ? new Tracked(sum / count, count, "corpses") : null;
	}

	static boolean isKeyCorpse(String type) {
		return CorpseType.KEY_LOOT.contains(CorpseType.of(type));
	}

	// "+1 bonus drop!" under a loot, any type
	private static Tracked extraRoll(List<JournalEntry> entries) {
		int loots = 0;
		int bonus = 0;
		for (JournalEntry entry : entries) {
			for (var loot : entry.loots()) {
				loots++;
				if (loot.bonusDrop()) bonus++;
			}
		}
		return loots >= MIN_LOOTS ? new Tracked((double) bonus / loots, loots, "corpses") : null;
	}

	// lost ticks = (ticks - R x boost ticks) / (R - 1), R = blocks/s boost vs not, from the 1st gem
	// block (research/11 s17). null = cant say. noisy lol: 0.7 to 6.0 over 10 shafts, median 1.8
	static Double lostTicks(MiningStats mining) {
		Integer boosted = mining.boostBlocks();
		Double ticks = mining.shownTicks();
		Double boostTicks = mining.boostTicks();
		if (boosted == null || boosted < MIN_BOOST_BLOCKS || ticks == null || boostTicks == null) return null;
		// pauses are no blocks, not slow ones
		long outside = mining.millis() - mining.boostMillis() - mining.pausedMillis();
		int normal = mining.blocks() - boosted;
		if (mining.boostMillis() <= 0 || outside <= 0 || normal <= 0) return null;
		double ratio = ((double) boosted / mining.boostMillis()) / ((double) normal / outside);
		// boost gave nothing
		if (ratio <= 1) return null;
		return Math.max(0, Math.min(MAX_LOST_TICKS, (ticks - ratio * boostTicks) / (ratio - 1)));
	}

	private record SpawnGem(String code, Tracked share) {
	}

	// gem most portals spawned on + how many. null = under MIN_SHAFTS w/ a gem
	// spawn block saved since 0.27.0-alpha.3, else the mining.jsonl rule below
	private static SpawnGem spawnGem(List<JournalEntry> entries, GameData data) {
		Map<String, int[]> byGem = new HashMap<>();
		int total = 0;
		for (JournalEntry entry : entries) {
			GameData.Gem gem = data.gemOfBlock(entry.spawnBlock());
			if (gem == null) continue;
			total++;
			int[] counts = byGem.computeIfAbsent(gem.code(), code -> new int[2]);
			counts[0]++;
			if (gem.code().equals(entry.shaftType())) counts[1]++;
		}
		if (total < MIN_SHAFTS) return null;
		String best = null;
		for (GameData.Gem gem : data.gems()) {
			int[] counts = byGem.get(gem.code());
			if (counts != null && (best == null || counts[0] > byGem.get(best)[0])) best = gem.code();
		}
		int[] counts = byGem.get(best);
		return new SpawnGem(best, new Tracked((double) counts[1] / counts[0], counts[0], "shafts"));
	}

	// most mined outside shafts, by fine. null = none
	static String spawnGem(List<MiningEntry> mining, TextPatterns patterns, GameData data) {
		Map<String, Double> fine = new HashMap<>();
		for (MiningEntry entry : mining) GemYield.fineByGem(entry.items(), patterns).forEach((gem, count) -> fine.merge(gem, count, Double::sum));
		return fine.entrySet().stream().max(Map.Entry.comparingByValue())
				.map(e -> data.gemByName(e.getKey())).map(GameData.Gem::code).orElse(null);
	}

	static boolean corpseShaft(JournalEntry entry) {
		String type = entry.shaftType();
		return type != null && !type.equals("FAIR") && !type.equals("LITT");
	}

	// what spawn() counts
	static int spawns(List<SessionSummary> sessions) {
		int count = 0;
		for (SessionSummary session : sessions) {
			for (JournalEntry entry : session.entries()) if (entry.searchMillis() != null) count++;
		}
		return count;
	}

	// vanguard always 1 corpse, littlefoot never extra (research/11 s10)
	private static void corpseRules(List<JournalEntry> entries, Map<NumberId, Tracked> tracked) {
		int shafts = 0;
		int extra = 0;
		int lapis = 0;
		int corpses = 0;
		for (JournalEntry entry : entries) {
			if (!corpseShaft(entry)) continue;
			shafts++;
			if (entry.extraCorpses() > 0) extra++;
			for (Map.Entry<String, CorpseCount> count : CorpseCounts.of(entry).entrySet()) {
				if (count.getKey().equals(CorpseType.VANGUARD.label())) continue;
				corpses += count.getValue().total();
				if (count.getKey().equals(CorpseType.LAPIS.label())) lapis += count.getValue().total();
			}
		}
		if (shafts >= MIN_SHAFTS) put(tracked, NumberId.EXTRA_CORPSE, new Tracked((double) extra / shafts, shafts, "shafts"));
		if (shafts >= MIN_SHAFTS && corpses > 0) put(tracked, NumberId.LAPIS_SHARE, new Tracked((double) lapis / corpses, corpses, "corpses"));
	}

	// measured since the newest patch (it changed strengths), rest from ur speed
	private static void ticks(List<JournalEntry> mined, long patchStart, Map<String, Double> typed, GameData data, Map<NumberId, Num> out) {
		Map<Integer, List<Double>> byStrength = new HashMap<>();
		List<Double> boost = new ArrayList<>();
		List<Double> speeds = new ArrayList<>();
		double factor = data.value("tick_factor");
		int strongest = data.strengths().getFirst();
		for (JournalEntry entry : mined) {
			if (entry.enteredAt() < patchStart) continue;
			GameData.Gem gem = data.gem(entry.shaftType());
			Double ticks = entry.mining().shownTicks();
			if (gem == null || ticks == null) continue;
			byStrength.computeIfAbsent(gem.strength(), s -> new ArrayList<>()).add(ticks);
			speeds.add(gem.strength() * factor / ticks);
			if (gem.strength() == strongest && entry.mining().boostTicks() != null) boost.add(entry.mining().boostTicks());
		}
		Map<Integer, Double> typedTicks = new HashMap<>();
		for (int strength : data.strengths()) {
			Double set = typed.get(YourNumbers.ticksOf(strength).key());
			if (set != null) {
				typedTicks.put(strength, set);
				speeds.add(strength * factor / set);
			}
		}
		double speed = speeds.isEmpty() ? data.start("speed") : Stats.median(speeds);
		for (int strength : data.strengths()) {
			NumberId id = YourNumbers.ticksOf(strength);
			List<Double> found = byStrength.get(strength);
			Tracked mine = found == null ? null : new Tracked(Stats.median(found), found.size(), "shafts");
			out.put(id, resolve(id, mine, typed, data.ticks(strength, speed), speeds.isEmpty() ? Source.START : Source.CALCULATED));
		}
		Tracked boostTicks = boost.isEmpty() ? null : new Tracked(Stats.median(boost), boost.size(), "shafts");
		out.put(NumberId.BOOST_TICKS, resolve(NumberId.BOOST_TICKS, boostTicks, typed, data.start(NumberId.BOOST_TICKS.key())));
	}

	// flawed share = base + per corpse x looted (frozen perfection, research/10 s3)
	private static void flawed(List<JournalEntry> mined, Map<String, Double> typed, GameData data, Map<NumberId, Num> out) {
		double perCorpse = data.value("per_corpse") / 100;
		Tracked base = null;
		if (!mined.isEmpty()) {
			List<Double> bases = mined.stream().map(e -> flawedShare(e.gems()) - perCorpse * CorpseCounts.looted(CorpseCounts.of(e))).toList();
			base = new Tracked(Stats.median(bases), mined.size(), "shafts");
		}
		out.put(NumberId.FLAWED, resolve(NumberId.FLAWED, base, typed, data.start(NumberId.FLAWED.key())));
	}

	private static double share(double part, double of) {
		return of <= 0 ? 0 : part / of;
	}

	private static double median(List<JournalEntry> entries, Function<JournalEntry, Double> value) {
		List<Double> found = new ArrayList<>();
		for (JournalEntry entry : entries) {
			Double v = value.apply(entry);
			if (v != null && Double.isFinite(v)) found.add(v);
		}
		return found.isEmpty() ? Double.NaN : Stats.median(found);
	}
}
