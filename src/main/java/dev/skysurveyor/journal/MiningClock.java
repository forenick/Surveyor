package dev.skysurveyor.journal;

import java.util.function.BooleanSupplier;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// mining = block hit or pickaxe ability. the one place for that + "in a shaft"
// each user has its own copy, LiveJournal hands events to them one after another
public final class MiningClock {
	// where mining counts for lastAt
	public enum Scope {
		// price check
		SHAFT,
		// outside mining
		MINES_OUTSIDE_SHAFT,
		// journal session, stash, breaks
		MINES_OR_SHAFT
	}

	private final Scope scope;
	private final BooleanSupplier inMiningArea;
	private boolean inShaft;
	private long lastAt = -1;

	public MiningClock(Scope scope, BooleanSupplier inMiningArea) {
		this.scope = scope;
		this.inMiningArea = inMiningArea;
	}

	// true = mining that counts in scope, lastAt moved
	public boolean onBlockHit(long now) {
		return mined(now);
	}

	// true = mining that counts in scope. ShaftEntered/ShaftLeft flip inShaft
	public boolean onEvent(ShaftEvent event, long now) {
		switch (event) {
			case PickaxeAbility ability -> {
				return mined(now);
			}
			case ShaftEntered entered -> inShaft = true;
			case ShaftLeft left -> inShaft = false;
			default -> {
			}
		}
		return false;
	}

	public boolean inShaft() {
		return inShaft;
	}

	// tab list, lags a world change. can be true in a shaft for a moment
	public boolean inMines() {
		return inMiningArea.getAsBoolean();
	}

	public boolean inScope() {
		return switch (scope) {
			case SHAFT -> inShaft;
			case MINES_OUTSIDE_SHAFT -> !inShaft && inMines();
			case MINES_OR_SHAFT -> inShaft || inMines();
		};
	}

	// -1 = never
	public long lastAt() {
		return lastAt;
	}

	public boolean minedSince(long from) {
		return lastAt >= 0 && lastAt >= from;
	}

	public void reset() {
		lastAt = -1;
	}

	private boolean mined(long now) {
		if (!inScope()) return false;
		lastAt = now;
		return true;
	}
}
