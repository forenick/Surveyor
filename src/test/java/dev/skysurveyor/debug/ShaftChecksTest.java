package dev.skysurveyor.debug;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Corpse;
import dev.skysurveyor.detection.CorpseLoot;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShaftChecksTest {
	private static List<String> check(List<Corpse> corpses, List<CorpseLoot> loots) {
		List<String> failed = new ArrayList<>();
		ShaftChecks.corpseLoots(corpses, loots, failed);
		return failed;
	}

	private static CorpseLoot loot(String type) {
		return new CorpseLoot(type, false, List.of(), 0);
	}

	@Test
	void sameCountsAndLeavingRightAfterALootPass() {
		assertEquals(List.of(), check(List.of(new Corpse("LAPIS", true), new Corpse("UMBER", false)), List.of(loot("LAPIS"))));
		assertEquals(List.of(), check(List.of(new Corpse("LAPIS", false)), List.of(loot("LAPIS"))), "tab not flipped yet");
	}

	@Test
	void missedLootMessageFails() {
		assertEquals(List.of("corpse loots: UMBER tab list 2 looted, chat 1"),
				check(List.of(new Corpse("UMBER", true), new Corpse("UMBER", true)), List.of(loot("UMBER"))));
		assertEquals(List.of("corpse loots: LAPIS tab list 0 looted, chat 2"), check(List.of(), List.of(loot("LAPIS"), loot("LAPIS"))));
	}
}
