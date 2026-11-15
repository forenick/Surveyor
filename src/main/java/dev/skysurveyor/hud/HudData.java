package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.hud.HudValue.Range;
import dev.skysurveyor.journal.BreakEntry;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.LootSummary;
import dev.skysurveyor.journal.MetalYield;
import dev.skysurveyor.journal.PersonalBests;
import dev.skysurveyor.money.Coins;
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
	private SessionTotals session = SessionTotals.NONE;

	public long now() {
		return now;
	}

	public boolean inShaft() {
		return inShaft;
	}

	// colour, then back to the value colour
	static String paint(Character color, String text) {
		return color == null ? text : PAINT + String.valueOf(color) + text + BACK;
	}

	public Object get(HudValue value, Range range) {
		return get(value, range, null);
	}

	public Object get(HudValue value, Range range, String target) {
		return values.get(key(value, range, target));
	}

	public Object getOrExample(HudValue value, Range range) {
		return getOrExample(value, range, null);
	}

	public Object getOrExample(HudValue value, Range range, String target) {
		Key key = key(value, range, target);
		Object v = values.get(key);
		return v != null && !placeholders.contains(key) ? v : example(key);
	}

	public Double number(HudValue value, Range range, boolean example) {
		Key key = key(value, range, null);
		HudData from = example && (values.get(key) == null || placeholders.contains(key)) ? EXAMPLE : this;
		return from.values.get(key) instanceof Number n ? n.doubleValue() : null;
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
			Map<String, Long> looted) {
		public static final Totals NONE = new Totals(0, 0, 0, 0);

		public Totals(long shafts, long powder, double fine, double metal) {
			this(shafts, powder, fine, 0, 0, metal, Map.of());
		}

		public Totals(long shafts, long powder, double fine, long spawnMillis, long timedSpawns, double metal) {
			this(shafts, powder, fine, spawnMillis, timedSpawns, metal, Map.of());
		}

		public Totals plus(Totals other) {
			Map<String, Long> sum = new HashMap<>(looted);
			other.looted.forEach((type, count) -> sum.merge(type, count, Long::sum));
			return new Totals(shafts + other.shafts, powder + other.powder, fine + other.fine,
					spawnMillis + other.spawnMillis, timedSpawns + other.timedSpawns, metal + other.metal, Map.copyOf(sum));
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
			Map<String, Long> looted = new HashMap<>();
			for (JournalEntry entry : entries) {
				addLooted(looted, entry.corpses());
				powder += JournalStats.powder(entry);
				fine += fine(entry.loots(), patterns);
				if (entry.metal() != null) metal += entry.metal().in(MetalYield.RAW);
				if (entry.searchMillis() != null) {
					spawn += entry.searchMillis();
					timed++;
				}
			}
			return new Totals(entries.size(), powder, fine, spawn, timed, metal, Map.copyOf(looted));
		}

		public static Totals of(Shaft shaft, TextPatterns patterns) {
			MetalYield metal = MetalYield.of(shaft);
			Map<String, Long> looted = new HashMap<>();
			addLooted(looted, shaft.corpses());
			return new Totals(1, JournalStats.powder(shaft.loots(), shaft.exitPowder()), fine(shaft.loots(), patterns), 0, 0,
					metal == null ? 0 : metal.in(MetalYield.RAW), Map.copyOf(looted));
		}

		private static void addLooted(Map<String, Long> looted, List<Corpse> corpses) {
			for (Corpse corpse : corpses) {
				if (corpse.looted()) looted.merge(corpse.type(), 1L, Long::sum);
			}
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

	public static HudData live(SessionTotals session, Totals allTime, Totals thisShaft, Long spawnTimer, Shaft shaft,
			long now, ShaftNames names) {
		return live(session, allTime, thisShaft, spawnTimer, shaft, now, names, MetalYield.ENCHANTED);
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
		// so the editor shows all examples, not mixed w/ zeros
		if (session.summary() == null) data.placeholders(Range.SESSION);
		data.put(HudValue.SESSION_SHAFTS, Range.ALL_TIME, allTime.shafts());
		data.put(HudValue.SESSION_POWDER, Range.ALL_TIME, allTime.powder());
		data.put(HudValue.SESSION_CORPSE_FINE, Range.ALL_TIME, allTime.fine());
		data.put(HudValue.MINED_METAL, Range.ALL_TIME, allTime.metal() / perTier);
		if (allTime.shafts() == 0) data.placeholders(Range.ALL_TIME);
		if (thisShaft != null) {
			data.put(HudValue.SESSION_POWDER, Range.SHAFT, thisShaft.powder());
			data.put(HudValue.SESSION_CORPSE_FINE, Range.SHAFT, thisShaft.fine());
			data.put(HudValue.MINED_METAL, Range.SHAFT, thisShaft.metal() / perTier);
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
		}
		data.put(HudValue.SPAWN_TIMER, spawnTimer);
		data.inShaft = shaft != null;
		if (shaft != null) {
			if (shaft.id() != null) {
				String type = shaft.id().type();
				data.put(HudValue.SHAFT_TYPE, paint(names.hudColor(type), names.name(type)));
			}
			data.put(HudValue.SHAFT_TIME, Math.max(0, now - shaft.enteredAt()));
			data.put(HudValue.SHAFT_CORPSES, corpses(shaft.corpses()));
			if (shaft.cold() != null) data.cold(shaft.cold());
			data.put(HudValue.SHAFT_CAMPFIRES, (long) shaft.campfiresPlaced().size());
		}
		return data;
	}

	// net coins, null = no prices yet. shaft = live or the last one. session + allTime = shafts only
	// outside = mined outside shafts, sessionStart = live session, null = none
	// breakMillis = live sessions break time not counted per hour, up to now
	public record Money(Double shaft, Double session, Double allTime, long allTimeShafts, Double outsideSession,
			Double outsideAllTime, Long sessionStart, long breakMillis) {
		public Money(Double shaft, Double session, Double allTime, long allTimeShafts) {
			this(shaft, session, allTime, allTimeShafts, null, null, null);
		}

		public Money(Double shaft, Double session, Double allTime, long allTimeShafts, Double outsideSession,
				Double outsideAllTime, Long sessionStart) {
			this(shaft, session, allTime, allTimeShafts, outsideSession, outsideAllTime, sessionStart, 0);
		}
	}

	public HudData money(Money money) {
		Double sessionCoins = MoneyStats.plus(money.session(), money.outsideSession());
		put(HudValue.COINS, Range.SHAFT, money.shaft());
		real(HudValue.COINS, Range.SESSION, sessionCoins);
		real(HudValue.COINS, Range.ALL_TIME, MoneyStats.plus(money.allTime(), money.outsideAllTime()));
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
		if (type != null) put(HudValue.SHAFT_TYPE, paint(names.hudColor(type), names.name(type)));
		put(HudValue.SHAFT_TIME, last.durationMillis());
		put(HudValue.SHAFT_CORPSES, corpses(last.corpses()));
		if (last.coldAtExit() != null) cold(last.coldAtExit());
		put(HudValue.SHAFT_CAMPFIRES, (long) last.campfiresAt().size());
		return put(HudValue.AVERAGE_SPAWN, Range.SHAFT, last.searchMillis());
	}

	// dry = shaft type tag -> shafts since. pbs = PersonalBests.key -> shown text. null = not known
	public record Extra(Map<String, Long> dry, Long sessionMillis, Long sinceShaft, Long shaftsToday, Map<String, String> pbs) {
		public Extra(Map<String, Long> dry, Long sessionMillis, Long sinceShaft, Long shaftsToday) {
			this(dry, sessionMillis, sinceShaft, shaftsToday, Map.of());
		}
	}

	public HudData extra(Extra extra) {
		extra.pbs().forEach((key, text) -> put(HudValue.PERSONAL_BEST, null, key, text));
		extra.dry().forEach((target, count) -> put(HudValue.DRYSTREAK, null, target, count));
		put(HudValue.SESSION_TIME, extra.sessionMillis());
		put(HudValue.SINCE_LAST_SHAFT, extra.sinceShaft());
		return put(HudValue.SHAFTS_TODAY, extra.shaftsToday());
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

	// null until the tab list shows them
	static String corpses(List<Corpse> corpses) {
		if (corpses.isEmpty()) return null;
		long looted = corpses.stream().filter(Corpse::looted).count();
		Set<String> types = new LinkedHashSet<>();
		for (String type : CorpseType.ORDER) {
			if (corpses.stream().anyMatch(c -> c.type().equals(type))) types.add(type);
		}
		corpses.forEach(c -> types.add(c.type()));
		List<String> painted = new ArrayList<>();
		for (String type : types) painted.add(paint(CorpseType.of(type) == null ? null : CorpseType.color(type).charAt(1), type));
		return looted + "/" + corpses.size() + " " + String.join(", ", painted);
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
				.put(HudValue.SPAWN_TIMER, 102_000L)
				.put(HudValue.DRYSTREAK, 12L)
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
				.put(HudValue.SHAFT_TYPE, paint('a', "Jade"))
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
				.put(HudValue.FPS, 144L)
				.put(HudValue.PING, 42L)
				.put(HudValue.TPS, 19.9);
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
