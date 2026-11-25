package dev.skysurveyor.recorder;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RecordingFilesTest {
	private static final long DAY = 86_400_000L;

	@Test
	void readsPlainAndGzip(@TempDir Path tmp) throws Exception {
		Path plain = tmp.resolve("a.jsonl");
		Files.writeString(plain, "{\"t\":0}\n\n{\"t\":1}\n");
		assertEquals(List.of("{\"t\":0}", "{\"t\":1}"), RecordingFiles.lines(plain));
		Path gz = gzip(tmp.resolve("b.jsonl.gz"), "{\"t\":0}\n{\"t\":1}\n", true);
		assertEquals(List.of("{\"t\":0}", "{\"t\":1}"), RecordingFiles.lines(gz));
	}

	@Test
	void cutOffGzipKeepsFlushedLines(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("c.jsonl.gz");
		// crash: sync flushed, never closed
		try (OutputStream out = Files.newOutputStream(file)) {
			GZIPOutputStream gz = new GZIPOutputStream(out, true);
			gz.write("{\"t\":0}\n{\"t\":1}\n".getBytes(StandardCharsets.UTF_8));
			gz.flush();
		}
		assertEquals(List.of("{\"t\":0}", "{\"t\":1}"), RecordingFiles.lines(file));
	}

	@Test
	void expiredOnlyOldAutoOnes(@TempDir Path tmp) throws Exception {
		long now = 100 * DAY;
		Path oldAuto = gzip(tmp.resolve("1.jsonl.gz"), "{\"type\":\"start\",\"auto\":true}\n", true);
		Path oldManual = gzip(tmp.resolve("2.jsonl.gz"), "{\"type\":\"start\"}\n", true);
		Path newAuto = gzip(tmp.resolve("3.jsonl.gz"), "{\"type\":\"start\",\"auto\":true}\n", true);
		Path oldPlain = tmp.resolve("4.jsonl");
		Files.writeString(oldPlain, "{\"type\":\"start\",\"live\":true}\n");
		Path other = tmp.resolve("5.txt");
		Files.writeString(other, "{\"auto\":true}\n");
		for (Path p : List.of(oldAuto, oldManual, oldPlain, other)) Files.setLastModifiedTime(p, FileTime.fromMillis(now - 40 * DAY));
		Files.setLastModifiedTime(newAuto, FileTime.fromMillis(now - 2 * DAY));

		assertEquals(List.of(oldAuto), RecordingFiles.expired(tmp, 30, now));
		assertEquals(List.of(), RecordingFiles.expired(tmp, -1, now), "forever");
		assertEquals(List.of(), RecordingFiles.expired(tmp.resolve("missing"), 30, now));
	}

	@Test
	void unreadableFirstLine(@TempDir Path tmp) throws Exception {
		Path file = tmp.resolve("x.jsonl.gz");
		Files.writeString(file, "not gzip");
		assertNull(RecordingFiles.firstLine(file));
	}

	private static Path gzip(Path file, String text, boolean close) throws Exception {
		GZIPOutputStream gz = new GZIPOutputStream(Files.newOutputStream(file), true);
		gz.write(text.getBytes(StandardCharsets.UTF_8));
		if (close) gz.close();
		return file;
	}
}
