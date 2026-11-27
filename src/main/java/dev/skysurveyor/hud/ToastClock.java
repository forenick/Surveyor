package dev.skysurveyor.hud;

public final class ToastClock {
	private long age;
	private long last = -1;

	public long tick(long now, boolean paused) {
		if (last >= 0 && !paused) age += Math.max(0, now - last);
		last = now;
		return age;
	}

	public long age() {
		return age;
	}
}
