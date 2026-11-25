package dev.skysurveyor.settings;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import dev.skysurveyor.util.Format;

public sealed interface Setting permits Setting.Choice, Setting.Number, Setting.Toggle {
	String name();

	String description();

	boolean isDefault(Settings settings);

	Settings reset(Settings settings);

	String defaultText();

	// null = always used
	Unused unusedWhen();

	// null = used. page dims the card but keeps it editable
	default String unused(Settings settings) {
		Unused rule = unusedWhen();
		return rule != null && rule.when().test(settings) ? rule.why() : null;
	}

	default List<String> searchText() {
		return List.of(name(), description());
	}

	// case + spaces dont matter, empty matches all
	default boolean matches(String query) {
		String q = query.strip().toLowerCase(Locale.ROOT);
		if (q.isEmpty()) return true;
		for (String text : searchText()) {
			if (text.toLowerCase(Locale.ROOT).contains(q)) return true;
		}
		return false;
	}

	record Option<T>(T value, String label, String tip) {
	}

	record Unused(Predicate<Settings> when, String why) {
	}

	// colour next to a toggle, same row. rgb
	record Swatch(String name, ToIntFunction<Settings> get, BiFunction<Settings, Integer, Settings> set) {
	}

	record Choice<T>(String name, String description, List<Option<T>> options, Function<Settings, T> get,
			BiFunction<Settings, T, Settings> set, Unused unusedWhen) implements Setting {
		public Choice(String name, String description, List<Option<T>> options, Function<Settings, T> get,
				BiFunction<Settings, T, Settings> set) {
			this(name, description, options, get, set, null);
		}

		public boolean isDefault(Settings settings) {
			return get.apply(settings).equals(get.apply(Settings.DEFAULTS));
		}

		public Settings reset(Settings settings) {
			return set.apply(settings, get.apply(Settings.DEFAULTS));
		}

		public boolean isDefault(Option<T> option) {
			return option.value().equals(get.apply(Settings.DEFAULTS));
		}

		public String defaultText() {
			T value = get.apply(Settings.DEFAULTS);
			return options.stream().filter(o -> o.value().equals(value)).map(Option::label).findFirst().orElse(String.valueOf(value));
		}

		public List<String> searchText() {
			List<String> text = new ArrayList<>(List.of(name, description));
			options.forEach(o -> text.add(o.label()));
			return text;
		}
	}

	record Number(String name, String description, String unit, double min, double max, ToDoubleFunction<Settings> get,
			BiFunction<Settings, Double, Settings> set, Unused unusedWhen) implements Setting {
		public Number(String name, String description, String unit, double min, double max, ToDoubleFunction<Settings> get,
				BiFunction<Settings, Double, Settings> set) {
			this(name, description, unit, min, max, get, set, null);
		}

		public boolean isDefault(Settings settings) {
			return get.applyAsDouble(settings) == get.applyAsDouble(Settings.DEFAULTS);
		}

		public Settings reset(Settings settings) {
			return set.apply(settings, get.applyAsDouble(Settings.DEFAULTS));
		}

		public String defaultText() {
			return format(get.applyAsDouble(Settings.DEFAULTS)) + unit;
		}

		// null = not a number in range. comma ok for eu keyboards
		public Double parse(String text) {
			String t = text.strip().replace(',', '.');
			// plain digits only, parseDouble takes 1d, 1e1, 0x1p1
			if (!t.matches("\\d+(\\.\\d*)?|\\.\\d+")) return null;
			try {
				double value = Double.parseDouble(t);
				return Double.isFinite(value) && value >= min && value <= max ? value : null;
			} catch (NumberFormatException e) {
				return null;
			}
		}

		public static String format(double value) {
			return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
		}
	}

	// swatch null = no colour
	record Toggle(String name, String description, Predicate<Settings> get, BiFunction<Settings, Boolean, Settings> set,
			Unused unusedWhen, Swatch swatch) implements Setting {
		public Toggle(String name, String description, Predicate<Settings> get, BiFunction<Settings, Boolean, Settings> set) {
			this(name, description, get, set, null, null);
		}

		public Toggle(String name, String description, Predicate<Settings> get, BiFunction<Settings, Boolean, Settings> set,
				Unused unusedWhen) {
			this(name, description, get, set, unusedWhen, null);
		}

		public boolean isDefault(Settings settings) {
			return get.test(settings) == get.test(Settings.DEFAULTS)
					&& (swatch == null || swatch.get().applyAsInt(settings) == swatch.get().applyAsInt(Settings.DEFAULTS));
		}

		public Settings reset(Settings settings) {
			Settings reset = set.apply(settings, get.test(Settings.DEFAULTS));
			return swatch == null ? reset : swatch.set().apply(reset, swatch.get().applyAsInt(Settings.DEFAULTS));
		}

		public String defaultText() {
			String onOff = get.test(Settings.DEFAULTS) ? "On" : "Off";
			return swatch == null ? onOff : onOff + ", " + Format.hex(swatch.get().applyAsInt(Settings.DEFAULTS));
		}
	}
}
