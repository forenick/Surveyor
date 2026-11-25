package dev.skysurveyor.debug;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.TextPatterns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ChatWatchTest {
	private final ChatWatch watch = ChatWatch.defaults(TextPatterns.defaults());

	@Test
	void unmatchedWithWatchWord() {
		assertEquals("A new kind of Corpse appeared!", watch.unmatched("§bA new kind of §eCorpse §bappeared!  "));
	}

	@Test
	void knownLinesAndOtherChatSkipped() {
		// research/05 campfire msg
		assertNull(watch.unmatched("§aYou placed a Portable Campfire!"));
		assertNull(watch.unmatched("§7Welcome to Hypixel SkyBlock!"), "no watch word");
		assertNull(watch.unmatched("§9Party §8> §bsomeone§f: corpse here"), "player chat");
		assertNull(watch.unmatched("   "));
	}

	// made up lines, screen + lore patterns would call them known
	@Test
	void screenAndLorePatternsDontCount() {
		assertEquals("Unlocked Cold Resistance II", watch.unmatched("§aUnlocked §bCold Resistance II"));
		assertEquals("Cold Resistance IV (51:12)", watch.unmatched("§bCold Resistance IV §7(51:12)"));
		assertNull(watch.unmatched("§a§lBUFF! §fYou splashed yourself with §bCold Resistance IV§f!"), "consumable.buff is chat");
	}

	@Test
	void keyIgnoresNumbers() {
		assertEquals(ChatWatch.key("Your Cold went to 12,345!"), ChatWatch.key("Your Cold went to 7!"));
	}
}
