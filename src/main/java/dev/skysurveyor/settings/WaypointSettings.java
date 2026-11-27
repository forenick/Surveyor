package dev.skysurveyor.settings;

import dev.skysurveyor.util.Format;

// "waypoints" in config.json, names never rename. color = "#RRGGBB"
public record WaypointSettings(Boolean fossil, String color) {
	// on, nick 11-21. purple = display choice
	public static final WaypointSettings DEFAULTS = new WaypointSettings(true, "#FF55FF");

	// missing or weird = default
	public WaypointSettings repaired() {
		return new WaypointSettings(fossil == null ? DEFAULTS.fossil : fossil, Format.rgb(color) == null ? DEFAULTS.color : color);
	}

	public boolean isFossil() {
		return fossil;
	}

	public int colorRgb() {
		Integer rgb = Format.rgb(color);
		return rgb == null ? Format.rgb(DEFAULTS.color) : rgb;
	}

	public WaypointSettings withFossil(boolean value) {
		return new WaypointSettings(value, color);
	}

	public WaypointSettings withColor(int rgb) {
		return new WaypointSettings(fossil, Format.hex(rgb));
	}
}
