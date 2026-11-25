package dev.skysurveyor.hud;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.effects.Consumable;
import dev.skysurveyor.journal.Breaks;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.journal.PersonalBests;

// ids saved in hud.json, never rename. tags use tag()
public enum HudValue {
	SESSION_SHAFTS("session_shafts", "Shafts", Group.SESSION, Kind.INTEGER, "Shafts: ", Range.SESSION, Range.ALL_TIME),
	SHAFTS_PER_HOUR("shafts_per_hour", "Shafts per hour", Group.SESSION, Kind.DECIMAL, "Shafts/h: "),
	AVERAGE_SPAWN("average_spawn", "Average spawn", Group.SPAWN, Kind.TIME, "Avg spawn: ", Range.SHAFT, Range.SESSION,
			Range.ALL_TIME),
	SESSION_POWDER("session_powder", "Shaft powder", Group.SESSION, Kind.INTEGER, "Shaft powder: ",
			Range.values()),
	SESSION_CORPSE_FINE("session_corpse_fine", "Corpse gems (Fine)", Group.SESSION, Kind.DECIMAL, "Corpse Fine: ", Range.values()),
	// in the metal tier setting
	MINED_METAL("mined_metal", "Mined metal", Group.SESSION, Kind.DECIMAL, "Mined metal: ", Range.values()),
	FOSSIL_ESSENCE("fossil_essence", "Fossil Essence", Group.SESSION, Kind.INTEGER, "Fossil Essence: ", Range.values()),
	SPAWN_TIMER("spawn_timer", "Spawn timer", Group.SPAWN, Kind.TIME, "Spawn: "),
	// shafts since the last one of the lines shaft type
	DRYSTREAK("drystreak", "Drystreak", Group.SPAWN, Kind.INTEGER, "Jasper drystreak: "),
	// tab list pity widget, shows now/max
	PITY("pity", "Shaft pity", Group.SPAWN, Kind.INTEGER, "Pity: "),
	SINCE_LAST_SHAFT("since_last_shaft", "Time since last shaft", Group.SPAWN, Kind.TIME, "Since last shaft: "),
	CORPSES_LOOTED("corpses_looted", "Corpses looted", Group.SESSION, Kind.INTEGER, "Lapis corpses: ", Range.values()),
	SESSION_TIME("session_time", "Session length", Group.OTHER, Kind.TIME, "Session: "),
	SHAFTS_TODAY("shafts_today", "Shafts today", Group.SESSION, Kind.INTEGER, "Shafts today: "),
	// this shaft from the 1st gem block, or this tunnel loop. whole percent (Docs/efficiency-design.md)
	EFFICIENCY("efficiency", "Efficiency", Group.OTHER, Kind.INTEGER, "Efficiency: ", "%"),
	// one of the Stats tabs personal bests, all-time
	PERSONAL_BEST("personal_best", "Personal best", Group.OTHER, Kind.TEXT, "PB: "),
	SHAFT_TYPE("shaft_type", "Shaft type", Group.SHAFT, Kind.TEXT, "Shaft: "),
	SHAFT_TIME("shaft_time", "Time inside", Group.SHAFT, Kind.TIME, "Time inside: "),
	SHAFT_CORPSES("shaft_corpses", "Corpses", Group.SHAFT, Kind.TEXT, "Corpses: "),
	SHAFT_COLD("shaft_cold", "Cold", Group.SHAFT, Kind.COLD, "Cold: "),
	SHAFT_CAMPFIRES("shaft_campfires", "Campfires", Group.SHAFT, Kind.INTEGER, "Campfires: "),
	// net, shaft = live while inside
	COINS("coins", "Coins", Group.MONEY, Kind.COINS, "Coins: ", Range.values()),
	COINS_PER_SHAFT("coins_per_shaft", "Coins per shaft", Group.MONEY, Kind.COINS, "Coins/shaft: ", Range.SESSION,
			Range.ALL_TIME),
	// sacks msgs while mining in the mines, not in a shaft. nicks name pick (2026-11-14)
	OUTSIDE_COINS("outside_coins", "Coins outside shafts", Group.MONEY, Kind.COINS, "Outside coins: ", Range.SESSION,
			Range.PER_HOUR, Range.ALL_TIME),
	// several rows, not a tag. label stays empty
	DROPS("drops", "Drops", Group.MONEY, Kind.TEXT, "", Range.SHAFT, Range.SESSION, Range.ALL_TIME),
	// time left, line hides while its effect is off (Docs/consumables-design.md)
	PRISTINE_POTATO("pristine_potato", "Pristine Potato", Group.CONSUMABLES, Kind.TIME, "Pristine Potato: "),
	FILET_O_FORTUNE("filet_o_fortune", "Filet O' Fortune", Group.CONSUMABLES, Kind.TIME, "Filet O' Fortune: "),
	COLD_RESISTANCE("cold_resistance", "Cold Resistance potion", Group.CONSUMABLES, Kind.TIME, "Cold Resistance: "),
	FIESTA_FLASK("fiesta_flask", "Fiesta Flask", Group.CONSUMABLES, Kind.TIME, "Fiesta Flask: "),
	FPS("fps", "FPS", Group.GAME, Kind.INTEGER, "FPS: "),
	PING("ping", "Ping", Group.GAME, Kind.INTEGER, "Ping: ", "ms"),
	TPS("tps", "TPS", Group.GAME, Kind.DECIMAL, "TPS: "),
	CLOCK("clock", "Clock", Group.GAME, Kind.TEXT, "Clock: "),
	MOD_VERSION("mod_version", "Surveyor version", Group.GAME, Kind.TEXT, "Surveyor "),
	MINECRAFT_VERSION("minecraft_version", "Minecraft version", Group.GAME, Kind.TEXT, "Minecraft ");

	// picker headings, display only. SHAFT = followsShaft, keep its 5
	public enum Group {
		SPAWN("Spawn"),
		SESSION("Shafts and loot"),
		SHAFT("Current shaft"),
		MONEY("Coins"),
		CONSUMABLES("Consumables"),
		OTHER("Other"),
		GAME("Game");

		private final String displayName;

		Group(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	// names saved in hud.json, never rename
	public enum Range {
		// or the last one after leaving
		SHAFT("This shaft", "The shaft you're in. Outside a shaft, the last one"),
		SESSION("Session", "This session. It ends after " + Journal.SESSION_GAP_MILLIS / 60_000
				+ " min without mining in the Dwarven Mines or shafting"),
		PER_HOUR("Per hour", "This session's amount per hour, breaks over " + Breaks.BREAK_MILLIS / 60_000 + " min left out"),
		ALL_TIME("All-time", "Everything in your journal");

		private final String displayName;
		private final String description;

		Range(String displayName, String description) {
			this.displayName = displayName;
			this.description = description;
		}

		public String displayName() {
			return displayName;
		}

		public String description() {
			return description;
		}

		public String tagName() {
			return name().toLowerCase(Locale.ROOT);
		}

		public static Range byTagName(String name) {
			for (Range range : values()) {
				if (range.tagName().equals(name)) return range;
			}
			return null;
		}
	}

	// range null = the value has none, f.e. shafts today
	public record Period(String name, String shortName, String description, HudValue value, Range range) {
		static Period of(HudValue value, Range range) {
			String shortName = switch (range) {
				case SHAFT -> "shaft";
				case PER_HOUR -> "/h";
				default -> range.displayName().toLowerCase(Locale.ROOT);
			};
			return new Period(range.displayName(), shortName, range.description(), value, range);
		}

		// any target
		public boolean shows(HudValue value, Range range) {
			return this.value == value && this.range == range;
		}
	}

	// what a line can draw under or instead of its number
	public enum Graph {
		NONE,
		BAR,
		// last shafts as columns
		CHART,
		// a value over the last minutes
		LINE
	}

	// what a line picks besides the range, f.e. {dry:jasper}
	public enum Target {
		NONE,
		SHAFT_TYPE,
		CORPSE_TYPE,
		// a PersonalBests.key, the list can change
		PERSONAL_BEST
	}

	// the players list + its keys, set by LiveHud. defaults till settings load
	private record PbList(List<PersonalBests.Def> list, List<String> keys) {
		static PbList of(List<PersonalBests.Def> list) {
			return new PbList(list, list.stream().map(PersonalBests::key).distinct().toList());
		}
	}

	private static volatile PbList personalBests = PbList.of(PersonalBests.DEFAULTS);

	// every refresh, keys only redone when the list changes
	public static void personalBests(List<PersonalBests.Def> list) {
		if (!list.equals(personalBests.list())) personalBests = PbList.of(list);
	}

	// built once, enum constants come before static fields
	private static final class Fixed {
		static final List<String> SHAFTS = shafts();
		static final List<String> CORPSES = CorpseType.ORDER.stream().map(HudValue::targetTag).toList();
		// Add line order, docs/hud-rethink.md
		static final List<HudValue> LISTED = List.of(SPAWN_TIMER, AVERAGE_SPAWN, SINCE_LAST_SHAFT, DRYSTREAK, PITY, SESSION_SHAFTS,
				SHAFTS_PER_HOUR, SHAFTS_TODAY, SESSION_POWDER, SESSION_CORPSE_FINE, MINED_METAL, FOSSIL_ESSENCE, CORPSES_LOOTED, SHAFT_TYPE,
				SHAFT_TIME, SHAFT_CORPSES, SHAFT_COLD, SHAFT_CAMPFIRES, COINS, COINS_PER_SHAFT, OUTSIDE_COINS, DROPS, PRISTINE_POTATO, FILET_O_FORTUNE,
				COLD_RESISTANCE, FIESTA_FLASK, EFFICIENCY, SESSION_TIME,
				PERSONAL_BEST, FPS, PING, TPS, CLOCK, MOD_VERSION, MINECRAFT_VERSION);

		private static List<String> shafts() {
			ShaftNames names = ShaftNames.defaults();
			return names.codes().stream().map(names::name).sorted().map(HudValue::targetTag).toList();
		}
	}

	private static PersonalBests.Def personalBest(String key) {
		for (PersonalBests.Def def : personalBests.list()) {
			if (PersonalBests.key(def).equals(key)) return def;
		}
		return null;
	}

	public enum Kind {
		// ms, Long
		TIME,
		INTEGER,
		DECIMAL,
		COLD,
		// Double, 1.2M
		COINS,
		TEXT
	}

	private final String id;
	private final String displayName;
	private final Group group;
	private final Kind kind;
	private final String defaultPrefix;
	private final List<Range> ranges;
	private final String unit;

	HudValue(String id, String displayName, Group group, Kind kind, String defaultPrefix, Range... ranges) {
		this(id, displayName, group, kind, defaultPrefix, "", ranges);
	}

	HudValue(String id, String displayName, Group group, Kind kind, String defaultPrefix, String unit, Range... ranges) {
		this.id = id;
		this.displayName = displayName;
		this.group = group;
		this.kind = kind;
		this.defaultPrefix = defaultPrefix;
		this.unit = unit;
		this.ranges = List.of(ranges);
	}

	public String id() {
		return id;
	}

	// short name in {tags}, nicks pick (2026-11-14). old ids still read
	public String tag() {
		return switch (this) {
			case SESSION_SHAFTS -> "shafts";
			case AVERAGE_SPAWN -> "avg_spawn";
			case SESSION_POWDER -> "shaft_powder";
			case SESSION_CORPSE_FINE -> "corpse_fine";
			case SPAWN_TIMER -> "spawn";
			case SHAFT_TIME -> "time_inside";
			case SHAFT_CORPSES -> "corpses";
			case SHAFT_COLD -> "cold";
			case SHAFT_CAMPFIRES -> "campfires";
			case MOD_VERSION -> "version";
			case MINECRAFT_VERSION -> "mc_version";
			case DRYSTREAK -> "dry";
			case SINCE_LAST_SHAFT -> "since_shaft";
			case CORPSES_LOOTED -> "looted";
			case PERSONAL_BEST -> "pb";
			default -> id;
		};
	}

	public String displayName() {
		return displayName;
	}

	public Group group() {
		return group;
	}

	public Kind kind() {
		return kind;
	}

	public String defaultPrefix() {
		return defaultPrefix;
	}

	// labels older versions gave new lines, still count as untyped
	private boolean oldDefaultPrefix(String prefix) {
		List<String> old = switch (this) {
			// 0.22 + 0.23 alpha.1-2
			case SESSION_POWDER -> List.of("Powder: ", "All-time powder: ", "All-time shaft powder: ");
			case SESSION_CORPSE_FINE -> List.of("Fine: ", "All-time corpse Fine: ");
			case SHAFT_TIME -> List.of("Inside: ");
			// 0.22, 0.23 alpha.1
			case COINS_PER_SHAFT -> List.of("Coins/shaft: ", "All-time avg/shaft: ", "Avg coins/shaft: ", "All-time avg coins/shaft: ");
			case CLOCK -> List.of("Time: ");
			// till 0.25 periods
			case SESSION_SHAFTS -> List.of("All-time shafts: ");
			case AVERAGE_SPAWN -> List.of("All-time avg spawn: ", "This shaft's spawn: ");
			case MINED_METAL -> List.of("All-time mined metal: ", "Metal this shaft: ");
			case COINS -> List.of("All-time coins: ");
			case OUTSIDE_COINS -> List.of("Outside shafts: ", "All-time outside shafts: ");
			default -> List.of();
		};
		if (old.contains(prefix)) return true;
		for (String target : targets()) {
			String name = targetName(target);
			boolean had = switch (target()) {
				case SHAFT_TYPE -> prefix.equals(name + " dry: ");
				case CORPSE_TYPE -> prefix.equals(name + " looted: ") || prefix.equals("All-time " + name + " looted: ")
						|| prefix.equals(name + " looted this shaft: ");
				case PERSONAL_BEST, NONE -> false;
			};
			if (had) return true;
		}
		return false;
	}

	// a label nobody typed: any default this value has or had, any range + target
	public boolean isDefaultPrefix(String prefix) {
		if (prefix == null || oldDefaultPrefix(prefix) || prefix.equals(defaultPrefix)) return true;
		List<String> targets = target() == Target.NONE ? Collections.singletonList(null) : targets();
		for (String target : targets) {
			if (prefix.equals(defaultPrefix(null, target))) return true;
			for (Range range : ranges) {
				if (prefix.equals(defaultPrefix(range, target))) return true;
			}
		}
		return false;
	}

	public String defaultPrefix(Range range) {
		return defaultPrefix(range, defaultTarget());
	}

	// noun 1st, period after. session = the short one. /h in the label, HudBoxText drops the values
	public String defaultPrefix(Range range, String target) {
		if (target() == Target.NONE && ranges.isEmpty() || multiRow()) return defaultPrefix;
		String name = targetName(target == null ? defaultTarget() : target);
		String noun = switch (target()) {
			case PERSONAL_BEST -> "PB " + name;
			case SHAFT_TYPE -> name + " drystreak";
			case CORPSE_TYPE -> name + " corpses";
			// all end in ": "
			case NONE -> defaultPrefix.substring(0, defaultPrefix.length() - 2);
		};
		if (!hasRange(range)) return noun + ": ";
		return switch (range) {
			case SHAFT -> switch (this) {
				case SESSION_POWDER -> "Powder this shaft: ";
				case AVERAGE_SPAWN -> "Spawn time this shaft: ";
				default -> noun + " this shaft: ";
			};
			case SESSION -> noun + ": ";
			case PER_HOUR -> noun + "/h: ";
			case ALL_TIME -> noun + " all-time: ";
		};
	}

	public String defaultColor() {
		return defaultColor(defaultTarget());
	}

	// colour code for new lines, null = follows the box. display choice
	public String defaultColor(String target) {
		switch (target()) {
			case CORPSE_TYPE -> {
				String label = targetName(target);
				return CorpseType.of(label) == null ? null : CorpseType.color(label).substring(1);
			}
			case SHAFT_TYPE -> {
				String code = shaftCode(target);
				Character color = code == null ? null : ShaftNames.defaults().hudColor(code);
				return color == null ? null : String.valueOf(color);
			}
			case PERSONAL_BEST, NONE -> {
			}
		}
		return switch (this) {
			case COINS, COINS_PER_SHAFT, OUTSIDE_COINS, SHAFT_CAMPFIRES -> "6";
			// game's glacite powder + cold colour
			case SESSION_POWDER, SHAFT_COLD -> "b";
			// fine = rare
			case SESSION_CORPSE_FINE -> "9";
			// games essence colour
			case FOSSIL_ESSENCE -> "d";
			// games colour for the pity number
			case PITY -> "e";
			// item rarity colours (epic, rare), cold resistance in the games aqua
			case PRISTINE_POTATO -> "5";
			case FILET_O_FORTUNE -> "9";
			case COLD_RESISTANCE -> "b";
			default -> null;
		};
	}

	// index into HudFormat.options, consumables count down in whole minutes
	public int defaultFormat() {
		return consumable() != null ? 3 : 0;
	}

	// null = not a consumable timer
	public Consumable consumable() {
		return switch (this) {
			case PRISTINE_POTATO -> Consumable.PRISTINE_POTATO;
			case FILET_O_FORTUNE -> Consumable.FILET_O_FORTUNE;
			case COLD_RESISTANCE -> Consumable.COLD_RESISTANCE;
			case FIESTA_FLASK -> Consumable.FIESTA_FLASK;
			default -> null;
		};
	}

	// shows the last shaft outside one, or - if the line asks
	public boolean followsShaft(Range range) {
		return group == Group.SHAFT || range == Range.SHAFT;
	}

	// small numbers, 1,204 vs 1.2k never matters
	public boolean hasFormatChoice() {
		return this != SHAFT_CAMPFIRES && this != FPS && this != PING && this != SHAFTS_TODAY && this != EFFICIENCY;
	}

	public Target target() {
		return switch (this) {
			case DRYSTREAK -> Target.SHAFT_TYPE;
			case CORPSES_LOOTED -> Target.CORPSE_TYPE;
			case PERSONAL_BEST -> Target.PERSONAL_BEST;
			default -> Target.NONE;
		};
	}

	// tag names, f.e. "jasper". null = none
	public String defaultTarget() {
		return switch (target()) {
			// most wanted shaft, research/Text File
			case SHAFT_TYPE -> "jasper";
			case CORPSE_TYPE -> "lapis";
			case PERSONAL_BEST -> {
				List<PersonalBests.Def> list = personalBests.list();
				yield PersonalBests.key(list.isEmpty() ? PersonalBests.DEFAULTS.getFirst() : list.getFirst());
			}
			case NONE -> null;
		};
	}

	public List<String> targets() {
		return switch (target()) {
			case SHAFT_TYPE -> Fixed.SHAFTS;
			case CORPSE_TYPE -> Fixed.CORPSES;
			case PERSONAL_BEST -> personalBests.keys();
			case NONE -> List.of();
		};
	}

	public boolean hasTarget(String target) {
		return target != null && targets().contains(target);
	}

	// a pb removed from the list stays on its line + shows -. still has to look like a key, so typos error
	public boolean acceptsTarget(String target) {
		if (target() != Target.PERSONAL_BEST) return hasTarget(target);
		if (target == null) return false;
		for (PersonalBests.Stat stat : PersonalBests.Stat.values()) {
			if (target.equals(stat.id()) || target.startsWith(stat.id() + "_")) return true;
		}
		return false;
	}

	// pbs: the default is just the 1st in the list, so lines + tags always save theirs
	public boolean pinsTarget() {
		return target() == Target.PERSONAL_BEST;
	}

	// unknown or none = the default
	public String targetOrDefault(String target) {
		return acceptsTarget(target) ? target : defaultTarget();
	}

	// "jasper" -> "Jasper"
	public String targetName(String target) {
		if (target == null) return "";
		return switch (target()) {
			case SHAFT_TYPE -> {
				String code = shaftCode(target);
				yield code == null ? target : ShaftNames.defaults().name(code);
			}
			case CORPSE_TYPE -> {
				for (String label : CorpseType.ORDER) {
					if (targetTag(label).equals(target)) yield label;
				}
				yield target;
			}
			case PERSONAL_BEST -> {
				PersonalBests.Def def = personalBest(target);
				yield def == null ? target : PersonalBests.name(def, ShaftNames.defaults());
			}
			case NONE -> target;
		};
	}

	// "jasper" -> "JASP", null = unknown
	private static String shaftCode(String tag) {
		ShaftNames names = ShaftNames.defaults();
		for (String code : names.codes()) {
			if (targetTag(names.name(code)).equals(tag)) return code;
		}
		return null;
	}

	public static String targetTag(String name) {
		return name.toLowerCase(Locale.ROOT).replace(' ', '_');
	}

	public String description() {
		return switch (this) {
			case SESSION_SHAFTS -> "Shafts you finished.";
			case SHAFTS_PER_HOUR -> "Shafts per hour of this session, counted up to now.";
			case AVERAGE_SPAWN -> "Average mining time it took to spawn a shaft. Spawn time of this shaft is how long the shaft "
					+ "you're in (or the last one) took. Only mining in the Dwarven Mines counts, gaps over 40 s count as 40 s.";
			case SESSION_POWDER -> "Glacite Powder from shafts: corpse loot plus the bonus for leaving. Not powder from mining.";
			case SESSION_CORPSE_FINE -> "Gemstones from corpse loot, counted in Fine. 80 Rough make a Flawed, 80 Flawed a Fine.";
			case MINED_METAL -> "Tungsten mined in Tungsten shafts and Umber in Umber shafts, in your Metal tier setting. "
					+ "Counted from Sacks messages, so it goes up every 30 s.";
			case FOSSIL_ESSENCE -> "Fossil Essence from mining fossils in shafts. The game only shows the last block's, so it's "
					+ "the fossil blocks you broke times the average shown.";
			case SPAWN_TIMER -> "Mining time since your last shaft. Block hits and pickaxe abilities in the Dwarven Mines "
					+ "count, gaps over 40 s count as 40 s.";
			case DRYSTREAK -> "Shafts since your last shaft of one type, f.e. Jasper. Any variant counts. 0 while you're in one.";
			case PITY -> "The game's shaft pity from the tab list: a shaft is guaranteed when it reaches the second number. "
					+ "Needs the Pity widget in your tab list, else it shows -.";
			case SINCE_LAST_SHAFT -> "Time since you left your last shaft. - while you're in one.";
			case CORPSES_LOOTED -> "Corpses you looted of one type, f.e. Lapis.";
			case SESSION_TIME -> "How long this session has been going.";
			case SHAFTS_TODAY -> "Shafts you finished today.";
			case EFFICIENCY -> "Blocks you broke against the blocks nonstop mining at your speed would break. In a shaft from "
					+ "your first gem block, in the tunnels since you left the last shaft. Starts over after 2 min without mining. "
					+ "Gemstone Spread doesn't count.";
			case PERSONAL_BEST -> "One of your personal bests from the Stats tab, over all your shafts.";
			case SHAFT_TYPE -> "The shaft you're in, or the last one, f.e. Jade, in its own colour.";
			case SHAFT_TIME -> "How long you've been in this shaft, or how long you were in the last one.";
			case SHAFT_CORPSES -> "Corpses looted out of all corpses in this shaft (or the last one), each type in its own colour. From the "
					+ "tab list.";
			case SHAFT_COLD -> "Your Cold in this shaft, from the scoreboard. After leaving, the Cold you left with.";
			case SHAFT_CAMPFIRES -> "Portable Campfires you placed in this shaft, or in the last one.";
			case COINS -> "Net coins from shafts: everything they gave, minus keys and campfires, at your price settings. "
					+ "Session, per hour and all-time also count what you mine outside shafts. Coins this shaft are "
					+ "live while you're inside.";
			case COINS_PER_SHAFT -> "Average net coins per shaft. Shafts only.";
			case OUTSIDE_COINS -> "Coins from what you mine in the Dwarven Mines outside shafts, from the Sacks messages, at "
					+ "your price settings. Items that go to your material stash aren't counted.";
			case DROPS -> "What your coins are made of, one row per item, biggest first. Gems count as Fine.";
			case PRISTINE_POTATO, FILET_O_FORTUNE, COLD_RESISTANCE, FIESTA_FLASK -> "Time left on your " + displayName
					+ ". Starts when you use one and follows the tab list and /effects. Hidden while it's off.";
			case FPS -> "Frames per second.";
			case PING -> "Time from your block breaks and clicks to the server's answer. Updates while you mine.";
			case TPS -> "Server ticks per second, 20 is full speed. Shows - where the server doesn't send tick pings.";
			case CLOCK -> "Your computer's time.";
			case MOD_VERSION -> "The Surveyor version you're running.";
			case MINECRAFT_VERSION -> "The Minecraft version you're running.";
		};
	}

	public List<Range> ranges() {
		return ranges;
	}

	// shafts today + per hour are periods of shafts, add line + line page only. ids + tags stay
	public HudValue family() {
		return this == SHAFTS_TODAY || this == SHAFTS_PER_HOUR ? SESSION_SHAFTS : this;
	}

	public boolean inAddLine() {
		return family() == this;
	}

	// in Range order, today after session. none = no periods
	public List<Period> periods() {
		if (family() != this) return family().periods();
		if (this == SESSION_SHAFTS) {
			return List.of(Period.of(this, Range.SESSION), new Period("Today", "today", SHAFTS_TODAY.description().replace(".", ""),
					SHAFTS_TODAY, null), new Period(Range.PER_HOUR.displayName(), "/h", Range.PER_HOUR.description(), SHAFTS_PER_HOUR, null),
					Period.of(this, Range.ALL_TIME));
		}
		if (ranges.size() < 2) return List.of();
		return ranges.stream().map(range -> Period.of(this, range)).toList();
	}

	// null = not one of this values periods
	public Period period(Range range) {
		for (Period period : periods()) {
			if (period.shows(this, range)) return period;
		}
		return null;
	}

	// page title + tooltips, docs/hud-rethink.md names table
	public String lineName(Range range, String target) {
		String name = targetName(target == null ? defaultTarget() : target);
		String noun = switch (this) {
			case SESSION_SHAFTS -> "Shafts";
			case AVERAGE_SPAWN -> range == Range.SHAFT ? "Spawn time of" : "Average spawn";
			case SESSION_POWDER -> range == Range.SHAFT ? "Powder from" : "Shaft powder";
			case SESSION_CORPSE_FINE -> "Corpse Fine";
			case CORPSES_LOOTED -> name + " corpses";
			case DRYSTREAK -> name + " drystreak";
			case SHAFT_CORPSES -> "Corpses in this shaft";
			case PERSONAL_BEST -> name;
			default -> displayName;
		};
		if (!hasRange(range)) return noun;
		return noun + switch (range) {
			case SHAFT -> " this shaft";
			case SESSION -> " this session";
			case PER_HOUR -> " per hour";
			case ALL_TIME -> " all-time";
		};
	}

	public boolean hasRange(Range range) {
		return range != null && ranges.contains(range);
	}

	// session so pre-range lines + tags still show the same
	public Range defaultRange() {
		return hasRange(Range.SESSION) ? Range.SESSION : ranges.isEmpty() ? null : ranges.getFirst();
	}

	public String unit() {
		return unit;
	}

	// range = the lines period
	public Graph graph(Range range) {
		return switch (this) {
			case PITY -> Graph.BAR;
			case EFFICIENCY -> Graph.LINE;
			// this shaft is 1 number, no chart
			case AVERAGE_SPAWN -> range == Range.SESSION || range == Range.ALL_TIME ? Graph.CHART : Graph.NONE;
			default -> Graph.NONE;
		};
	}

	public boolean hasNumber() {
		return kind != Kind.TEXT;
	}

	// drops draws rows, {drops} stays as typed
	public boolean isTag() {
		return !multiRow();
	}

	// draws its own rows, no label or tag
	public boolean multiRow() {
		return this == DROPS;
	}

	// tag name, or the old id
	public static HudValue byTag(String name) {
		for (HudValue value : values()) {
			if (value.isTag() && value.tag().equals(name)) return value;
		}
		HudValue value = byId(name);
		return value == null || !value.isTag() ? null : value;
	}

	public static List<HudValue> listed() {
		return Fixed.LISTED;
	}

	public static HudValue byId(String id) {
		for (HudValue value : values()) {
			if (value.id.equals(id)) return value;
		}
		return null;
	}
}
