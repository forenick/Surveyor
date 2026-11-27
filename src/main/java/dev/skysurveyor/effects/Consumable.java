package dev.skysurveyor.effects;

import dev.skysurveyor.detection.TextPatterns;

// ids saved in effects.json + pattern keys, never rename
public enum Consumable {
	PRISTINE_POTATO("pristine_potato"),
	FILET_O_FORTUNE("filet_o_fortune"),
	COLD_RESISTANCE("cold_resistance"),
	FIESTA_FLASK("fiesta_flask");

	private final String id;

	Consumable(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	public static Consumable byId(String id) {
		for (Consumable c : values()) {
			if (c.id.equals(id)) return c;
		}
		return null;
	}

	// codes stripped
	public static Consumable ofItem(TextPatterns patterns, String name) {
		for (Consumable c : values()) {
			if (patterns.matches("consumable." + c.id, name)) return c;
		}
		return null;
	}

	// w/o level
	public static Consumable ofEffect(TextPatterns patterns, String effect) {
		for (Consumable c : values()) {
			if (patterns.matches("effect." + c.id, effect)) return c;
		}
		return null;
	}
}
