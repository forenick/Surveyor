package dev.skysurveyor.hud;

import java.util.List;
import java.util.Locale;

// ids saved in hud.json, never rename. tags use tag()
public enum HudValue {
	SESSION_SHAFTS("session_shafts", "Shafts", Group.SESSION, Kind.INTEGER, "Shafts: ", Range.SESSION, Range.ALL_TIME),
	SHAFTS_PER_HOUR("shafts_per_hour", "Shafts per hour", Group.SESSION, Kind.DECIMAL, "Shafts/h: "),
	AVERAGE_SPAWN("average_spawn", "Average spawn", Group.SPAWN, Kind.TIME, "Avg spawn: ", Range.SHAFT, Range.SESSION,
			Range.ALL_TIME),
	SESSION_POWDER("session_powder", "Glacite Powder from shafts", Group.SESSION, Kind.INTEGER, "Shaft powder: ",
			Range.values()),
	SESSION_CORPSE_FINE("session_corpse_fine", "Corpse gems (Fine)", Group.SESSION, Kind.DECIMAL, "Corpse Fine: ", Range.values()),
	SPAWN_TIMER("spawn_timer", "Spawn timer", Group.SPAWN, Kind.TIME, "Spawn: "),
	SHAFT_TYPE("shaft_type", "Shaft type", Group.SHAFT, Kind.TEXT, "Shaft: "),
	SHAFT_TIME("shaft_time", "Time inside", Group.SHAFT, Kind.TIME, "Time inside: "),
	SHAFT_CORPSES("shaft_corpses", "Corpses", Group.SHAFT, Kind.TEXT, "Corpses: "),
	SHAFT_COLD("shaft_cold", "Cold", Group.SHAFT, Kind.COLD, "Cold: "),
	SHAFT_CAMPFIRES("shaft_campfires", "Campfires", Group.SHAFT, Kind.INTEGER, "Campfires: "),
	// net, shaft = live while inside
	COINS("coins", "Coins", Group.MONEY, Kind.COINS, "Coins: ", Range.values()),
	COINS_PER_SHAFT("coins_per_shaft", "Avg coins per shaft", Group.MONEY, Kind.COINS, "Avg coins/shaft: ", Range.SESSION,
			Range.ALL_TIME),
	FPS("fps", "FPS", Group.GAME, Kind.INTEGER, "FPS: "),
	PING("ping", "Ping", Group.GAME, Kind.INTEGER, "Ping: ", "ms"),
	TPS("tps", "TPS", Group.GAME, Kind.DECIMAL, "TPS: "),
	CLOCK("clock", "Clock", Group.GAME, Kind.TEXT, "Clock: "),
	MOD_VERSION("mod_version", "Surveyor version", Group.GAME, Kind.TEXT, "Surveyor "),
	MINECRAFT_VERSION("minecraft_version", "Minecraft version", Group.GAME, Kind.TEXT, "Minecraft ");

	// picker headings, display only
	public enum Group {
		SPAWN("Spawn"),
		SESSION("Totals"),
		SHAFT("Shaft"),
		MONEY("Money"),
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
		SHAFT("Shaft", "The shaft you're in, or the last one while you're outside"),
		SESSION("Session", "This session. It ends after 15 min without mining in the Dwarven Mines or shafting"),
		PER_HOUR("Per hour", "The session amount per hour of this session, counted up to now"),
		ALL_TIME("All-time", "Every shaft in your journal");

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
	public boolean oldDefaultPrefix(String prefix) {
		List<String> old = switch (this) {
			// 0.22 + 0.23 alpha.1-2
			case SESSION_POWDER -> List.of("Powder: ", "All-time powder: ");
			case SESSION_CORPSE_FINE -> List.of("Fine: ");
			case SHAFT_TIME -> List.of("Inside: ");
			// 0.22, 0.23 alpha.1
			case COINS_PER_SHAFT -> List.of("Coins/shaft: ", "All-time avg/shaft: ");
			case CLOCK -> List.of("Time: ");
			default -> List.of();
		};
		return old.contains(prefix);
	}

	// per hour has /h on the value already
	public String defaultPrefix(Range range) {
		if (range == Range.ALL_TIME) {
			return switch (this) {
				case SESSION_SHAFTS -> "All-time shafts: ";
				case SESSION_POWDER -> "All-time shaft powder: ";
				case AVERAGE_SPAWN -> "All-time avg spawn: ";
				case SESSION_CORPSE_FINE -> "All-time corpse Fine: ";
				case COINS -> "All-time coins: ";
				case COINS_PER_SHAFT -> "All-time avg coins/shaft: ";
				default -> defaultPrefix;
			};
		}
		if (range == Range.SHAFT) {
			return switch (this) {
				case SESSION_POWDER -> "Powder this shaft: ";
				case AVERAGE_SPAWN -> "This shaft's spawn: ";
				case SESSION_CORPSE_FINE -> "Corpse Fine this shaft: ";
				case COINS -> "Coins this shaft: ";
				default -> defaultPrefix;
			};
		}
		return defaultPrefix;
	}

	// colour code for new lines, null = follows the box. display choice
	public String defaultColor() {
		return switch (this) {
			case COINS, COINS_PER_SHAFT, SHAFT_CAMPFIRES -> "6";
			// game's glacite powder + cold colour
			case SESSION_POWDER, SHAFT_COLD -> "b";
			// fine = rare
			case SESSION_CORPSE_FINE -> "9";
			default -> null;
		};
	}

	// shows the last shaft outside one, or - if the line asks
	public boolean followsShaft(Range range) {
		return group == Group.SHAFT || range == Range.SHAFT;
	}

	// small numbers, 1,204 vs 1.2k never matters
	public boolean hasFormatChoice() {
		return this != SHAFT_CAMPFIRES && this != FPS && this != PING;
	}

	public String description() {
		return switch (this) {
			case SESSION_SHAFTS -> "Shafts you finished.";
			case SHAFTS_PER_HOUR -> "Shafts per hour of this session, counted up to now.";
			case AVERAGE_SPAWN -> "Average mining time it took to spawn a shaft. The shaft range is how long the shaft "
					+ "you're in (or the last one) took. Only mining in the Dwarven Mines counts, gaps over 40 s count as 40 s.";
			case SESSION_POWDER -> "Glacite Powder from shafts: corpse loot plus the bonus for leaving. Not powder from mining.";
			case SESSION_CORPSE_FINE -> "Gemstones from corpse loot, counted in Fine. 80 Rough make a Flawed, 80 Flawed a Fine.";
			case SPAWN_TIMER -> "Mining time since your last shaft. Block hits and pickaxe abilities in the Dwarven Mines "
					+ "count, gaps over 40 s count as 40 s.";
			case SHAFT_TYPE -> "The shaft you're in, or the last one, f.e. Jade, in its own colour.";
			case SHAFT_TIME -> "How long you've been in this shaft, or how long you were in the last one.";
			case SHAFT_CORPSES -> "Corpses looted out of all corpses in this shaft (or the last one), each type in its own colour. From the "
					+ "tab list.";
			case SHAFT_COLD -> "Your Cold in this shaft, from the scoreboard. After leaving, the Cold you left with.";
			case SHAFT_CAMPFIRES -> "Portable Campfires you placed in this shaft, or in the last one.";
			case COINS -> "Net coins from shafts: everything they gave, minus keys and campfires, at your price settings. "
					+ "The shaft range is live while you're inside.";
			case COINS_PER_SHAFT -> "Average net coins per shaft.";
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

	public boolean hasNumber() {
		return kind != Kind.TEXT;
	}

	// tag name, or the old id
	public static HudValue byTag(String name) {
		for (HudValue value : values()) {
			if (value.tag().equals(name)) return value;
		}
		return byId(name);
	}

	public static HudValue byId(String id) {
		for (HudValue value : values()) {
			if (value.id.equals(id)) return value;
		}
		return null;
	}
}
