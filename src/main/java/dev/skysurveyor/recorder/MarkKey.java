package dev.skysurveyor.recorder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// the mark key is only in controls for players who record or have it bound (review 11-27)
// javap 26.2 + fabric key mapping 2.0.5: registering throws once Options exists, so its decided at init, a change needs a restart.
// Options.save writes only registered keys, an unregistered one loses its line. so a bound key always registers
public final class MarkKey {
	// options.txt saves by this name, never rename
	public static final String NAME = "key.skysurveyor.mark";
	private static final String OPTION = "key_" + NAME + ":";
	// what an unbound key saves as
	private static final String UNBOUND = "key.keyboard.unknown";

	private MarkKey() {
	}

	// init thread, 2 small files, before Options reads the same one. any doubt = show it
	public static boolean inControls(Path gameDir, Path dataFolder) {
		try {
			Path options = gameDir.resolve("options.txt");
			if (Files.isRegularFile(options) && bound(Files.readAllLines(options, StandardCharsets.UTF_8))) return true;
			Path config = dataFolder.resolve("config.json");
			return Files.isRegularFile(config) && records(Files.readString(config, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			return true;
		}
	}

	static boolean bound(List<String> options) {
		for (String line : options) {
			if (line.startsWith(OPTION)) return !line.substring(OPTION.length()).trim().equals(UNBOUND);
		}
		return false;
	}

	// dev.autoRecord, missing = off. broken json throws = show it
	static boolean records(String config) {
		JsonElement json = JsonParser.parseString(config);
		if (!json.isJsonObject()) return false;
		JsonElement dev = json.getAsJsonObject().get("dev");
		if (dev == null || !dev.isJsonObject()) return false;
		JsonObject section = dev.getAsJsonObject();
		JsonElement auto = section.get("autoRecord");
		return auto != null && auto.isJsonPrimitive() && !auto.getAsString().equals("off");
	}
}
