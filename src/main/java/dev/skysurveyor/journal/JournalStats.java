package dev.skysurveyor.journal;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToLongFunction;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.ItemCount;

public final class JournalStats {
	// display choice, not a game number
	public static final long MIN_RATE_MILLIS = 5 * 60_000L;

	// so 1 lucky spawn isnt a record. display choice, not a game number
	public static final int MIN_TIMED_FOR_RECORD = 3;

	// nicks pick (11-11), display choice, not a game number
	public static final int MIN_SHAFTS_FOR_RATE_RECORD = 5;

	private JournalStats() {
	}

	// null under MIN_RATE_MILLIS
	public static Double perHour(double amount, long millis) {
		return millis < MIN_RATE_MILLIS ? null : amount * 3_600_000.0 / millis;
	}

	public record CorpseCount(int looted, int total) {
	}

	// mining empty before 0.24.0-alpha.11, breaks before 0.24.0-alpha.18
	public record SessionSummary(long id, List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> breaks) {
		public SessionSummary(long id, List<JournalEntry> entries) {
			this(id, entries, List.of(), List.of());
		}

		public SessionSummary withBreaks(List<BreakEntry> breaks) {
			return new SessionSummary(id, entries, mining, breaks);
		}

		public long start() {
			return id;
		}

		// last activity, so mining after the last shaft counts
		public long end() {
			long end = Math.max(id, entries.getLast().endedAt());
			return mining.isEmpty() ? end : Math.max(end, mining.getLast().at());
		}

		// wall clock, breaks incl
		public long lengthMillis() {
			return end() - start();
		}

		public long breakMillis() {
			return breakMillis(end());
		}

		// ended by now
		public long breakMillis(long now) {
			return Breaks.counted(breaks, start(), Math.max(end(), now));
		}

		// every per hour rate divides by this
		public long rateMillis() {
			return rateMillis(end());
		}

		public long rateMillis(long now) {
			return Math.max(end(), now) - start() - breakMillis(now);
		}

		public int shafts() {
			return entries.size();
		}

		public int timedSpawns() {
			return (int) entries.stream().filter(e -> e.searchMillis() != null).count();
		}

		public Long averageSearchMillis() {
			return averageSearch(entries);
		}

		public Double shaftsPerHour() {
			return shaftsPerHour(end());
		}

		public Double shaftsPerHour(long now) {
			return perHour(shafts(), now);
		}

		public Double perHour(double amount, long now) {
			return JournalStats.perHour(amount, rateMillis(now));
		}

		public long powder() {
			return entries.stream().mapToLong(JournalStats::powder).sum();
		}

		public int looted(String type) {
			return entries.stream().mapToInt(e -> CorpseCounts.looted(e, type)).sum();
		}
	}

	// corpses = w/ loot seen, item corpses = ones that dropped it
	public record Drops(int corpses, Map<String, ItemDrop> items) {
	}

	public record ItemDrop(int corpses, long count) {
		public double chance(int of) {
			return corpses / (double) of;
		}
	}

	public record AllTime(
			int shafts,
			int sessions,
			long timeMillis,
			long powder,
			Map<String, Integer> looted,
			Long averageSearchMillis,
			List<SessionSummary> oldestFirst,
			Map<String, Map<String, Integer>> types,
			Map<String, Integer> corpsesSeen,
			Map<String, Integer> shaftsWithCorpse,
			Map<String, Drops> drops) {
		public int typedShafts() {
			return types.values().stream().flatMap(v -> v.values().stream()).mapToInt(Integer::intValue).sum();
		}
	}

	// never seen = every shaft
	public static Map<String, Long> drystreaks(List<JournalEntry> entries, List<String> types) {
		Map<String, Long> dry = new LinkedHashMap<>();
		long since = 0;
		for (int i = entries.size() - 1; i >= 0 && dry.size() < types.size(); i--) {
			String type = entries.get(i).shaftType();
			if (type != null && types.contains(type)) dry.putIfAbsent(type, since);
			since++;
		}
		for (String type : types) dry.putIfAbsent(type, (long) entries.size());
		return dry;
	}

	// finished that day
	public static long shaftsOn(List<JournalEntry> entries, LocalDate day, ZoneId zone) {
		long count = 0;
		for (int i = entries.size() - 1; i >= 0; i--) {
			JournalEntry entry = entries.get(i);
			LocalDate date = Instant.ofEpochMilli(entry.endedAt()).atZone(zone).toLocalDate();
			if (date.isBefore(day)) break;
			if (date.equals(day)) count++;
		}
		return count;
	}

	public static List<SessionSummary> sessions(List<JournalEntry> entries) {
		return sessions(entries, List.of(), List.of());
	}

	// no shaft = not shown, its mining + breaks neither
	public static List<SessionSummary> sessions(List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> breaks) {
		Map<Long, List<JournalEntry>> bySession = new LinkedHashMap<>();
		for (JournalEntry entry : entries) {
			bySession.computeIfAbsent(entry.sessionId(), id -> new ArrayList<>()).add(entry);
		}
		Map<Long, List<MiningEntry>> outside = bySession(mining, MiningEntry::sessionId, bySession.keySet());
		Map<Long, List<BreakEntry>> paused = bySession(breaks, BreakEntry::sessionId, bySession.keySet());
		List<SessionSummary> sessions = new ArrayList<>();
		bySession.forEach((id, list) -> sessions.add(new SessionSummary(id, List.copyOf(list), List.copyOf(outside.getOrDefault(id, List.of())),
				List.copyOf(paused.getOrDefault(id, List.of())))));
		sessions.sort(Comparator.comparingLong(SessionSummary::start).reversed());
		return sessions;
	}

	private static <T> Map<Long, List<T>> bySession(List<T> list, ToLongFunction<T> sessionId, Set<Long> keep) {
		Map<Long, List<T>> grouped = new HashMap<>();
		for (T item : list) {
			long id = sessionId.applyAsLong(item);
			if (keep.contains(id)) grouped.computeIfAbsent(id, k -> new ArrayList<>()).add(item);
		}
		return grouped;
	}

	// colds only from 0.22.0 on
	public record Campfires(int placed, List<Integer> colds) {
		public Double averageCold() {
			return colds.isEmpty() ? null : colds.stream().mapToInt(Integer::intValue).average().orElseThrow();
		}
	}

	public static Campfires campfires(List<JournalEntry> entries) {
		int placed = 0;
		List<Integer> colds = new ArrayList<>();
		for (JournalEntry entry : entries) {
			placed += entry.campfiresAt().size();
			if (entry.campfireColds() == null) continue;
			for (Integer cold : entry.campfireColds()) if (cold != null) colds.add(cold);
		}
		return new Campfires(placed, List.copyOf(colds));
	}

	public static long powder(JournalEntry entry) {
		return powder(entry.loots(), entry.exitPowder());
	}

	public static long powder(List<CorpseLoot> loots, long exitPowder) {
		return loots.stream().mapToLong(CorpseLoot::powder).sum() + exitPowder;
	}

	public static AllTime allTime(List<JournalEntry> entries) {
		return allTime(entries, List.of(), List.of());
	}

	// timeMillis w/o breaks, avg rates divide by it
	public static AllTime allTime(List<JournalEntry> entries, List<MiningEntry> mining, List<BreakEntry> breaks) {
		List<SessionSummary> sessions = sessions(entries, mining, breaks);
		List<SessionSummary> oldestFirst = new ArrayList<>(sessions);
		oldestFirst.sort(Comparator.comparingLong(SessionSummary::start));

		Map<String, Integer> looted = new LinkedHashMap<>();
		Map<String, Integer> seen = new LinkedHashMap<>();
		Map<String, Map<String, Integer>> types = new LinkedHashMap<>();
		Map<String, Integer> shaftsWithCorpse = new LinkedHashMap<>();
		Map<String, Integer> lootSeen = new LinkedHashMap<>();
		Map<String, Map<String, ItemDrop>> dropped = new LinkedHashMap<>();
		long powder = 0;
		for (JournalEntry entry : entries) {
			powder += powder(entry);
			CorpseCounts.of(entry).forEach((type, count) -> {
				seen.merge(type, count.total(), Integer::sum);
				if (count.looted() > 0) looted.merge(type, count.looted(), Integer::sum);
				shaftsWithCorpse.merge(type, 1, Integer::sum);
			});
			for (CorpseLoot loot : entry.loots()) {
				lootSeen.merge(loot.type(), 1, Integer::sum);
				Map<String, ItemDrop> items = dropped.computeIfAbsent(loot.type(), t -> new LinkedHashMap<>());
				Map<String, Long> once = new LinkedHashMap<>();
				for (ItemCount item : loot.items()) once.merge(item.item(), item.count(), Long::sum);
				once.forEach((item, count) -> items.merge(item, new ItemDrop(1, count),
						(had, one) -> new ItemDrop(had.corpses() + 1, had.count() + count)));
			}
			String type = entry.shaftType();
			String variant = entry.variant();
			if (type != null && variant != null) {
				types.computeIfAbsent(type, t -> new LinkedHashMap<>()).merge(variant, 1, Integer::sum);
			}
		}

		return new AllTime(entries.size(), sessions.size(),
				sessions.stream().mapToLong(SessionSummary::rateMillis).sum(), powder,
				orderedMap(looted), averageSearch(entries), List.copyOf(oldestFirst), byTotal(types), orderedMap(seen), orderedMap(shaftsWithCorpse),
				drops(lootSeen, dropped));
	}

	private static Map<String, Drops> drops(Map<String, Integer> lootSeen, Map<String, Map<String, ItemDrop>> dropped) {
		Map<String, Drops> drops = new LinkedHashMap<>();
		for (String type : ordered(lootSeen.keySet())) drops.put(type, new Drops(lootSeen.get(type), dropped.get(type)));
		return drops;
	}

	public static Long recentAverage(List<SessionSummary> newestFirst, int count) {
		return averageSearch(newestFirst.stream().limit(count).flatMap(s -> s.entries().stream()).toList());
	}

	public static Long averageSearch(List<JournalEntry> entries) {
		long total = 0;
		int timed = 0;
		for (JournalEntry entry : entries) {
			if (entry.searchMillis() == null) continue;
			total += entry.searchMillis();
			timed++;
		}
		return timed == 0 ? null : total / timed;
	}

	static List<String> ordered(Iterable<String> types) {
		List<String> known = new ArrayList<>();
		List<String> other = new ArrayList<>();
		for (String type : types) (CorpseType.ORDER.contains(type) ? known : other).add(type);
		known.sort(Comparator.comparingInt(CorpseType.ORDER::indexOf));
		other.sort(null);
		known.addAll(other);
		return known;
	}

	private static Map<String, Map<String, Integer>> byTotal(Map<String, Map<String, Integer>> types) {
		List<Map.Entry<String, Map<String, Integer>>> list = new ArrayList<>(types.entrySet());
		list.sort(Comparator.comparingInt((Map.Entry<String, Map<String, Integer>> e) ->
				e.getValue().values().stream().mapToInt(Integer::intValue).sum()).reversed());
		Map<String, Map<String, Integer>> sorted = new LinkedHashMap<>();
		for (Map.Entry<String, Map<String, Integer>> e : list) {
			Map<String, Integer> variants = new LinkedHashMap<>();
			e.getValue().keySet().stream().sorted(Comparator.comparingInt(v -> "12C".indexOf(v) < 0 ? 9 : "12C".indexOf(v)))
					.forEach(v -> variants.put(v, e.getValue().get(v)));
			sorted.put(e.getKey(), variants);
		}
		return sorted;
	}

	private static Map<String, Integer> orderedMap(Map<String, Integer> counts) {
		Map<String, Integer> ordered = new LinkedHashMap<>();
		for (String type : ordered(counts.keySet())) ordered.put(type, counts.get(type));
		return ordered;
	}
}
