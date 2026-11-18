package dev.skysurveyor.data;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.skysurveyor.util.Resources;

public final class ShaftNames {
	public static final int DEFAULT_RGB = 0x55FFFF;
	public static final String CRYSTAL = "C";
	// littlefoots den, LITT_L
	public static final String LITTLEFOOT = "L";

	private static final Pattern COLOR = Pattern.compile("\\s*#([0-9A-Fa-f]{6})(?:\\s+&([0-9a-f]))?(?![0-9A-Za-z])");

	private static ShaftNames defaults;

	// hudColor null = none given
	private record Type(String name, int rgb, Character hudColor) {
	}

	// file order
	private final Map<String, Type> types = new LinkedHashMap<>();

	ShaftNames(List<String> lines) {
		for (String line : lines) {
			int eq = line.indexOf('=');
			if (eq < 0 || line.strip().startsWith("#")) continue;
			String value = line.substring(eq + 1);
			int comma = value.indexOf(',');
			String name = comma < 0 ? value : value.substring(0, comma);
			// '#' in name part = comment
			int comment = name.indexOf('#');
			if (comment >= 0) name = name.substring(0, comment);
			int rgb = DEFAULT_RGB;
			Character hudColor = null;
			if (comma >= 0) {
				Matcher color = COLOR.matcher(value.substring(comma + 1));
				if (color.lookingAt()) {
					rgb = Integer.parseInt(color.group(1), 16);
					if (color.group(2) != null) hudColor = color.group(2).charAt(0);
				}
			}
			types.put(line.substring(0, eq).strip(), new Type(name.strip(), rgb, hudColor));
		}
	}

	public static String variantName(String variant) {
		return switch (variant) {
			case "1" -> "type 1";
			case "2" -> "type 2";
			case CRYSTAL -> "crystal";
			default -> "_" + variant;
		};
	}

	public static synchronized ShaftNames defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/shaft_types.txt");
			defaults = new ShaftNames(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	public List<String> codes() {
		return List.copyOf(types.keySet());
	}

	public String name(String code) {
		Type type = types.get(code);
		return type == null ? code : type.name();
	}

	// § code for the HUD, null = none
	public Character hudColor(String code) {
		Type type = types.get(code);
		return type == null ? null : type.hudColor();
	}

	// by name, f.e. "Jade" or "Tungsten". null = none
	public Character hudColorByName(String name) {
		for (Type type : types.values()) {
			if (type.name().equalsIgnoreCase(name)) return type.hudColor();
		}
		return null;
	}

	// gem or metal name, white w/o one. journals mined column + hud drops
	public char minedColor(String name) {
		Character color = hudColorByName(name);
		return color == null ? 'f' : color;
	}

	public int rgb(String code) {
		Type type = types.get(code);
		return type == null ? DEFAULT_RGB : type.rgb();
	}
}
