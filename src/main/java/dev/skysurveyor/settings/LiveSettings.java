package dev.skysurveyor.settings;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Function;

import com.google.gson.JsonObject;

// get() any thread, file io thread only
public final class LiveSettings {
	private final List<BiConsumer<Settings, Settings>> listeners = new CopyOnWriteArrayList<>();
	private volatile ConfigFile config;
	private Executor io;

	// one thread, saves + reloads stay in order
	public void start(Path folder, Executor io) {
		this.io = io;
		config = new ConfigFile(folder);
	}

	public Settings get() {
		return config == null ? Settings.DEFAULTS : config.get();
	}

	// memory now, changed sections saved on io thread
	public Settings change(Function<Settings, Settings> change) {
		Settings before = get();
		Settings after = change.apply(before);
		if (config == null || after.equals(before)) return after;
		for (ConfigSection<?> section : ConfigSection.ALL) apply(section, before, after);
		for (BiConsumer<Settings, Settings> listener : listeners) listener.accept(before, after);
		return after;
	}

	private <T> void apply(ConfigSection<T> section, Settings before, Settings after) {
		T old = section.of(before);
		T value = section.of(after);
		if (value.equals(old)) return;
		config.set(section, value);
		io.execute(() -> config.save(section, old, value));
	}

	// on change()s thread. not for hand edits
	public void onChange(BiConsumer<Settings, Settings> listener) {
		listeners.add(listener);
	}

	public static JsonObject toJson(Settings settings) {
		return ConfigFile.json(settings);
	}

	// null = saved fine
	public String saveError() {
		return config == null ? null : config.saveError();
	}

	// io thread
	public void reloadIfChanged() {
		if (config != null) config.reloadIfChanged();
	}
}
