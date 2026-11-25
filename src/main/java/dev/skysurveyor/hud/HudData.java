package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ItemNames;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TabListParser;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.effects.Consumable;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.CorpseCounts;
import dev.skysurveyor.journal.FossilYield;
import dev.skysurveyor.journal.GemYield;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.CorpseCount;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LootSummary;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.money.DropList;
import dev.skysurveyor.money.MoneyStats;
import dev.skysurveyor.util.Format;

public final class HudData {
	// mockup numbers, not game data
	public static final HudData EXAMPLE = example();

	private record Key(HudValue value, Range range, String target) {
	}

	private final Map<Key, Object> values = new HashMap<>();
	// real but empty (zeros before the first shaft), editor shows examples
	private final Set<Key> placeholders = new HashSet<>();
	// private use chars, HudBoxText swaps them for § codes w/ the right styles
	static final char PAINT = '\uE003';
	static final char BACK = '\uE002';

	private long now;
	private boolean inShaft;
	// the shafts (or last ones) colour code, null = none
	private Character shaftColor;
	private SessionTotals session = SessionTotals.NONE;
	// a values max, f.e. the pity widgets 2nd number. values w/o a range
	private final Map<HudValue, Long> maxes = new EnumMap<>(HudValue.class);
	// oldest 1st, empty = none
	private List<Spawn> recentSpawns = List.of();
	private List<Double> efficiencyTrack = List.of();
	private Double usualEfficiency;
	// drops line per period, missing = not worked out (no line wants it)
	private final Map<Range, DropList> drops = new EnumMap<>(Range.class);
	// a line asked for a pb, LiveHud only works them out then. client thread
	private boolean pbRead;

	public long now() {
		return now;
	}

	public boolean inShaft() {
		return inShaft;
	}

	public Character shaftColor() {
		return shaftColor;
	}

	private HudData shaftType(ShaftNames names, String type) {
		shaftColor = names.hudColor(type);
		return put(HudValue.SHAFT_TYPE, paint(shaftColor, names.name(type)));
	}

	// colour, then back to the value colour
	static String paint(Character color, String text) {
		return color == null ? text : PAINT + String.valueOf(color) + text + BACK;
	}

	public Object get(HudValue value, Range range) {
		return get(value, range, null);
	}

	public Object get(HudValue value, Range range, String target) {
		read(value);
		return values.get(key(value, range, target));
	}

	public Object getOrExample(HudValue value, Range range) {
		return getOrExample(value, range, null);
	}

	public Object getOrExample(HudValue value, Range range, String target) {
		read(value);
		Key key = key(value, range, target);
		Object v = values.get(key);
		return v != null && !placeholders.contains(key) ? v : example(key);
	}

	public Double number(HudValue value, Range range, boolean example) {
		read(value);
		Key key = key(value, range, null);
		HudData from = example && (values.get(key) == null || placeholders.contains(key)) ? EXAMPLE : this;
		return from.values.get(key) instanceof Number n ? n.doubleValue() : null;
	}

	private void read(HudValue value) {
		if (value == HudValue.PERSONAL_BEST) pbRead = true;
	}

	public boolean pbRead() {
		return pbRead;
	}

	// other targets show the default ones example, pbs differ too much
	private static Object example(Key key) {
		Object v = EXAMPLE.values.get(key);
		if (v != null || key.target() == null || key.value() == HudValue.PERSONAL_BEST) return v;
		return EXAMPLE.values.get(key(key.value(), key.range(), null));
	}

	// hand edited files can ask for ranges a value doesnt have
	private static Key key(HudValue value, Range range, String target) {
		return new Key(value, value.hasRange(range) ? range : value.defaultRange(), value.targetOrDefault(target));
	}

	private HudData put(HudValue value, Object v) {
		return put(value, value.defaultRange(), v);
	}

	private HudData put(HudValue value, Range range, Object v) {
		return put(value, range, null, v);
	}

	private HudData put(HudValue value, Range range, String target, Object v) {
		if (v != null) values.put(key(value, range, target), v);
		return this;
	}

	// spawnMillis over timedSpawns shafts, for the avg. metal = shafts own metal in raw. looted = corpse type -> count
	public record Totals(long shafts, long powder, double fine, long spawnMillis, long timedSpawns, double metal,
			Map<String, Long> looted, long essence) {
		public static final Totals NONE = new Totals(0, 0, 0, 0, 0, 0, Map.of(), 0);

		public Totals plus(Totals other) {
			Map<String, Long> sum = new HashMap<>(looted);
			other.looted.forEach((type, count) -> sum.merge(type, count, Long::sum));
			return new Totals(shafts + other.shafts, powder + other.powder, fine + other.fine,
					spawnMillis + other.spawnMillis, timedSpawns + other.timedSpawns, metal + other.metal, Map.copyOf(sum),
					essence + other.essence);
		}

		public long looted(String type) {
			return looted.getOrDefault(type, 0L);
		}

		public Long averageSpawn() {
			return timedSpawns == 0 ? null : spawnMillis / timedSpawns;
		}

		public static Totals of(List<JournalEntry> entries, TextPatterns patterns) {
			long powder = 0;
			double fine = 0;
			long spawn = 0;
			long timed = 0;
			double metal = 0;
			long essence = 0;
			Map<String, Long> looted = new HashMap<>();
			for (JournalEntry entry : entries) {
				essence += entry.fossilEssence();
				addLooted(looted, CorpseCounts.of(entry));
				powder += JournalStats.powder(entry);
				fine += fine(entry.loots(), patterns);
				if (entry.metal() != null) metal += entry.metal().in(MetalYield.RAW);
				if (entry.searchMillis() != null) {
					spawn += entry.searchMillis();
					timed++;
				}
			}
			return new Totals(entries.size(), powder, fine, spawn, timed, metal, Map.copyOf(looted), essence);
		}

		public static Totals of(Shaft shaft, TextPatterns patterns) {
			MetalYield metal = MetalYield.of(shaft);
			FossilYield fossil = FossilYield.of(shaft);
			Map<String, Long> looted = new HashMap<>();
			addLooted(looted, CorpseCounts.of(shaft));
			return new Totals(1, JournalStats.powder(shaft.loots(), shaft.exitPowder()), fine(shaft.loots(), patterns), 0, 0,
					metal == null ? 0 : metal.in(MetalYield.RAW), Map.copyOf(looted),
					fossil == null ? 0 : fossil.essence());
		}

		private static void addLooted(Map<String, Long> looted, Map<String, CorpseCount> counts) {
			counts.forEach((type, count) -> {
				if (count.looted() > 0) looted.merge(type, (long) count.looted(), Long::sum);
			});
		}

		private static double fine(List<CorpseLoot> loots, TextPatterns patterns) {
			LootSummary loot = LootSummary.of(loots, patterns);
			return loot.gems() == null ? 0 : loot.gems().fine();
		}
	}

	public record SessionTotals(Totals totals, SessionSummary summary) {
		public static final SessionTotals NONE = new SessionTotals(Totals.NONE, null);

		public long shafts() {
			return totals.shafts();
		}

		public Long averageSpawn() {
			return summary == null ? null : summary.averageSearchMillis();
		}

		// live breaks change every refresh, totals dont
		public SessionTotals withBreaks(List<BreakEntry> breaks) {
			return summary == null ? this : new SessionTotals(totals, summary.withBreaks(breaks));
		}

		public static SessionTotals of(List<JournalEntry> entries, TextPatterns patterns) {
			if (entries.isEmpty()) return NONE;
			return new SessionTotals(Totals.of(entries, patterns), new SessionSummary(entries.getFirst().sessionId(), entries));
		}
	}

	// now = same clock as the shafts times. metalTier = MetalYield tier
	public static HudData live(SessionTotals session, Totals allTime, Totals thisShaft, Long spawnTimer, Shaft shaft,
			long now, ShaftNames names, int metalTier) {
		double perTier = MetalYield.rawPer(metalTier);
		HudData data = new HudData();
		data.now = now;
		data.session = session;
		Totals totals = session.totals();
		data.put(HudValue.SESSION_SHAFTS, totals.shafts());
		data.put(HudValue.SESSION_POWDER, totals.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, totals.fine());
		data.put(HudValue.MINED_METAL, totals.metal() / perTier);
		data.put(HudValue.FOSSIL_ESSENCE, totals.essence());
		// so the editor shows all examples, not mixed w/ zeros
		if (session.summary() == null) data.placeholders(Range.SESSION);
		data.put(HudValue.SESSION_SHAFTS, Range.ALL_TIME, allTime.shafts());
		data.put(HudValue.SESSION_POWDER, Range.ALL_TIME, allTime.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, Range.ALL_TIME, allTime.fine());
		data.put(HudValue.MINED_METAL, Range.ALL_TIME, allTime.metal() / perTier);
		data.put(HudValue.FOSSIL_ESSENCE, Range.ALL_TIME, allTime.essence());
		if (allTime.shafts() == 0) data.placeholders(Range.ALL_TIME);
		if (thisShaft != null) {
			data.put(HudValue.SESSION_POWDER, Range.SHAFT, thisShaft.powder());
			data.put(HudValue.SESSION_CORPSE_FINE, Range.SHAFT, thisShaft.fine());
			data.put(HudValue.MINED_METAL, Range.SHAFT, thisShaft.metal() / perTier);
			data.put(HudValue.FOSSIL_ESSENCE, Range.SHAFT, thisShaft.essence());
		}
		for (String type : CorpseType.ORDER) {
			String target = HudValue.targetTag(type);
			data.put(HudValue.CORPSES_LOOTED, Range.SESSION, target, totals.looted(type));
			data.put(HudValue.CORPSES_LOOTED, Range.ALL_TIME, target, allTime.looted(type));
			if (thisShaft != null) data.put(HudValue.CORPSES_LOOTED, Range.SHAFT, target, thisShaft.looted(type));
			if (session.summary() != null) {
				Double rate = session.summary().perHour(totals.looted(type), now);
				if (rate != null) data.put(HudValue.CORPSES_LOOTED, Range.PER_HOUR, target, Math.round(rate));
			}
		}
		data.put(HudValue.AVERAGE_SPAWN, session.averageSpawn());
		data.put(HudValue.AVERAGE_SPAWN, Range.ALL_TIME, allTime.averageSpawn());
		// spawn timer stops at the shafts own spawn while inside
		if (shaft != null) data.put(HudValue.AVERAGE_SPAWN, Range.SHAFT, spawnTimer);
		if (session.summary() != null) {
			SessionSummary summary = session.summary();
			data.put(HudValue.SHAFTS_PER_HOUR, summary.shaftsPerHour(now));
			Double powderPerHour = summary.perHour(totals.powder(), now);
			if (powderPerHour != null) data.put(HudValue.SESSION_POWDER, Range.PER_HOUR, Math.round(powderPerHour));
			data.put(HudValue.SESSION_CORPSE_FINE, Range.PER_HOUR, summary.perHour(totals.fine(), now));
			data.put(HudValue.MINED_METAL, Range.PER_HOUR, summary.perHour(totals.metal() / perTier, now));
			Double essencePerHour = summary.perHour(totals.essence(), now);
			if (essencePerHour != null) data.put(HudValue.FOSSIL_ESSENCE, Range.PER_HOUR, Math.round(essencePerHour));
		}
		data.put(HudValue.SPAWN_TIMER, spawnTimer);
		data.inShaft = shaft != null;
		if (shaft != null) {
			if (shaft.id() != null) {
				data.shaftType(names, shaft.id().type());
			}
			data.put(HudValue.SHAFT_TIME, Math.max(0, now - shaft.enteredAt()));
			data.put(HudValue.SHAFT_CORPSES, corpses(CorpseCounts.of(shaft)));
			if (shaft.cold() != null) data.cold(shaft.cold());
			data.put(HudValue.SHAFT_CAMPFIRES, (long) shaft.campfiresPlaced().size());
		}
		return data;
	}

	// net coins, null = no prices yet. shaft = live or the last one. session + allTime = shafts only
	// outside = mined outside shafts, sessionStart = live session, null = none
	// breakMillis = live sessions break time not counted per hour, up to now
	// inside = the shaft ur in, not saved yet. counts in session + all-time coins, not per shaft
	public record Money(Double shaft, Double session, Double allTime, long allTimeShafts, Double outsideSession,
			Double outsideAllTime, Long sessionStart, long breakMillis, Double inside) {
	}

	public HudData money(Money money) {
		Double sessionCoins = MoneyStats.plus(MoneyStats.plus(money.session(), money.outsideSession()), money.inside());
		Double allTimeCoins = MoneyStats.plus(MoneyStats.plus(money.allTime(), money.outsideAllTime()), money.inside());
		put(HudValue.COINS, Range.SHAFT, money.shaft());
		real(HudValue.COINS, Range.SESSION, sessionCoins);
		real(HudValue.COINS, Range.ALL_TIME, allTimeCoins);
		real(HudValue.OUTSIDE_COINS, Range.SESSION, money.outsideSession());
		real(HudValue.OUTSIDE_COINS, Range.ALL_TIME, money.outsideAllTime());
		real(HudValue.COINS, Range.PER_HOUR, perHour(sessionCoins, money));
		real(HudValue.OUTSIDE_COINS, Range.PER_HOUR, perHour(money.outsideSession(), money));
		if (money.session() != null && session.shafts() > 0) {
			put(HudValue.COINS_PER_SHAFT, Range.SESSION, money.session() / session.shafts());
		}
		if (money.allTime() != null && money.allTimeShafts() > 0) {
			put(HudValue.COINS_PER_SHAFT, Range.ALL_TIME, money.allTime() / money.allTimeShafts());
		}
		return this;
	}

	private Double perHour(Double amount, Money money) {
		if (amount == null) return null;
		if (session.summary() != null) return session.summary().perHour(amount, now);
		Long start = money.sessionStart();
		return start == null ? null : JournalStats.perHour(amount, now - start - money.breakMillis());
	}

	// a session w/o shafts can still have coins, not an editor example then
	private void real(HudValue value, Range range, Double v) {
		put(value, range, v);
		if (v != null) placeholders.remove(key(value, range, null));
	}

	// outside a shaft, the shaft values show the last one
	public HudData lastShaft(JournalEntry last, ShaftNames names) {
		if (inShaft || last == null) return this;
		String type = last.shaftType();
		if (type != null) shaftType(names, type);
		put(HudValue.SHAFT_TIME, last.durationMillis());
		put(HudValue.SHAFT_CORPSES, corpses(CorpseCounts.of(last)));
		if (last.coldAtExit() != null) cold(last.coldAtExit());
		put(HudValue.SHAFT_CAMPFIRES, (long) last.campfiresAt().size());
		return put(HudValue.AVERAGE_SPAWN, Range.SHAFT, last.searchMillis());
	}

	// dry = shaft type tag -> shafts since. pbs = PersonalBests.key -> shown text. null = not known
	public record Extra(Map<String, Long> dry, Long sessionMillis, Long sinceShaft, Long shaftsToday, Map<String, String> pbs) {
	}

	public HudData extra(Extra extra) {
		extra.pbs().forEach((key, text) -> put(HudValue.PERSONAL_BEST, null, key, text));
		extra.dry().forEach((target, count) -> put(HudValue.DRYSTREAK, null, target, count));
		put(HudValue.SESSION_TIME, extra.sessionMillis());
		put(HudValue.SINCE_LAST_SHAFT, extra.sinceShaft());
		return put(HudValue.SHAFTS_TODAY, extra.shaftsToday());
	}

	// whole percent, null = -
	public HudData efficiency(Long percent) {
		return put(HudValue.EFFICIENCY, percent);
	}

	// last minute every 10 s, oldest 1st. usual = median of this shaft type, null = none (tunnels, never mined)
	public HudData efficiencyTrack(List<Double> points, Double usual) {
		efficiencyTrack = List.copyOf(points);
		usualEfficiency = usual;
		return this;
	}

	public List<Double> efficiencyTrack(boolean example) {
		return efficiencyTrack.size() < 2 && example ? EXAMPLE.efficiencyTrack : efficiencyTrack;
	}

	public Double usualEfficiency(boolean example) {
		return efficiencyTrack.size() < 2 && example ? EXAMPLE.usualEfficiency : usualEfficiency;
	}

	// ms left per active one, missing = off
	public HudData consumables(Map<Consumable, Long> left) {
		for (HudValue value : HudValue.values()) {
			Long ms = value.consumable() == null ? null : left.get(value.consumable());
			if (ms != null) put(value, ms);
		}
		return this;
	}

	// null = no pity widget
	public HudData pity(TabListParser.Pity pity) {
		if (pity == null) return this;
		maxes.put(HudValue.PITY, pity.max());
		return put(HudValue.PITY, pity.now());
	}

	// the examples max when the number is the example
	public Long max(HudValue value, boolean example) {
		if (values.get(key(value, null, null)) != null) return maxes.get(value);
		return example ? EXAMPLE.maxes.get(value) : null;
	}

	// a timed shaft for the chart. type = shaft code, null = unknown
	public record Spawn(long millis, String type) {
	}

	// display choice
	public static final int RECENT_SHAFTS = 12;

	// last timed shafts, oldest 1st
	public static List<Spawn> lastSpawns(List<JournalEntry> entries) {
		List<Spawn> spawns = new ArrayList<>();
		for (int i = entries.size() - 1; i >= 0 && spawns.size() < RECENT_SHAFTS; i--) {
			JournalEntry entry = entries.get(i);
			if (entry.searchMillis() != null) spawns.add(new Spawn(entry.searchMillis(), entry.shaftType()));
		}
		return List.copyOf(spawns.reversed());
	}

	public HudData recentSpawns(List<Spawn> spawns) {
		recentSpawns = List.copyOf(spawns);
		return this;
	}

	// the examples when there are none yet
	public List<Spawn> recentSpawns(boolean example) {
		return recentSpawns.isEmpty() && example ? EXAMPLE.recentSpawns : recentSpawns;
	}

	public HudData drops(Range range, DropList list) {
		if (list != null) drops.put(range, list);
		return this;
	}

	// session example stands in for all-time
	private HudData drops(List<Range> ranges, DropList list) {
		for (Range range : ranges) drops(range, list);
		return this;
	}

	// editor shows the example while there are none. null = not worked out
	public DropList drops(Range range, boolean example) {
		DropList real = drops.get(range);
		if (!example || real != null && !real.isEmpty()) return real;
		return EXAMPLE.drops.get(range);
	}

	// null = not known right now
	public record Game(Integer fps, Long ping, Double tps, String clock, String modVersion, String minecraftVersion) {
	}

	public HudData game(Game game) {
		put(HudValue.FPS, game.fps() == null ? null : game.fps().longValue());
		put(HudValue.PING, game.ping());
		put(HudValue.TPS, game.tps());
		put(HudValue.CLOCK, game.clock());
		put(HudValue.MOD_VERSION, game.modVersion());
		return put(HudValue.MINECRAFT_VERSION, game.minecraftVersion());
	}

	private void placeholders(Range range) {
		for (HudValue value : HudValue.values()) {
			if (!value.hasRange(range)) continue;
			if (value.targets().isEmpty()) placeholders.add(key(value, range, null));
			for (String target : value.targets()) placeholders.add(key(value, range, target));
		}
	}

	// null until the tab list or a loot msg shows them
	static String corpses(Map<String, CorpseCount> counts) {
		if (counts.isEmpty()) return null;
		List<String> painted = new ArrayList<>();
		for (String type : counts.keySet()) painted.add(paint(CorpseType.of(type) == null ? null : CorpseType.color(type).charAt(1), type));
		return CorpseCounts.looted(counts) + "/" + CorpseCounts.total(counts) + " " + String.join(", ", painted);
	}

	private static HudData example() {
		HudData example = new HudData();
		example.inShaft = true;
		return example
				.put(HudValue.SESSION_SHAFTS, 6L)
				.put(HudValue.SESSION_SHAFTS, Range.ALL_TIME, 412L)
				.put(HudValue.SHAFTS_PER_HOUR, 19.4)
				.put(HudValue.AVERAGE_SPAWN, 85_000L)
				.put(HudValue.AVERAGE_SPAWN, Range.SHAFT, 102_000L)
				.put(HudValue.AVERAGE_SPAWN, Range.ALL_TIME, 91_000L)
				.put(HudValue.SESSION_POWDER, Range.SHAFT, 7_120L)
				.put(HudValue.SESSION_POWDER, 43_436L)
				.put(HudValue.SESSION_POWDER, Range.PER_HOUR, 137_166L)
				.put(HudValue.SESSION_POWDER, Range.ALL_TIME, 1_204_310L)
				.put(HudValue.SESSION_CORPSE_FINE, Range.SHAFT, 3.1)
				.put(HudValue.SESSION_CORPSE_FINE, 15.6)
				.put(HudValue.SESSION_CORPSE_FINE, Range.PER_HOUR, 49.3)
				.put(HudValue.SESSION_CORPSE_FINE, Range.ALL_TIME, 412.8)
				.put(HudValue.MINED_METAL, Range.SHAFT, 41.2)
				.put(HudValue.MINED_METAL, 301.3)
				.put(HudValue.MINED_METAL, Range.PER_HOUR, 98.6)
				.put(HudValue.MINED_METAL, Range.ALL_TIME, 2_140.7)
				.put(HudValue.FOSSIL_ESSENCE, Range.SHAFT, 283L)
				.put(HudValue.FOSSIL_ESSENCE, 1_131L)
				.put(HudValue.FOSSIL_ESSENCE, Range.PER_HOUR, 566L)
				.put(HudValue.FOSSIL_ESSENCE, Range.ALL_TIME, 6_412L)
				.put(HudValue.SPAWN_TIMER, 102_000L)
				.put(HudValue.DRYSTREAK, 12L)
				.pity(new TabListParser.Pity(1_498, 2_000))
				.efficiency(69L)
				// recording 5, 2nd jasper shaft per minute from pristine msgs, own blocks (mockups/efficiency-v1.html)
				.efficiencyTrack(List.of(0.74, 0.81, 0.78, 0.58, 0.70, 0.68, 0.69, 0.52, 0.62, 0.78, 0.62), 0.68)
				// mockup hud-graphs-v1, real journal shafts
				.recentSpawns(List.of(new Spawn(16_000, "UMBE"), new Spawn(4_000, "TITA"), new Spawn(79_000, "FAIR"),
						new Spawn(81_000, "JASP"), new Spawn(41_000, "TUNG"), new Spawn(42_000, "CITR"), new Spawn(80_000, "ONYX"),
						new Spawn(93_000, "JADE"), new Spawn(81_000, "UMBE"), new Spawn(91_000, "TITA"), new Spawn(78_000, "SAPP"),
						new Spawn(98_000, "JASP")))
				.put(HudValue.SINCE_LAST_SHAFT, 48_000L)
				.put(HudValue.CORPSES_LOOTED, Range.SHAFT, 2L)
				.put(HudValue.CORPSES_LOOTED, 9L)
				.put(HudValue.CORPSES_LOOTED, Range.PER_HOUR, 28L)
				.put(HudValue.CORPSES_LOOTED, Range.ALL_TIME, 172L)
				.put(HudValue.SESSION_TIME, 1_142_000L)
				.put(HudValue.SHAFTS_TODAY, 11L)
				.pb("session_rate", "26.5")
				.pb("session_shafts", "22")
				.pb("shaft_powder", "27,429")
				.pb("shaft_gems", "391.0 Fine")
				.pb("shaft_metal", "303.3 Enchanted Tungsten")
				.pb("shaft_coins", "+10.0M")
				.pb("session_coin_rate", "+33.4M")
				.pb("drystreak_jasp", "9 (longest 18)")
				.shaftType(ShaftNames.defaults(), "JADE")
				.put(HudValue.SHAFT_TIME, 134_000L)
				.put(HudValue.SHAFT_CORPSES, "2/4 " + paint('9', "Lapis") + ", " + paint('6', "Umber"))
				.cold(-12)
				.put(HudValue.SHAFT_CAMPFIRES, 1L)
				.put(HudValue.COINS, Range.SHAFT, 2_431_000.0)
				.put(HudValue.COINS, 14_820_000.0)
				.put(HudValue.COINS, Range.PER_HOUR, 46_800_000.0)
				.put(HudValue.COINS, Range.ALL_TIME, 612_300_000.0)
				.put(HudValue.COINS_PER_SHAFT, 2_470_000.0)
				.put(HudValue.COINS_PER_SHAFT, Range.ALL_TIME, 1_486_000.0)
				.put(HudValue.OUTSIDE_COINS, 4_210_000.0)
				.put(HudValue.OUTSIDE_COINS, Range.PER_HOUR, 13_300_000.0)
				.put(HudValue.OUTSIDE_COINS, Range.ALL_TIME, 96_400_000.0)
				.drops(List.of(Range.SESSION, Range.ALL_TIME), exampleDrops(List.of(gem("Jasper", 931, 42.7), item("Glossy Gemstone", 13, 18.8), gem("Jade", 378, 11.1),
						item("Refined Mineral", 35, 3.4), gem("Amber", 83, 2.6), item("Suspicious Scrap", 18, 2.5), gem("Aquamarine", 57, 1.9),
						gem("Citrine", 52, 1.7), gem("Sapphire", 47, 1.7), gem("Peridot", 26, 0.9), item("Enchanted Umber", 18, 0.6),
						item("Enchanted Glacite", 176, 0.5)), List.of(new DropList.Row(DropList.Kind.SPENT, "Tungsten Key", 3, -10.4e6, ItemNames.current().color("Tungsten Key"))),
						List.of(source("Mined gems", 42.7, gem("Jasper", 931, 42.7)),
								source(DropList.OUTSIDE, 25.8, gem("Jade", 364, 10.4), item("Glossy Gemstone", 5, 6.9), item("Refined Mineral", 35, 3.4)),
								source("Glossy Gemstones", 11.9, item("Glossy Gemstone", 8, 11.9)),
								source("Corpse loot", 9.2, gem("Aquamarine", 57, 1.9), gem("Citrine", 52, 1.7), item("Suspicious Scrap", 11, 1.5)),
								source("Suspicious Scrap", 1.0, item("Suspicious Scrap", 7, 1.0)), source("Other drops", 0.8))))
				.drops(Range.SHAFT, exampleDrops(List.of(gem("Jasper", 61, 2.8), item("Glossy Gemstone", 1, 1.5), gem("Citrine", 6, 0.2),
						item("Suspicious Scrap", 1, 0.14)), List.of(),
						List.of(source("Mined gems", 2.8, gem("Jasper", 61, 2.8)), source("Glossy Gemstones", 1.5, item("Glossy Gemstone", 1, 1.5)),
								source("Corpse loot", 0.2, gem("Citrine", 6, 0.2)), source("Suspicious Scrap", 0.14, item("Suspicious Scrap", 1, 0.14)))))
				.consumables(Map.of(Consumable.PRISTINE_POTATO, 2_532_000L, Consumable.FILET_O_FORTUNE, 2_528_000L,
						Consumable.COLD_RESISTANCE, 1_814_000L, Consumable.FIESTA_FLASK, 712_000L))
				.put(HudValue.FPS, 144L)
				.put(HudValue.PING, 42L)
				.put(HudValue.TPS, 19.9);
	}

	// mockup hud-drops-v1, coins in M. other drops w/o items, the mockup names none
	private static DropList exampleDrops(List<DropList.Row> items, List<DropList.Row> spent, List<DropList.Source> sources) {
		return new DropList(items, spent, 0, false, false, sources);
	}

	private static DropList.Source source(String name, double millions, DropList.Row... items) {
		return new DropList.Source(new DropList.Row(DropList.Kind.SOURCE, name, 0, millions * 1e6, 'f'), List.of(items));
	}

	private static DropList.Row gem(String gem, double fine, double millions) {
		String name = GemYield.fineName(gem);
		return new DropList.Row(DropList.Kind.GEM, name, fine, millions * 1e6, ItemNames.current().color(name));
	}

	private static DropList.Row item(String name, double count, double millions) {
		return new DropList.Row(DropList.Kind.ITEM, name, count, millions * 1e6, ItemNames.current().color(name));
	}

	private HudData pb(String key, String text) {
		return put(HudValue.PERSONAL_BEST, null, key, text);
	}

	// plain, the lines value colour paints it
	public static String pbText(PersonalBests.Def def, PersonalBests.Result result, int metalTier) {
		PersonalBests.Stat stat = def.type();
		Double v = result.value();
		if (stat == null || v == null) return null;
		long n = Math.round(v);
		return switch (stat) {
			case SHAFT_COINS, SESSION_COINS, SESSION_COIN_RATE -> Coins.signed(v);
			case SHAFT_POWDER, SESSION_POWDER -> Format.number(n);
			case SHAFT_GEMS -> Format.decimal(v) + " Fine";
			case SHAFT_METAL -> result.entry().metal().shortText(metalTier);
			case SHAFT_SPAWN, SHAFT_INSIDE -> Format.duration(n);
			case SESSION_SPAWN -> Format.duration(n) + " avg";
			case SHAFT_CORPSES, SHAFT_LAPIS, SESSION_SHAFTS -> Long.toString(n);
			case SHAFT_COLD -> n + "❄";
			case SHAFT_EFFICIENCY -> Format.percent(v);
			case SESSION_RATE -> Format.decimal(v);
			case DRYSTREAK -> result.current() + " (longest " + n + ")";
		};
	}

	// package private for tests
	HudData cold(int cold) {
		return put(HudValue.SHAFT_COLD, (long) cold);
	}

	// package private for tests
	HudData set(HudValue value, Range range, Object v) {
		return put(value, range, v);
	}
}
