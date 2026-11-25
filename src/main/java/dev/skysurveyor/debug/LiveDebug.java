package dev.skysurveyor.debug;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.Minecraft;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.recorder.ShaftRecorder;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.Settings;
import dev.skysurveyor.util.DataFolder;
import dev.skysurveyor.util.Guard;

// copy debug info. clipboard only on a click or the command
public final class LiveDebug {
	private LiveDebug() {
	}

	// file reads on the io thread, clipboard back on the client thread
	public static void copyDebugInfo(Minecraft client, Runnable copied) {
		Map<String, String> versions = new LinkedHashMap<>();
		versions.put("Surveyor", version(Surveyor.MOD_ID));
		versions.put("Minecraft", version("minecraft"));
		versions.put("Fabric Loader", version("fabricloader"));
		versions.put("Fabric API", version("fabric-api"));
		versions.put("owo-lib", version("owo"));
		versions.put("Java", System.getProperty("java.version"));
		versions.put("OS", System.getProperty("os.name") + " " + System.getProperty("os.version") + " " + System.getProperty("os.arch"));
		List<String> mods = mods();
		List<String> changed = DebugInfo.changedSettings(LiveSettings.toJson(Surveyor.SETTINGS.get()), LiveSettings.toJson(Settings.DEFAULTS));
		List<Guard.Failure> errors = Guard.failures();
		Map<String, Integer> skipped = Surveyor.JOURNAL.skippedLines();
		String recording = recording(Surveyor.RECORDER);
		Path folder = DataFolder.in(client.gameDirectory.toPath());
		Surveyor.MONEY.run(() -> {
			String text = new DebugInfo(versions, mods, changed, errors, files(folder), skipped, recording).text();
			client.execute(() -> Guard.run("copy debug info", () -> {
				client.keyboardHandler.setClipboard(text);
				copied.run();
			}));
		});
	}

	static String recording(ShaftRecorder recorder) {
		if (!recorder.isRecording()) return "off";
		return (recorder.autoSession() == null ? "manual, " : "auto, ") + DebugInfo.size(recorder.writtenBytes()) + " written";
	}

	private static String version(String id) {
		return FabricLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("none");
	}

	// top level mods only, fabric apis own modules + jars inside jars left out
	private static List<String> mods() {
		List<String> mods = new ArrayList<>();
		for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
			String id = mod.getMetadata().getId();
			if (mod.getContainingMod().isPresent() || id.equals(Surveyor.MOD_ID) || id.equals("minecraft") || id.equals("java")
					|| id.equals("fabricloader") || id.startsWith("fabric-") && !id.equals("fabric-api")) {
				continue;
			}
			mods.add(id + " " + mod.getMetadata().getVersion().getFriendlyString());
		}
		mods.sort(String::compareTo);
		return mods;
	}

	// the data folders own files, not recordings or caches
	private static List<String> files(Path folder) {
		List<String> files = new ArrayList<>();
		if (!Files.isDirectory(folder)) return files;
		try (Stream<Path> list = Files.list(folder)) {
			for (Path file : list.filter(Files::isRegularFile).sorted().toList()) {
				String name = file.getFileName().toString();
				if (name.equals("prices.json") || name.equals("items.json")) continue;
				long lines = name.endsWith(".jsonl") ? countLines(file) : -1;
				files.add(DebugInfo.file(name, Files.size(file), lines));
			}
		} catch (IOException e) {
			files.add("could not list: " + e);
		}
		return files;
	}

	private static long countLines(Path file) throws IOException {
		try (Stream<String> lines = Files.lines(file)) {
			return lines.filter(line -> !line.isBlank()).count();
		} catch (UncheckedIOException e) {
			return -1;
		}
	}
}
