package dev.skysurveyor.detection;

// shafts for tests outside this package, Shaft(long) is package private
public final class TestShafts {
	private TestShafts() {
	}

	public static Shaft entered(long at) {
		return new Shaft(at);
	}
}
