package dev.skysurveyor.recorder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoundFilterTest {
	@Test
	void blockAndStepNoiseOut() {
		assertFalse(SoundFilter.keep("block.stone.break"));
		assertFalse(SoundFilter.keep("block.glass.hit"));
		assertFalse(SoundFilter.keep("entity.player.step"));
		assertFalse(SoundFilter.keep("ambient.cave"));
		assertFalse(SoundFilter.keep("music.overworld.deep_dark"));
	}

	@Test
	void dingsStay() {
		assertTrue(SoundFilter.keep("block.note_block.pling"));
		assertTrue(SoundFilter.keep("entity.experience_orb.pickup"));
		assertTrue(SoundFilter.keep("block.anvil.land"));
		assertTrue(SoundFilter.keep("entity.player.levelup"));
	}
}
