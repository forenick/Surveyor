package dev.skysurveyor.detection;

// Shaft(long) is package private
public final class TestShafts {
	private TestShafts() {
	}

	public static Shaft entered(long at) {
		return new Shaft(at);
	}
}
