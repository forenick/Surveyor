package dev.skysurveyor.recorder;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import dev.skysurveyor.util.DataFolder;

// all file io on its own thread, the client thread only queues lines
final class RecordingWriter implements AutoCloseable {
	private static final Gson GSON = new Gson();
	// a crash loses at most this much
	private static final long FLUSH_MILLIS = 1000;

	private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
		Thread thread = new Thread(runnable, "Surveyor Recorder");
		thread.setDaemon(true);
		return thread;
	});
	// the name asked for till the file is open, then the real one
	private volatile Path file;
	// recorder thread only, null = not open (yet or failed)
	private BufferedWriter writer;
	private volatile String failure;
	private volatile long written;

	// no io here. name taken = _2, _3..
	RecordingWriter(Path gameDir, String name, String extension) {
		this.file = gameDir.resolve(DataFolder.NAME).resolve("recordings").resolve(name + extension);
		executor.execute(() -> open(gameDir, name, extension));
		executor.scheduleWithFixedDelay(this::flush, FLUSH_MILLIS, FLUSH_MILLIS, TimeUnit.MILLISECONDS);
	}

	private void open(Path gameDir, String name, String extension) {
		try {
			Path folder = DataFolder.in(gameDir).resolve("recordings");
			Files.createDirectories(folder);
			Path target = folder.resolve(name + extension);
			for (int i = 2;; i++) {
				OutputStream out;
				try {
					out = Files.newOutputStream(target, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
				} catch (FileAlreadyExistsException taken) {
					target = folder.resolve(name + "_" + i + extension);
					continue;
				}
				try {
					// sync flush, a cut off file still opens up to the last flush
					writer = new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(out, 1 << 16, true), StandardCharsets.UTF_8));
				} catch (IOException e) {
					out.close();
					throw e;
				}
				file = target;
				return;
			}
		} catch (IOException | RuntimeException e) {
			ShaftRecorder.LOGGER.error("Could not open a recording file", e);
			failure = e.getMessage() == null ? e.toString() : e.getMessage();
		}
	}

	Path file() {
		return file;
	}

	// null = fine so far
	String failure() {
		return failure;
	}

	// uncompressed, recorder thread writes it
	long written() {
		return written;
	}

	void write(JsonObject event) {
		write(GSON.toJson(event));
	}

	void write(String line) {
		executor.execute(() -> {
			if (writer == null) return;
			try {
				writer.write(line);
				writer.newLine();
				written += line.getBytes(StandardCharsets.UTF_8).length + 1;
			} catch (IOException e) {
				ShaftRecorder.LOGGER.error("Failed to write recording line", e);
			}
		});
	}

	private void flush() {
		if (writer == null) return;
		try {
			writer.flush();
		} catch (IOException e) {
			ShaftRecorder.LOGGER.error("Failed to flush recording", e);
		}
	}

	// returns right away, the recorder thread finishes the file
	@Override
	public void close() {
		executor.execute(() -> {
			if (writer == null) return;
			try {
				writer.close();
			} catch (IOException e) {
				ShaftRecorder.LOGGER.error("Failed to close recording", e);
			}
		});
		executor.shutdown();
	}

	// game closing only, its a daemon thread so the end of the gzip could be cut
	void awaitClosed(long millis) {
		try {
			executor.awaitTermination(millis, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
