package dev.skysurveyor.journal;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// appended, crash loses only the last line
public class JsonLinesFile<T> {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	private final Path file;
	private final Class<T> type;
	private final Predicate<T> complete;
	private final Gson gson = new GsonBuilder().disableHtmlEscaping().create();
	// one thread for every file, owner shuts it down
	private final Executor io;
	private volatile boolean failed;
	private volatile int skipped;

	public JsonLinesFile(Path file, Class<T> type, Predicate<T> complete, Executor io) {
		this.file = file;
		this.type = type;
		this.complete = complete;
		this.io = io;
	}

	public Path path() {
		return file;
	}

	// read threw, lines may be missing
	public boolean failed() {
		return failed;
	}

	// they stay in the file
	public int skipped() {
		return skipped;
	}

	// never fails, else empty journal w/o a word. catches throwable on purpose, trust me
	public CompletableFuture<List<T>> load() {
		return CompletableFuture.supplyAsync(() -> {
			List<T> entries = new ArrayList<>();
			try {
				read(entries);
			} catch (Throwable e) {
				failed = true;
				LOGGER.error("Could not read all of {}, keeping {} entries read before it", file, entries.size(), e);
			}
			return entries;
		}, io);
	}

	public void append(T entry) {
		String line = gson.toJson(entry) + "\n";
		io.execute(() -> {
			try {
				Files.createDirectories(file.getParent());
				// cut off write, dont glue onto it
				String text = endsMidLine() ? "\n" + line : line;
				Files.writeString(file, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			} catch (IOException e) {
				LOGGER.error("Could not write to {}", file, e);
			}
		});
	}

	List<T> read() throws IOException {
		List<T> entries = new ArrayList<>();
		read(entries);
		return entries;
	}

	private void read(List<T> entries) throws IOException {
		if (!Files.exists(file)) return;
		// replaces bad utf-8, newBufferedReader would throw on one bad char
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
			String line;
			int number = 0;
			while ((line = reader.readLine()) != null) {
				number++;
				if (line.isBlank()) continue;
				try {
					T entry = gson.fromJson(line, type);
					// "null" parses to null
					if (entry == null) {
						skipped++;
						LOGGER.warn("Skipping empty line {} of {}", number, file);
					} else if (!complete.test(entry)) {
						// f.e. {} from a hand edit, line stays
						skipped++;
						LOGGER.warn("Skipping line {} of {}: fields missing", number, file);
					} else {
						entries.add(entry);
					}
				} catch (RuntimeException e) {
					// f.e. price typed as text, line stays
					skipped++;
					LOGGER.warn("Skipping unreadable line {} of {}: {}", number, file, e.toString());
				}
			}
		}
	}

	// cant tell = true, blank lines skipped on read
	private boolean endsMidLine() {
		if (!Files.exists(file)) return false;
		try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
			long size = channel.size();
			if (size == 0) return false;
			ByteBuffer last = ByteBuffer.allocate(1);
			channel.position(size - 1);
			return channel.read(last) != 1 || last.get(0) != '\n';
		} catch (IOException e) {
			return true;
		}
	}
}
