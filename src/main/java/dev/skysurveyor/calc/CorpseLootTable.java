package dev.skysurveyor.calc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import dev.skysurveyor.util.Resources;

// corpse_loot.txt, avg drops (research/16)
public final class CorpseLootTable {
	public record Row(String id, int amount, int weight, String name) {
	}

	public record Table(int leastRolls, int mostRolls, List<Row> rows) {
		public double averageRolls() {
			return (leastRolls + mostRolls) / 2.0;
		}

		public int totalWeight() {
			return rows.stream().mapToInt(Row::weight).sum();
		}
	}

	// unpriced rows count as 0
	public record Value(double worth, double perRoll, double rolls, List<String> unpriced) {
	}

	private static CorpseLootTable defaults;

	private final Map<String, Table> tables;

	private CorpseLootTable(Map<String, Table> tables) {
		this.tables = Map.copyOf(tables);
	}

	public static synchronized CorpseLootTable defaults() {
		if (defaults == null) {
			List<String> lines = Resources.lines("/skysurveyor/corpse_loot.txt");
			defaults = parse(lines == null ? List.of() : lines);
		}
		return defaults;
	}

	public static CorpseLootTable parse(List<String> lines) {
		Map<String, int[]> rolls = new HashMap<>();
		Map<String, List<Row>> rows = new HashMap<>();
		for (String line : lines) {
			int hash = line.indexOf('#');
			String text = (hash >= 0 ? line.substring(0, hash) : line).strip();
			if (text.isEmpty()) continue;
			int eq = text.indexOf('=');
			if (eq < 0) throw new IllegalArgumentException("Bad corpse loot line: " + line);
			String[] key = text.substring(0, eq).strip().split("\\s+");
			String value = text.substring(eq + 1).strip();
			if (key[0].equals("rolls")) {
				String[] parts = value.split("\\s+");
				rolls.put(key[1], new int[] {Integer.parseInt(parts[0]), Integer.parseInt(parts[1])});
			} else if (key[0].equals("loot")) {
				String[] parts = value.split(",", 4);
				rows.computeIfAbsent(key[1], k -> new ArrayList<>()).add(new Row(parts[0].strip(), Integer.parseInt(parts[1].strip()),
						Integer.parseInt(parts[2].strip()), parts[3].strip()));
			}
		}
		Map<String, Table> tables = new HashMap<>();
		rows.forEach((name, list) -> {
			int[] r = rolls.get(name);
			if (r == null) throw new IllegalArgumentException("No rolls for " + name);
			tables.put(name, new Table(r[0], r[1], List.copyOf(list)));
		});
		return new CorpseLootTable(tables);
	}

	// null = no table
	public Table of(String corpseType) {
		return switch (corpseType) {
			case "Umber", "Tungsten" -> tables.get("umber_tungsten");
			default -> null;
		};
	}

	// price null = none. extraRoll = chance of 1 more roll
	public static Value value(Table table, Function<Row, Double> price, double extraRoll) {
		double sum = 0;
		List<String> unpriced = new ArrayList<>();
		for (Row row : table.rows()) {
			Double each = price.apply(row);
			if (each == null) {
				if (!unpriced.contains(row.name())) unpriced.add(row.name());
				continue;
			}
			sum += row.weight() * row.amount() * each;
		}
		double perRoll = sum / table.totalWeight();
		double rolls = table.averageRolls() + extraRoll;
		return new Value(perRoll * rolls, perRoll, rolls, List.copyOf(unpriced));
	}
}
