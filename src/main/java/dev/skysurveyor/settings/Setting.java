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

public sealed interface Setting permits Setting.Choice, Setting.Number, Setting.Toggle, Setting.Action {
	String name();

	String description();

	// one line under the name: what the pick does now
	String line(Settings settings);

	boolean isDefault(Settings settings);

	Settings reset(Settings settings);

	String defaultText();

	// null = always used
	Unused unusedWhen();

	// null = used. dimmed, still editable
	default String unused(Settings settings) {
		Unused rule = unusedWhen();
		return rule != null && rule.why() != null && rule.when().test(settings) ? rule.why() : null;
	}

	// folds away under its parent
	default boolean folded(Settings settings) {
		Unused rule = unusedWhen();
		return rule != null && rule.fold() && rule.when().test(settings);
	}

	default boolean isChild() {
		return unusedWhen() != null && unusedWhen().fold();
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

	// fold = sits under its parent, hidden while when. why null = fold only, never dimmed
	record Unused(Predicate<Settings> when, String why, boolean fold) {
		public Unused(Predicate<Settings> when, String why) {
			this(when, why, false);
		}
	}

	// rgb, same row as the toggle
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

		public String line(Settings settings) {
			T value = get.apply(settings);
			return options.stream().filter(o -> o.value().equals(value)).map(Option::tip).findFirst().orElse("");
		}

		public List<String> searchText() {
			List<String> text = new ArrayList<>(List.of(name, description));
			options.forEach(o -> text.add(o.label()));
			return text;
		}
	}

	record Number(String name, String description, String line, String unit, double min, double max, ToDoubleFunction<Settings> get,
			BiFunction<Settings, Double, Settings> set, Unused unusedWhen) implements Setting {
		public Number(String name, String description, String line, String unit, double min, double max, ToDoubleFunction<Settings> get,
				BiFunction<Settings, Double, Settings> set) {
			this(name, description, line, unit, min, max, get, set, null);
		}

		public String line(Settings settings) {
			return line;
		}

		public List<String> searchText() {
			return List.of(name, description, line);
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
			// plain digits only, parseDouble takes 1d, 1e1, 0x1p1 lol
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
	record Toggle(String name, String description, String on, String off, Predicate<Settings> get,
			BiFunction<Settings, Boolean, Settings> set, Unused unusedWhen, Swatch swatch) implements Setting {
		public Toggle(String name, String description, String on, String off, Predicate<Settings> get,
				BiFunction<Settings, Boolean, Settings> set) {
			this(name, description, on, off, get, set, null, null);
		}

		public Toggle(String name, String description, String on, String off, Predicate<Settings> get,
				BiFunction<Settings, Boolean, Settings> set, Unused unusedWhen) {
			this(name, description, on, off, get, set, unusedWhen, null);
		}

		public String line(Settings settings) {
			return get.test(settings) ? on : off;
		}

		public List<String> searchText() {
			return List.of(name, description, on, off);
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

	// a button, not a saved value. id picks what it does
	record Action(String id, String name, String description, String line, String button) implements Setting {
		public String line(Settings settings) {
			return line;
		}

		public boolean isDefault(Settings settings) {
			return true;
		}

		public Settings reset(Settings settings) {
			return settings;
		}

		public String defaultText() {
			return "";
		}

		public Unused unusedWhen() {
			return null;
		}

		public List<String> searchText() {
			return List.of(name, description, line, button);
		}
	}
}
