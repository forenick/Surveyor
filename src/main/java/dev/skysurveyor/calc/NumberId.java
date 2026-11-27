package dev.skysurveyor.calc;

import java.util.Locale;

import dev.skysurveyor.money.Coins;
import dev.skysurveyor.util.Format;

// keys saved in config.json, never rename
public enum NumberId {
	SPAWN("spawn", "Average spawn", Segment.PLAY, Kind.SECONDS, "Mining time per shaft you spawn, like the HUD's Average spawn"),
	NOT_MINING("not_mining", "Time per skip", Segment.PLAY, Kind.SECONDS,
			"What a skipped shaft costs on top of the spawn: going in, looking around and back to your spot"),
	BACK_MINED("back_mined", "Back after mining", Segment.PLAY, Kind.SECONDS,
			"Time from leaving a shaft you mined until you mine in the tunnels again"),
	COINS_PER_SPAWN("coins_per_spawn", "Coins per spawn", Segment.PLAY, Kind.COINS, "Coins you mine in the tunnels while spawning a shaft"),
	EFFICIENCY("efficiency", "Efficiency", Segment.PLAY, Kind.PERCENT,
			"Blocks you break against nonstop mining, from your first gem block. The HUD's Efficiency"),
	SETUP("setup", "Setup", Segment.PLAY, Kind.SECONDS, "Time in a shaft before your first gem block: corpses, mobs, getting ready"),
	BOOST("boost", "Boost time", Segment.PLAY, Kind.PERCENT, "Share of your mining time in Mining Speed Boost"),
	LAPIS_LOOT("lapis_loot", "Lapis corpse loot", Segment.PLAY, Kind.COINS, "What a Lapis corpse's loot sells for, Pristine not counted"),
	OTHER("other", "Other drops a minute", Segment.PLAY, Kind.COINS,
			"Coins a minute in a shaft from everything but gems: Glossy Gemstones, Suspicious Scrap, Fossil Essence, mob drops, ores"),
	TICKS_4000("ticks_4000", "Ticks on Jasper and BP9 gems", Segment.GEAR, Kind.TICKS,
			"Ticks a Jasper, Onyx, Peridot, Citrine or Aquamarine block takes you, out of Mining Speed Boost"),
	TICKS_3500("ticks_3500", "Ticks on Topaz", Segment.GEAR, Kind.TICKS, "Ticks a Topaz block takes you, out of Mining Speed Boost"),
	TICKS_3000("ticks_3000", "Ticks on BP7 gems", Segment.GEAR, Kind.TICKS,
			"Ticks an Amber, Sapphire, Amethyst, Jade or Opal block takes you, out of Mining Speed Boost"),
	TICKS_2500("ticks_2500", "Ticks on Ruby", Segment.GEAR, Kind.TICKS, "Ticks a Ruby block takes you, out of Mining Speed Boost"),
	BOOST_TICKS("boost_ticks", "Ticks in boost", Segment.GEAR, Kind.TICKS, "Ticks a Jasper or BP9 gem block takes you in Mining Speed Boost"),
	LOST_TICKS("lost_ticks", "Ticks lost per block", Segment.GEAR, Kind.DECIMAL,
			"Ticks between one block breaking and the next starting: aiming, moving, ping. Tracked from your blocks in and out of Mining Speed Boost"),
	SPREAD("spread", "Gemstone Spread", Segment.GEAR, Kind.PERCENT, "Extra blocks Gemstone Spread breaks, as a share of your own"),
	GEMS_PER_BLOCK("gems_per_block", "Gems per block", Segment.GEAR, Kind.ROUGH,
			"Rough gems a block drops for you, before Pristine. Goes up with Mining Fortune and the Mining Fiesta"),
	FLAWED("flawed", "Flawed share", Segment.GEAR, Kind.PERCENT, "Share of your gems that drop Flawed, with no corpse looted"),
	FREEZE("freeze", "Freeze after", Segment.SHAFTS, Kind.SECONDS, "Time in a shaft until you freeze, no campfire"),
	CAMPFIRE("campfire", "Campfire adds", Segment.SHAFTS, Kind.SECONDS, "Time one campfire adds, from your freeze time. A 2nd adds less"),
	SPAWN_GEM("spawn_gem", "Shafts of your spawn gem", Segment.SHAFTS, Kind.PERCENT, "How many of your shafts are the gem you mine to spawn them"),
	EXTRA_CORPSE("extra_corpse", "Extra corpse", Segment.SHAFTS, Kind.PERCENT, "Share of your shafts with an extra corpse"),
	LAPIS_SHARE("lapis_share", "Lapis share", Segment.SHAFTS, Kind.PERCENT, "Share of your corpses that are Lapis"),
	KEY_LOOT("key_loot", "Key corpse loot", Segment.SHAFTS, Kind.COINS,
			"What an Umber or Tungsten corpse's loot is worth on average, Pristine not counted. Change it in Settings, Journal"),
	EXTRA_ROLL("extra_roll", "Extra loot roll", Segment.SHAFTS, Kind.PERCENT,
			"Share of your corpses with a +1 bonus drop: Gifts from the Departed and corpse milestones"),
	CLOSE_CALL("close_call", "Your call", Segment.SHAFTS, Kind.COINS,
			"A gain from 0 up to this shows yellow: worth a little, mine it or skip it. The HUD's Shaft gain and Campfire gain "
					+ "colours are set in Edit HUD"),
	KEY_CLOSE_CALL("key_close_call", "Your call on keys", Segment.SHAFTS, Kind.COINS, "A key corpse gain from 0 up to this shows yellow");

	public enum Segment {
		PLAY("How you play"),
		GEAR("Your gear"),
		SHAFTS("Your shafts");

		private final String displayName;

		Segment(String displayName) {
			this.displayName = displayName;
		}

		public String displayName() {
			return displayName;
		}
	}

	public enum Kind {
		SECONDS,
		PERCENT,
		COINS,
		TICKS,
		DECIMAL,
		ROUGH
	}

	private final String key;
	private final String displayName;
	private final Segment segment;
	private final Kind kind;
	private final String description;

	NumberId(String key, String displayName, Segment segment, Kind kind, String description) {
		this.key = key;
		this.displayName = displayName;
		this.segment = segment;
		this.kind = kind;
		this.description = description;
	}

	public String key() {
		return key;
	}

	public String displayName() {
		return displayName;
	}

	public Segment segment() {
		return segment;
	}

	public Kind kind() {
		return kind;
	}

	public String description() {
		return description;
	}

	// campfire follows the freeze time
	public boolean typeable() {
		return this != CAMPFIRE;
	}

	public static NumberId byKey(String key) {
		for (NumberId id : values()) {
			if (id.key.equals(key)) return id;
		}
		return null;
	}

	public String format(double value) {
		return switch (kind) {
			case SECONDS -> seconds(value);
			case PERCENT -> Format.percent(value);
			case COINS -> Coins.format(value);
			case TICKS -> Math.abs(value - Math.round(value)) < 0.05 ? Long.toString(Math.round(value)) : Format.decimal(value);
			case DECIMAL -> Format.decimal(value);
			case ROUGH -> Long.toString(Math.round(value));
		};
	}

	// 104 -> "1m 44s", 12:50 for the freeze
	private String seconds(double value) {
		long whole = Math.round(value);
		if (this == FREEZE || this == CAMPFIRE) return whole / 60 + ":" + String.format(Locale.ROOT, "%02d", whole % 60);
		return Format.duration(whole * 1000);
	}

	// null = not a number this takes. "1:44", "104", "64%", "1.4M", "500k"
	public Double parse(String text) {
		String t = commas(text.strip().toLowerCase(Locale.ROOT).replace(" ", ""));
		if (t == null || t.isEmpty()) return null;
		try {
			Double value = switch (kind) {
				case SECONDS -> parseSeconds(t);
				case PERCENT -> Double.parseDouble(t.endsWith("%") ? t.substring(0, t.length() - 1) : t) / 100;
				case COINS -> parseCoins(t);
				case TICKS, DECIMAL, ROUGH -> Double.parseDouble(t.startsWith("x") ? t.substring(1) : t);
			};
			return value == null || !valid(value) ? null : value;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	// "1,500" = 1500, "1,5" = 1.5. null = can't tell
	static String commas(String t) {
		if (!t.contains(",")) return t;
		if (t.matches("[^,]*\\d(,\\d{3}(?!\\d)[^,]*)+")) return t.replace(",", "");
		if (t.indexOf(',') == t.lastIndexOf(',') && !t.contains(".")) return t.replace(',', '.');
		return null;
	}

	// typed or hand edited in config.json
	public boolean valid(double value) {
		return Double.isFinite(value) && value >= 0 && inRange(value);
	}

	// keeps the model off 0 ticks and such
	private boolean inRange(double value) {
		return switch (kind) {
			case PERCENT -> value <= (this == SPREAD ? 2 : 1);
			case TICKS -> value >= 1 && value <= 200;
			case SECONDS -> this == FREEZE ? value >= 60 && value <= 7200 : value <= 7200;
			// so a typo cant blow the table up. display choice
			case COINS -> value <= 1e12;
			case DECIMAL -> value <= 100;
			case ROUGH -> value <= 1000;
		};
	}

	private static Double parseSeconds(String t) {
		if (t.contains(":")) {
			String[] parts = t.split(":");
			if (parts.length != 2) return null;
			return Double.parseDouble(parts[0]) * 60 + Double.parseDouble(parts[1]);
		}
		if (t.contains("m") || t.endsWith("s")) {
			double total = 0;
			String rest = t;
			int m = rest.indexOf('m');
			if (m >= 0) {
				total += Double.parseDouble(rest.substring(0, m)) * 60;
				rest = rest.substring(m + 1);
			}
			if (rest.endsWith("s")) rest = rest.substring(0, rest.length() - 1);
			if (!rest.isEmpty()) total += Double.parseDouble(rest);
			return total;
		}
		return Double.parseDouble(t);
	}

	private static Double parseCoins(String t) {
		double factor = 1;
		if (t.endsWith("b")) factor = 1e9;
		else if (t.endsWith("m")) factor = 1e6;
		else if (t.endsWith("k")) factor = 1e3;
		String number = factor == 1 ? t : t.substring(0, t.length() - 1);
		return Double.parseDouble(number) * factor;
	}
}
