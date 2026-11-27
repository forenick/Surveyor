package dev.skysurveyor.journal;

import java.util.function.BooleanSupplier;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PickaxeAbility;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// mining = block hit or pickaxe ability, only defined here
// one copy per user, LiveJournal feeds them in turn
public final class MiningClock {
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

	// true = counts in scope, lastAt moved
	public boolean onBlockHit(long now) {
		return mined(now);
	}

	// true = counts in scope. entered/left flip inShaft
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

	// tab list lags a world change, can be true in a shaft briefly
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
