package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.skysurveyor.util.Resources;

// calculator.txt
public final class GameData {
	public record Gem(String code, String name, int strength, int breakingPower) {
	}

	// group: layouts on 1 map share an efficiency. crystals per breaking power (CRYS_9), rest = id
	// route = own blocks till the route is done, simulated (calculator.txt). 0 = none
	public record Layout(String id, String gem, String variant, String group, double route) {
	}

	// same as ShaftNames.CRYSTAL
	static final String CRYSTAL = "C";
	// config.json keys hold it (efficiency_CRYS_9), never rename
	static final String CRYSTAL_GROUP = "CRYS_";

	private static GameData defaults;

	private final List<Gem> gems;
	private final Map<String, Double> values;
	// vanilla block id -> gem code
	private final Map<String, String> blocks;
	// file order
	private final List<Layout> layouts;
	private final Map<String, Layout> layoutsById = new HashMap<>();
	// strongest first
	private final List<Integer> strengths;

	private GameData(List<Gem> gems, Map<String, Double> values, Map<String, String> blocks, List<Layout> layouts) {
		this.gems = List.copyOf(gems);
		this.values = Map.copyOf(values);
		this.blocks = Map.copyOf(blocks);
		this.layouts = List.copyOf(layouts);
		for (Layout layout : layouts) layoutsById.put(layout.id(), layout);
		this.strengths = gems.stream().map(Gem::strength).distinct().sorted((a, b) -> b - a).toList();
	}

	public static synchronized GameData defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/calculator.txt");
			defaults = parse(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	public static GameData parse(List<String> lines) {
		List<Gem> gems = new ArrayList<>();
		Map<String, Double> values = new HashMap<>();
		Map<String, String> blocks = new HashMap<>();
		List<Layout> layouts = new ArrayList<>();
		for (String line : lines) {
			int hash = line.indexOf('#');
			String text = (hash >= 0 ? line.substring(0, hash) : line).strip();
			if (text.isEmpty()) continue;
			int eq = text.indexOf('=');
			if (eq < 0) throw new IllegalArgumentException("Bad calculator line: " + line);
			String key = text.substring(0, eq).strip();
			String value = text.substring(eq + 1).strip();
			if (key.startsWith("gem ")) {
				String[] parts = value.split(",");
				gems.add(new Gem(key.substring(4).strip(), parts[0].strip(), Integer.parseInt(parts[1].strip()), Integer.parseInt(parts[2].strip())));
			} else if (key.startsWith("block ")) {
				blocks.put(key.substring(6).strip(), value);
			} else if (key.startsWith("layout ")) {
				String id = key.substring(7).strip();
				int split = id.indexOf('_');
				String code = id.substring(0, split);
				String variant = id.substring(split + 1);
				// gem lines come 1st in the file
				Gem gem = gems.stream().filter(g -> g.code().equals(code)).findFirst().orElse(null);
				boolean crystal = variant.equals(CRYSTAL) && gem != null;
				layouts.add(new Layout(id, code, variant, crystal ? CRYSTAL_GROUP + gem.breakingPower() : id, Double.parseDouble(value)));
			} else {
				values.put(key, Double.parseDouble(value));
			}
		}
		return new GameData(gems, values, blocks, layouts);
	}

	public List<Gem> gems() {
		return gems;
	}

	// null = not a gem shaft
	public Gem gem(String code) {
		for (Gem gem : gems) {
			if (gem.code().equals(code)) return gem;
		}
		return null;
	}

	// block or pane, null = not a gem
	public Gem gemOfBlock(String block) {
		if (block == null) return null;
		String code = blocks.get(block.endsWith("_pane") ? block.substring(0, block.length() - "_pane".length()) : block);
		return code == null ? null : gem(code);
	}

	public Gem gemByName(String name) {
		for (Gem gem : gems) {
			if (gem.name().equalsIgnoreCase(name)) return gem;
		}
		return null;
	}

	public List<Layout> layouts() {
		return layouts;
	}

	// null = not a gem layout or a new one
	public Layout layout(String id) {
		return id == null ? null : layoutsById.get(id);
	}

	public List<Layout> layoutsOf(String gem) {
		return layouts.stream().filter(l -> l.gem().equals(gem)).toList();
	}

	public double value(String key) {
		Double value = values.get(key);
		if (value == null) throw new IllegalStateException("calculator.txt has no " + key);
		return value;
	}

	public double start(String key) {
		return value("start." + key);
	}

	public List<Integer> strengths() {
		return strengths;
	}

	public int ticks(int strength, double speed) {
		double raw = strength * value("tick_factor") / speed;
		return (int) Math.max(value("min_ticks"), Math.floor(raw + 0.5));
	}
}
