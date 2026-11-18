package dev.skysurveyor.data;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.util.Resources;

// item names like hypixel writes them: rarity colour, gem icon, name, §8x count
public final class ItemNames {
	private static final char COMMON = 'f';

	// set on the client thread when hypixels pack (un)loads
	private static volatile boolean iconsLoaded;
	private static Data data;

	private record Data(Map<String, Character> rarities, Map<String, String> qualities, Map<String, Character> colors,
			Map<String, Character> icons) {
	}

	// name -> rarity (COMMON, RARE..), null = unknown
	private final Function<String, String> rarity;

	public ItemNames(Function<String, String> rarity) {
		this.rarity = rarity;
	}

	public static final ItemNames BUNDLED = new ItemNames(name -> null);
	// w/ hypixels rarities once the item list loaded, any thread
	private static volatile ItemNames current = BUNDLED;

	public static ItemNames current() {
		return current;
	}

	public static void use(ItemNames names) {
		current = names;
	}

	public static void iconsLoaded(boolean loaded) {
		iconsLoaded = loaded;
	}

	public static boolean iconsLoaded() {
		return iconsLoaded;
	}

	static Data parse(List<String> lines) {
		Map<String, Character> rarities = new HashMap<>();
		Map<String, String> qualities = new HashMap<>();
		Map<String, Character> colors = new HashMap<>();
		Map<String, Character> icons = new HashMap<>();
		for (String line : lines) {
			String stripped = line.strip();
			int eq = stripped.indexOf('=');
			int space = stripped.indexOf(' ');
			if (stripped.startsWith("#") || eq < 0 || space < 0 || space > eq) continue;
			String kind = stripped.substring(0, space);
			String key = stripped.substring(space + 1, eq).strip();
			String value = stripped.substring(eq + 1).strip();
			if (value.isEmpty()) continue;
			switch (kind) {
				case "rarity" -> rarities.put(key, value.charAt(0));
				case "quality" -> qualities.put(key, value);
				case "color" -> colors.put(key, value.charAt(0));
				case "icon" -> icons.put(key, (char) Integer.parseInt(value, 16));
				default -> {
				}
			}
		}
		return new Data(Map.copyOf(rarities), Map.copyOf(qualities), Map.copyOf(colors), Map.copyOf(icons));
	}

	private static synchronized Data data() {
		if (data == null) {
			List<String> lines = Resources.lines("/skysurveyor/item_names.txt");
			data = parse(lines == null ? List.of() : lines);
		}
		return data;
	}

	// § code char
	public char color(String name) {
		Data d = data();
		Character fixed = d.colors().get(name);
		if (fixed != null) return fixed;
		String tier = rarity.apply(name);
		if (tier == null) {
			Matcher gem = gem(name);
			tier = gem != null ? d.qualities().get(gem.group("quality")) : null;
		}
		if (tier == null) return COMMON;
		Character color = d.rarities().get(tier);
		return color == null ? COMMON : color;
	}

	// glyph + space, "" w/o hypixels pack or for non gems
	public String icon(String name) {
		if (!iconsLoaded) return "";
		Matcher gem = gem(name);
		if (gem == null) return "";
		Character icon = data().icons().get(gem.group("gem"));
		return icon == null ? "" : icon + " ";
	}

	private static Matcher gem(String name) {
		return TextPatterns.defaults().match("gem.any_tier", name);
	}

	public String styled(String name) {
		return "§" + color(name) + icon(name) + name;
	}

	// amount "1" left out like hypixel
	public String styled(String name, String amount) {
		return styled(name) + amount(amount);
	}

	public static String amount(String amount) {
		return amount == null || amount.isEmpty() || amount.equals("1") ? "" : " §8x" + amount;
	}
}
