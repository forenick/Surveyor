package dev.skysurveyor.mining;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.PortalSpawned;
import dev.skysurveyor.detection.ShaftEvent.ShaftEntered;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;

// client thread only
// loop = 1st block after a shaft till the next, or LOOP_GAP_MILLIS idle
public final class MiningNow {
	// display choice, same gap as a journal break
	public static final int HUD_MIN_BLOCKS = 10;
	public static final long LOOP_GAP_MILLIS = 2 * 60_000L;
	// chart, display choices
	public static final long WINDOW_MILLIS = 60_000;
	public static final long STEP_MILLIS = 10_000;
	public static final int MAX_POINTS = 90;

	private final MiningMeter shaft = new MiningMeter();
	private final MiningMeter loop = new MiningMeter();
	private final BooleanSupplier inMines;
	private boolean inShaft;
	private long enteredAt = -1;
	// -1 = none yet
	private long loopFrom = -1;
	// oldest 1st, 0..1
	private final List<Double> points = new ArrayList<>();
	private long pointsKey = -1;
	private long lastPoint = -1;
	// kept bc the world change clears the loop
	private String portalBlock;
	private String spawnBlock;

	public MiningNow(BooleanSupplier inMines) {
		this.inMines = inMines;
	}

	// before the journal, order matters
	public void onEvent(ShaftEvent event, long now) {
		shaft.onEvent(event, now);
		loop.onEvent(event, now);
		switch (event) {
			case PortalSpawned spawned when spawned.foundByYou() -> {
				MiningStats stats = inShaft ? null : current(now);
				portalBlock = stats == null ? null : stats.block();
			}
			case ShaftEntered entered -> {
				inShaft = true;
				enteredAt = entered.shaft().enteredAt();
				spawnBlock = portalBlock;
				portalBlock = null;
				startLoop();
			}
			case ShaftLeft left -> inShaft = false;
			default -> {
			}
		}
	}

	// after the trackers world change
	public void onWorldChange() {
		shaft.onWorldChange();
		loop.onWorldChange();
		loopFrom = -1;
		inShaft = false;
		enteredAt = -1;
	}

	public void onHit(long now) {
		MiningMeter meter = meter(now);
		if (meter != null) meter.onHit(now);
	}

	public void onBreak(long now, String block, int ticks, boolean clean, int spread) {
		MiningMeter meter = meter(now);
		if (meter != null) meter.onBreak(now, block, ticks, clean, spread);
	}

	// null = hub, garden etc
	private MiningMeter meter(long now) {
		if (inShaft) return shaft;
		if (!inMines.getAsBoolean()) return null;
		long last = loop.lastAt();
		if (last >= 0 && now - last > LOOP_GAP_MILLIS) startLoop();
		if (loopFrom < 0) loopFrom = now;
		return loop;
	}

	private void startLoop() {
		loop.startOver();
		loopFrom = -1;
	}

	public boolean inShaft() {
		return inShaft;
	}

	public boolean boosting() {
		return shaft.boosting() || loop.boosting();
	}

	// main block not last, last was stone in 3/39 spawns
	public String spawnBlock() {
		return spawnBlock;
	}

	public MiningStats shaftStats(long entered, long ended) {
		return shaft.stats(entered, ended);
	}

	// null = -
	public MiningStats current(long now) {
		if (inShaft) return enteredAt < 0 ? null : shaft.stats(enteredAt, now);
		long last = loop.lastAt();
		if (loopFrom < 0 || last < 0 || now - last > LOOP_GAP_MILLIS) return null;
		return loop.stats(loopFrom, now);
	}

	// call often, samples itself
	public List<Double> track(long now) {
		MiningStats stats = current(now);
		long key = inShaft ? enteredAt : loopFrom;
		if (stats == null || key != pointsKey) {
			points.clear();
			pointsKey = stats == null ? -1 : key;
			lastPoint = -1;
		}
		if (stats == null || stats.blocks() < HUD_MIN_BLOCKS || lastPoint >= 0 && now - lastPoint < STEP_MILLIS) return List.copyOf(points);
		long start = now - stats.millis();
		Double efficiency = (inShaft ? shaft : loop).window(Math.max(start, now - WINDOW_MILLIS), now, stats.block(), stats.ticks(), stats.boostTicks());
		if (efficiency != null) {
			points.add(efficiency);
			if (points.size() > MAX_POINTS) points.removeFirst();
			lastPoint = now;
		}
		return List.copyOf(points);
	}

	// null under HUD_MIN_BLOCKS
	public Long percent(long now) {
		MiningStats stats = current(now);
		if (stats == null || stats.blocks() < HUD_MIN_BLOCKS) return null;
		Double efficiency = Efficiency.of(stats);
		return efficiency == null ? null : Math.round(efficiency * 100);
	}
}
