package dev.skysurveyor.journal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.money.MoneyStats;

// the Stats tabs personal bests, built from a list the player can change
public final class PersonalBests {
	public enum Per {
		SHAFT("Per shaft"),
		SESSION("Per session"),
		STREAK("Streak");

		private final String displayName;

		Per(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	// ids saved in config.json, never rename
	public enum Stat {
		SHAFT_COINS("shaft_coins", Per.SHAFT, "Coins", false),
		SHAFT_POWDER("shaft_powder", Per.SHAFT, "Powder", false),
		SHAFT_GEMS("shaft_gems", Per.SHAFT, "Gems", false),
		SHAFT_METAL("shaft_metal", Per.SHAFT, "Metal", false),
		SHAFT_SPAWN("shaft_spawn", Per.SHAFT, "Spawn", true),
		SHAFT_INSIDE("shaft_inside", Per.SHAFT, "Time inside", false),
		SHAFT_CORPSES("shaft_corpses", Per.SHAFT, "Corpses", false),
		SHAFT_LAPIS("shaft_lapis", Per.SHAFT, "Lapis", false),
		// coldest = lowest, cold is negative
		SHAFT_COLD("shaft_cold", Per.SHAFT, "Cold", true),
		// own blocks against nonstop, shafts w/ 5 min of mining (mining/Efficiency.record)
		SHAFT_EFFICIENCY("shaft_efficiency", Per.SHAFT, "Efficiency", false),
		SESSION_SHAFTS("session_shafts", Per.SESSION, "Shafts", false),
		SESSION_RATE("session_rate", Per.SESSION, "Shafts / h", false),
		SESSION_COINS("session_coins", Per.SESSION, "Coins", false),
		SESSION_COIN_RATE("session_coin_rate", Per.SESSION, "Coins / h", false),
		SESSION_POWDER("session_powder", Per.SESSION, "Powder", false),
		SESSION_SPAWN("session_spawn", Per.SESSION, "Avg spawn", true),
		DRYSTREAK("drystreak", Per.STREAK, "Drystreak", false);

		private final String id;
		private final Per per;
		private final String displayName;
		private final boolean lowest;

		Stat(String id, Per per, String displayName, boolean lowest) {
			this.id = id;
			this.per = per;
			this.displayName = displayName;
			this.lowest = lowest;
		}

		public String id() {
			return id;
		}

		public Per per() {
			return per;
		}

		public String displayName() {
			return displayName;
		}

		public boolean lowestByDefault() {
			return lowest;
		}

		// already per hour, no "/ session"
		public boolean isRate() {
			return this == SESSION_RATE || this == SESSION_COIN_RATE;
		}

		public String label() {
			return switch (per) {
				case SHAFT -> displayName + " / shaft";
				case SESSION -> isRate() ? displayName : displayName + " / session";
				case STREAK -> displayName;
			};
		}

		// same name in the other per, f.e. coins / shaft <-> coins / session
		public Stat in(Per other) {
			for (Stat stat : values()) {
				if (stat.per == other && stat.displayName.equals(displayName)) return stat;
			}
			return null;
		}

		public static Stat byId(String id) {
			for (Stat stat : values()) {
				if (stat.id.equals(id)) return stat;
			}
			return null;
		}
	}

	// saved as is. shaft = type code f.e. JASP, any variant. best "lowest"/"highest", null = the stats own. name null = made up
	public record Def(String stat, String shaft, String best, String name) {
		public static final String LOWEST = "lowest";
		public static final String HIGHEST = "highest";

		public Stat type() {
			return Stat.byId(stat);
		}

		public boolean lowest() {
			Stat type = type();
			if (LOWEST.equals(best)) return true;
			if (HIGHEST.equals(best)) return false;
			return type != null && type.lowestByDefault();
		}

		// streaks always need a shaft type
		public String shaftOrDefault() {
			return shaft == null && type() == Stat.DRYSTREAK ? DEFAULT_DRY : shaft;
		}
	}

	// most wanted shaft, Docs/Text File.md
	public static final String DEFAULT_DRY = "JASP";

	// nicks pick (2026-11-14): fastest spawn, fastest session + lapis / shaft gone
	public static final List<Def> DEFAULTS = List.of(new Def("session_rate", null, null, null),
			new Def("session_shafts", null, null, null), new Def("shaft_powder", null, null, null),
			new Def("shaft_gems", null, null, null), new Def("shaft_efficiency", null, null, null), new Def("shaft_metal", null, null, null),
			new Def("shaft_coins", null, null, null), new Def("session_coin_rate", null, null, null),
			new Def("drystreak", DEFAULT_DRY, null, null));

	@FunctionalInterface
	public interface Coins {
		// net, null = no price yet
		Double shaft(JournalEntry entry);

		default Double session(List<JournalEntry> entries) {
			Double total = null;
			for (JournalEntry entry : entries) total = MoneyStats.plus(total, shaft(entry));
			return total;
		}

		// mining outside shafts, null = none or no price
		default Double outside(List<MiningEntry> mining) {
			return null;
		}

		static Coins of(Coins shaft, Function<MiningEntry, Double> outside) {
			return new Coins() {
				@Override
				public Double shaft(JournalEntry entry) {
					return shaft.shaft(entry);
				}

				@Override
				public Double outside(List<MiningEntry> mining) {
					return MoneyStats.outside(mining, outside);
				}
			};
		}
	}

	// value null = nothing counts yet. streaks: value = longest, entry = the shaft that ended it (null = still going)
	public record Result(Double value, JournalEntry entry, SessionSummary session, Long current) {
		static final Result NONE = new Result(null, null, null, null);
	}

	private PersonalBests() {
	}

	// stable id for HUD lines, from what it counts. survives renames + reordering
	public static String key(Def def) {
		String shaft = def.shaftOrDefault();
		return def.stat() + (shaft == null ? "" : "_" + shaft.toLowerCase(java.util.Locale.ROOT)) + (def.best() == null ? "" : "_" + def.best());
	}

	// the shaft ur in counts for a drystreak, its own type ends it
	public static long liveDry(long saved, String type, boolean inShaft, String inShaftType) {
		if (!inShaft) return saved;
		return type.equals(inShaftType) ? 0 : saved + 1;
	}

	public static Result live(Def def, Result saved, String inShaftType) {
		if (def.type() != Stat.DRYSTREAK || inShaftType == null || saved.current() == null) return saved;
		long current = liveDry(saved.current(), def.shaftOrDefault(), true, inShaftType);
		return current > saved.value() ? new Result((double) current, null, null, current) : new Result(saved.value(), saved.entry(), null, current);
	}

	public static String name(Def def, ShaftNames names) {
		if (def.name() != null && !def.name().isBlank()) return def.name();
		Stat stat = def.type();
		if (stat == null) return "?";
		String shaft = def.shaftOrDefault() == null ? null : names.name(def.shaftOrDefault());
		return switch (stat.per()) {
			case STREAK -> shaft + " dry";
			case SHAFT -> stat.displayName() + " / " + (shaft == null ? "" : shaft + " ") + "shaft";
			case SESSION -> {
				String base = stat.displayName();
				if (shaft != null) base = shaft + " " + Character.toLowerCase(base.charAt(0)) + base.substring(1);
				yield stat.isRate() ? base : base + " / session";
			}
		};
	}

	public static Result best(Def def, List<JournalEntry> entries, List<SessionSummary> sessions, Coins coins) {
		Stat stat = def.type();
		if (stat == null) return Result.NONE;
		String shaft = def.shaftOrDefault();
		boolean lowest = def.lowest();
		return switch (stat.per()) {
			case STREAK -> drystreak(entries, shaft);
			case SHAFT -> {
				Best<JournalEntry> best = best(entries, lowest, e -> matches(e, shaft) ? shaftValue(stat, e, coins) : null);
				yield best == null ? Result.NONE : new Result(best.value(), best.item(), null, null);
			}
			case SESSION -> {
				Best<SessionSummary> best = best(sessions, lowest, s -> sessionValue(stat, s, shaft, coins));
				yield best == null ? Result.NONE : new Result(best.value(), null, best.item(), null);
			}
		};
	}

	private record Best<T>(T item, double value) {
	}

	// first one wins a tie
	private static <T> Best<T> best(List<T> items, boolean lowest, Function<T, Double> valueOf) {
		Best<T> best = null;
		for (T item : items) {
			Double value = valueOf.apply(item);
			if (value == null || best != null && !(lowest ? value < best.value() : value > best.value())) continue;
			best = new Best<>(item, value);
		}
		return best;
	}

	private static boolean matches(JournalEntry entry, String shaft) {
		return shaft == null || shaft.equals(entry.shaftType());
	}

	// null = this shaft doesnt count, f.e. no gems in a metal shaft
	static Double shaftValue(Stat stat, JournalEntry entry, Coins coins) {
		return switch (stat) {
			case SHAFT_COINS -> coins.shaft(entry);
			case SHAFT_POWDER -> (double) JournalStats.powder(entry);
			case SHAFT_GEMS -> entry.gems() == null || entry.gems().fineEquivalent() <= 0 ? null : entry.gems().fineEquivalent();
			// raw count, tungsten and umber alike
			case SHAFT_METAL -> entry.metal() == null ? null : entry.metal().in(MetalYield.RAW);
			case SHAFT_SPAWN -> entry.searchMillis() == null ? null : (double) entry.searchMillis();
			case SHAFT_INSIDE -> (double) entry.durationMillis();
			case SHAFT_CORPSES -> (double) CorpseCounts.looted(CorpseCounts.of(entry));
			case SHAFT_LAPIS -> (double) CorpseCounts.looted(entry, CorpseType.LAPIS.label());
			case SHAFT_COLD -> entry.coldAtExit() == null ? null : (double) entry.coldAtExit();
			case SHAFT_EFFICIENCY -> Efficiency.record(entry.mining());
			default -> null;
		};
	}

	static Double sessionValue(Stat stat, SessionSummary session, String shaft, Coins coins) {
		List<JournalEntry> counted = shaft == null ? session.entries() : session.entries().stream().filter(e -> matches(e, shaft)).toList();
		if (counted.isEmpty()) return null;
		// rates need a few shafts in the session, so 1 lucky spawn isnt a record
		boolean rateOk = session.shafts() >= JournalStats.MIN_SHAFTS_FOR_RATE_RECORD;
		return switch (stat) {
			case SESSION_SHAFTS -> (double) counted.size();
			case SESSION_RATE -> rateOk ? session.perHour(counted.size(), session.end()) : null;
			case SESSION_COINS -> sessionCoins(session, counted, shaft, coins);
			case SESSION_COIN_RATE -> {
				Double total = sessionCoins(session, counted, shaft, coins);
				yield rateOk && total != null ? session.perHour(total, session.end()) : null;
			}
			case SESSION_POWDER -> (double) counted.stream().mapToLong(JournalStats::powder).sum();
			case SESSION_SPAWN -> {
				long timed = counted.stream().filter(e -> e.searchMillis() != null).count();
				Long average = JournalStats.averageSearch(counted);
				yield timed >= JournalStats.MIN_TIMED_FOR_RECORD && average != null ? (double) average : null;
			}
			default -> null;
		};
	}

	// all shafts = whole session w/ outside, one shaft type = only those shafts
	private static Double sessionCoins(SessionSummary session, List<JournalEntry> counted, String shaft, Coins coins) {
		Double shafts = coins.session(counted);
		return shaft == null ? MoneyStats.plus(shafts, coins.outside(session.mining())) : shafts;
	}

	// shafts before the first one count as a streak too
	static Result drystreak(List<JournalEntry> entries, String shaft) {
		long count = 0;
		long longest = 0;
		JournalEntry ended = null;
		for (JournalEntry entry : entries) {
			if (shaft.equals(entry.shaftType())) {
				if (count > longest) {
					longest = count;
					ended = entry;
				}
				count = 0;
			} else {
				count++;
			}
		}
		if (count > longest) {
			longest = count;
			ended = null;
		}
		return new Result((double) longest, ended, null, count);
	}

	// for tests + the builders list
	public static List<Stat> stats(Per per) {
		List<Stat> out = new ArrayList<>();
		for (Stat stat : Stat.values()) {
			if (stat.per() == per) out.add(stat);
		}
		return out;
	}
}
