package dev.skysurveyor.recorder;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

// .jsonl (before 0.26.0-alpha.3) + .jsonl.gz. plain java, tests + the replay tool use it too
public final class RecordingFiles {
	private RecordingFiles() {
	}

	public static boolean isRecording(Path file) {
		String name = file.getFileName().toString();
		return name.endsWith(".jsonl") || name.endsWith(".jsonl.gz");
	}

	public static BufferedReader open(Path file) throws IOException {
		InputStream in = Files.newInputStream(file);
		if (file.getFileName().toString().endsWith(".gz")) in = new CutOffEnd(new GZIPInputStream(in, 1 << 16));
		return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	// every line up to a cut off end (crash mid write), a half line is dropped
	public static List<String> lines(Path file) throws IOException {
		List<String> lines = new ArrayList<>();
		try (BufferedReader reader = open(file)) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (!line.isBlank()) lines.add(line);
			}
		}
		if (!lines.isEmpty() && !lines.getLast().endsWith("}")) lines.removeLast();
		return lines;
	}

	// gzip never closed (crash) = normal end at the last flush, not an error
	private static final class CutOffEnd extends FilterInputStream {
		CutOffEnd(InputStream in) {
			super(in);
		}

		@Override
		public int read() throws IOException {
			try {
				return super.read();
			} catch (EOFException e) {
				return -1;
			}
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			try {
				return super.read(b, off, len);
			} catch (EOFException e) {
				return -1;
			}
		}
	}

	// null = unreadable
	public static JsonObject firstLine(Path file) {
		try (BufferedReader reader = open(file)) {
			String line = reader.readLine();
			return line == null ? null : JsonParser.parseString(line).getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	// auto recordings older than days, by file time. manual ones never
	public static List<Path> expired(Path folder, int days, long now) throws IOException {
		if (days < 0 || !Files.isDirectory(folder)) return List.of();
		long cutoff = now - days * 86_400_000L;
		List<Path> old = new ArrayList<>();
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.filter(RecordingFiles::isRecording).sorted().toList()) {
				if (Files.getLastModifiedTime(file).toMillis() >= cutoff) continue;
				JsonObject start = firstLine(file);
				if (start != null && start.has("auto") && start.get("auto").isJsonPrimitive() && start.get("auto").getAsBoolean()) {
					old.add(file);
				}
			}
		}
		return old;
	}
}
