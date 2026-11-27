package dev.skysurveyor.hud;

import java.util.BitSet;

import dev.skysurveyor.data.FossilSpots;
import dev.skysurveyor.data.FossilSpots.Spot;
import dev.skysurveyor.detection.Shaft;

// spot from fossil_spots.txt only, world just hides it
public final class FossilWaypoint {
	// display choices, not game numbers
	static final double SCALE_PER_METER = 0.08;
	static final float MIN_SCALE = 0.4f;
	// fill tints the whole screen from inside
	static final double FILL_FROM = 5;
	// nick 11-21: gone if mostly mined. rec 11-21 23:03: 11/13, 29/29
	static final double MINED_SHARE = 0.8;

	private FossilWaypoint() {
	}

	// null = not in a shaft w/ a known fossil
	public static Spot spot(Shaft shaft, FossilSpots spots) {
		return shaft == null || shaft.id() == null ? null : spots.get(shaft.id().id());
	}

	static boolean mined(int broken, Spot spot) {
		return broken >= MINED_SHARE * spot.blocks();
	}

	// seen 1st, else unloaded chunk = mined. anyones breaks count
	public static final class Mined {
		public interface Blocks {
			boolean fossil(int x, int y, int z);
		}

		private final BitSet seen = new BitSet();
		private Shaft shaft;
		private boolean mined;

		public void update(Shaft shaft, Spot spot, Blocks blocks) {
			if (shaft != this.shaft) {
				this.shaft = shaft;
				seen.clear();
				mined = false;
			}
			if (spot == null) return;
			int gone = 0;
			int i = 0;
			for (int x = spot.minX(); x <= spot.maxX(); x++) {
				for (int y = spot.minY(); y <= spot.maxY(); y++) {
					for (int z = spot.minZ(); z <= spot.maxZ(); z++, i++) {
						if (blocks.fossil(x, y, z)) seen.set(i);
						else if (seen.get(i)) gone++;
					}
				}
			}
			mined = FossilWaypoint.mined(gone, spot);
		}

		public boolean mined(Shaft shaft) {
			return shaft == this.shaft && mined;
		}
	}

	// nearest block of the box, 0 inside
	public static double distance(Spot spot, double x, double y, double z) {
		double dx = Math.max(0, Math.max(spot.minX() - x, x - (spot.maxX() + 1)));
		double dy = Math.max(0, Math.max(spot.minY() - y, y - (spot.maxY() + 1)));
		double dz = Math.max(0, Math.max(spot.minZ() - z, z - (spot.maxZ() + 1)));
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	public static String label(double distance) {
		return "Fossil " + Math.round(distance) + "m";
	}

	// distance = eye to the label
	public static float textScale(double distance) {
		return (float) Math.max(MIN_SCALE, SCALE_PER_METER * distance);
	}

	public static boolean filled(double distance) {
		return distance > FILL_FROM;
	}
}
