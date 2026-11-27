package dev.skysurveyor.settings;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.util.SafeWrite;

// memory any thread, file io thread only. hand edits by mtime
final class ConfigFile {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final AtomicReference<Settings> memory = new AtomicReference<>(Settings.DEFAULTS);
	private volatile String saveError;
	private long loadedMtime = Long.MIN_VALUE;

	ConfigFile(Path folder) {
		this.file = folder.resolve("config.json");
	}

	Settings get() {
		return memory.get();
	}

	// any thread. one section only, else another threads set is lost
	<T> void set(ConfigSection<T> section, T value) {
		memory.updateAndGet(settings -> section.in(settings, value));
	}

	// null = last save worked
	String saveError() {
		return saveError;
	}

	// changed keys only, unknown + unloaded hand edits stay
	// never sets memory, older queued save would undo a newer
	<T> void save(ConfigSection<T> section, T before, T after) {
		String key = section.key();
		if (section.whole()) {
			boolean defaults = after.equals(section.of(Settings.DEFAULTS));
			save(json -> {
				if (defaults) json.remove(key);
				else json.add(key, GSON.toJsonTree(after));
			});
			return;
		}
		JsonObject old = GSON.toJsonTree(before).getAsJsonObject();
		save(json -> {
			JsonObject part = json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
			GSON.toJsonTree(after).getAsJsonObject().entrySet().forEach(e -> {
				if (!Objects.equals(old.get(e.getKey()), e.getValue())) part.add(e.getKey(), e.getValue());
			});
			json.add(key, part);
		});
	}

	// json comments + layout lost
	private void save(Consumer<JsonObject> edit) {
		try {
			JsonObject json = Files.exists(file)
					? JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject()
					// memory, keeps page changes from before 1st save
					: json(memory.get());
			edit.accept(json);
			// loadedMtime stays, next reload sees older edits
			SafeWrite.write(file, GSON.toJson(json));
			saveError = null;
		} catch (IOException | RuntimeException e) {
			// broken hand edit, never overwrite it
			LOGGER.warn("Could not save {}: {}", file, e.toString());
			saveError = e.toString();
		}
	}

	static JsonObject json(Settings settings) {
		JsonObject json = new JsonObject();
		for (ConfigSection<?> section : ConfigSection.ALL) {
			if (section.whole() && section.isDefault(settings)) continue;
			json.add(section.key(), GSON.toJsonTree(section.of(settings)));
		}
		return json;
	}

	void reloadIfChanged() {
		try {
			// deleted = start over
			if (!Files.exists(file)) {
				memory.set(Settings.DEFAULTS);
				SafeWrite.write(file, GSON.toJson(json(memory.get())));
			}
			long mtime = Files.getLastModifiedTime(file).toMillis();
			if (mtime == loadedMtime) return;
			loadedMtime = mtime;
			JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
			// broken section keeps last values
			for (ConfigSection<?> section : ConfigSection.ALL) load(section, json);
			// fixed by hand, next save should work
			saveError = null;
		} catch (IOException | RuntimeException e) {
			// broken hand edit, keep last good + never overwrite
			LOGGER.warn("Could not read {}: {}", file, e.toString());
		}
	}

	private <T> void load(ConfigSection<T> section, JsonObject json) {
		try {
			T loaded = json.has(section.key()) ? section.read().apply(json.get(section.key())) : null;
			set(section, loaded == null ? section.of(Settings.DEFAULTS) : loaded);
		} catch (RuntimeException e) {
			LOGGER.warn("Could not read \"{}\" in {}: {}", section.key(), file, e.toString());
		}
	}
}
