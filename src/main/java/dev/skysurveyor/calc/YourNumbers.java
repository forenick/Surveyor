package dev.skysurveyor.calc;

import java.util.EnumMap;
import java.util.Map;

// spawnGem = gem code u mine most in tunnels, null = none
// fiesta = numbers per state, numbers has the one in use
// layouts: id -> its efficiency, empty = one efficiency for all
public record YourNumbers(Map<NumberId, Num> numbers, String spawnGem, PerState fiesta, Map<String, LayoutNum> layouts) {
	public enum Source {
		TRACKED,
		// typed
		SET,
		// calculator.txt, nothing tracked yet
		START,
		CALCULATED
	}

	// tracked kept when typed over. basis = shafts, spawns or corpses
	public record Num(double value, Source source, Double tracked, int basis, String basisText) {
	}

	// efficiency on the layouts own gem ticks, like the hud shows in it
	// weight = share of that gems shafts. seen of gemShafts, every shaft u have
	// route = own blocks till the route is done, 0 = no limit
	public record LayoutNum(Num efficiency, Num route, double weight, int seen, int gemShafts) {
		public LayoutNum(Num efficiency, double weight, int seen, int gemShafts) {
			this(efficiency, new Num(0, Source.CALCULATED, null, 0, null), weight, seen, gemShafts);
		}
	}

	// from = scaled from (calculated). pool = pre-fiesta shafts (tracked)
	public record StateNum(Num num, Fiesta.State from, boolean pool) {
	}

	// now null = fiesta unknown, all shafts together
	public record PerState(Fiesta.State now, Map<NumberId, Map<Fiesta.State, StateNum>> states) {
		public static final PerState NONE = new PerState(null, Map.of());

		// null = not per state
		public Map<Fiesta.State, StateNum> of(NumberId id) {
			return states.get(id);
		}

		// in use + scaled from another of the players states
		boolean scaled(NumberId id) {
			Map<Fiesta.State, StateNum> byState = states.get(id);
			return now != null && byState != null && byState.get(now) != null && byState.get(now).from() != null;
		}
	}

	public YourNumbers {
		numbers = Map.copyOf(new EnumMap<>(numbers));
		if (fiesta == null) fiesta = PerState.NONE;
		layouts = layouts == null ? Map.of() : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(layouts));
	}

	public YourNumbers(Map<NumberId, Num> numbers, String spawnGem) {
		this(numbers, spawnGem, PerState.NONE, Map.of());
	}

	public YourNumbers(Map<NumberId, Num> numbers, String spawnGem, PerState fiesta) {
		this(numbers, spawnGem, fiesta, Map.of());
	}

	public YourNumbers withLayouts(Map<String, LayoutNum> value) {
		return new YourNumbers(numbers, spawnGem, fiesta, value);
	}

	public Num num(NumberId id) {
		return numbers.get(id);
	}

	public double get(NumberId id) {
		return numbers.get(id).value();
	}

	// own pace, so a verdict means something (calculator-design.md)
	// any gems ticks, rest scale from them like on the guide tab
	public boolean ownPace() {
		return own(NumberId.EFFICIENCY) && (own(NumberId.GEMS_PER_BLOCK) || fiesta.scaled(NumberId.GEMS_PER_BLOCK))
				&& (own(NumberId.TICKS_4000) || own(NumberId.TICKS_3500) || own(NumberId.TICKS_3000) || own(NumberId.TICKS_2500));
	}

	private boolean own(NumberId id) {
		Source source = num(id).source();
		return source == Source.TRACKED || source == Source.SET;
	}

	public static NumberId ticksOf(int strength) {
		return switch (strength) {
			case 4000 -> NumberId.TICKS_4000;
			case 3500 -> NumberId.TICKS_3500;
			case 3000 -> NumberId.TICKS_3000;
			case 2500 -> NumberId.TICKS_2500;
			default -> throw new IllegalArgumentException("No ticks number for strength " + strength);
		};
	}
}
