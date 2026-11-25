package dev.skysurveyor.debug;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugLinesTest {
	@Test
	void nothingKnownYet() {
		List<String> lines = DebugLines.of(new DebugLines.Values("0.26.0", false, null, null, null, null, null, null, null, 0, 0, null, null,
				null, null, "off"));
		assertEquals(List.of("§eSurveyor 0.26.0", "§7Area: §felsewhere", "§7Session: §fnone", "§7Spawn timer: §foff", "§7Last event: §fnone",
				"§7Unmatched chat: §f0§7  Errors: §f0", "§7Prices: §fBazaar never, BIN never", "§7Ping: §f-§7  TPS: §f-", "§7Recording: §foff"),
				lines);
	}

	@Test
	void inAShaft() {
		List<String> lines = DebugLines.of(new DebugLines.Values("0.26.0", true, "JASP_C", 7L, 3, 5_000L, 65_000L, "ShaftEntered", 2_000L, 2, 1,
				60_000L, 600_000L, 80L, 19.84, "auto, 1.2 MB written"));
		assertEquals("§7Area: §fshaft JASP_C", lines.get(1));
		assertEquals("§7Unmatched chat: §e2§7  Errors: §c1", lines.get(5));
		assertEquals("§7Ping: §f80 ms§7  TPS: §f19.8", lines.get(7));
	}
}
