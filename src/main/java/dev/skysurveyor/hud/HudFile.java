package dev.skysurveyor.hud;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.util.SafeWrite;

// unreadable file = backup before any write
public final class HudFile {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	static final String BAD = ".bad";
	static final String OLD = ".old";

	private final Path file;
	// single thread = read before first save, saves stay in order
	private final Executor io;
	private final Supplier<LocalDateTime> clock;
	private final Function<String, HudLayout> parse;
	// client thread
	private boolean loaded;
	// io thread only. null = no backup, BAD unreadable, OLD older format or cut
	private String backupSuffix;

	public HudFile(Path file, Executor io) {
		this(file, io, LocalDateTime::now, HudLayout::fromJson);
	}

	HudFile(Path file, Executor io, Supplier<LocalDateTime> clock, Function<String, HudLayout> parse) {
		this.file = file;
		this.io = io;
		this.clock = clock;
		this.parse = parse;
	}

	public Path path() {
		return file;
	}

	// null = defaults. never completes exceptionally
	public CompletableFuture<HudLayout> load() {
		return CompletableFuture.supplyAsync(this::read, io);
	}

	// client thread, once load() is done
	public void loaded() {
		loaded = true;
	}

	// client thread. false = still loading, defaults would overwrite the file
	public boolean save(String json, BooleanSupplier unchanged) {
		if (!loaded && unchanged.getAsBoolean()) return false;
		Runnable write = () -> {
			try {
				write(json);
			} catch (IOException | RuntimeException e) {
				LOGGER.error("Could not save the HUD to {}", file, e);
			}
		};
		try {
			io.execute(write);
		} catch (RejectedExecutionException e) {
			// game closing w/ editor open, io thread already gone
			write.run();
		}
		return true;
	}

	// io thread. throwable on purpose, hostile file can overflow the stack
	HudLayout read() {
		String json;
		try {
			if (Files.notExists(file)) return null;
			json = Files.readString(file, StandardCharsets.UTF_8);
		} catch (Throwable e) {
			// maybe just locked, keep it, first save backs it up
			backupSuffix = BAD;
			LOGGER.warn("Could not read {}, using the default HUD", file, e);
			return null;
		}
		try {
			HudLayout layout = parse.apply(json);
			// older format, repaired or cut hand edit, keep the original
			if (!layout.toJson().equals(json)) backupSuffix = OLD;
			if (layout.cutParts() > 0) {
				LOGGER.warn("Cut {} too long parts of {}, the first save keeps the original as a backup", layout.cutParts(), file);
			}
			return layout;
		} catch (Throwable e) {
			// else the next save overwrites a hand edit
			try {
				Path backup = Files.move(file, backupPath(BAD));
				LOGGER.warn("Could not read {}, moved it to {} and using the default HUD", file, backup, e);
			} catch (IOException | RuntimeException moveFailed) {
				backupSuffix = BAD;
				LOGGER.warn("Could not read {} or move it away, using the default HUD", file, e);
			}
			return null;
		}
	}

	// io thread. backup fails = no write, try again next save
	void write(String json) throws IOException {
		if (backupSuffix != null && !Files.notExists(file)) {
			Files.copy(file, backupPath(backupSuffix));
			backupSuffix = null;
		}
		SafeWrite.write(file, json);
	}

	// names have 1 s steps, never copy over an older backup
	private Path backupPath(String suffix) {
		String name = file.getFileName() + "." + clock.get().format(BACKUP_TIME);
		Path path = file.resolveSibling(name + suffix);
		for (int n = 2; Files.exists(path); n++) path = file.resolveSibling(name + "-" + n + suffix);
		return path;
	}
}
