package dev.skysurveyor.detection;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.detection.Shaft.ExitReason;
import dev.skysurveyor.detection.ShaftEvent.ExitBonus;
import dev.skysurveyor.detection.ShaftEvent.WarpFailed;
import dev.skysurveyor.detection.ShaftEvent.Warping;

import static org.junit.jupiter.api.Assertions.assertEquals;

// rec 5 at 924849 + 982992, both outside a shaft
class WarpFailedTest {
	private final ChatParser parser = new ChatParser(TextPatterns.defaults());

	@Test
	void bothReasonsParse() {
		assertEquals(new WarpFailed(), parser.parse("§cCouldn't warp you! Try again later. §7(DYNAMIC_POOL_ERROR)", List.of()));
		assertEquals(new WarpFailed(), parser.parse("§cCouldn't warp you! Try again later. §7(PLAYER_TRANSFER_COOLDOWN)", List.of()));
	}

	@Test
	void failedWarpThenDisconnectIsUnknown() {
		Shaft shaft = new Shaft(0);
		shaft.apply(new Warping(), 1_000);
		shaft.apply(new WarpFailed(), 1_003);
		shaft.leave(60_000);
		assertEquals(ExitReason.UNKNOWN, shaft.exitReason());
	}

	@Test
	void retriedWarpIsAWarp() {
		Shaft shaft = new Shaft(0);
		shaft.apply(new Warping(), 1_000);
		shaft.apply(new WarpFailed(), 1_003);
		shaft.apply(new Warping(), 5_000);
		shaft.leave(9_000);
		assertEquals(ExitReason.WARP, shaft.exitReason());
	}

	@Test
	void failedWarpKeepsABye() {
		Shaft shaft = new Shaft(0);
		shaft.apply(new ExitBonus(1_000), 1_000);
		shaft.apply(new WarpFailed(), 1_003);
		shaft.leave(9_000);
		assertEquals(ExitReason.BYE, shaft.exitReason());
	}
}
