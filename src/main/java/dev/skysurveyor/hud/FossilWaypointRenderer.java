package dev.skysurveyor.hud;

import java.util.function.Supplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import dev.skysurveyor.data.FossilSpots;
import dev.skysurveyor.data.FossilSpots.Spot;
import dev.skysurveyor.detection.Shaft;
import dev.skysurveyor.detection.ShaftTracker;
import dev.skysurveyor.settings.WaypointSettings;

// vanilla gizmos, always on top = through walls (nicks exception, fixed spots only). the world only hides it
public final class FossilWaypointRenderer {
	// display choices
	private static final int FILL_ALPHA = 0x30000000;
	private static final float WIDTH = 2.5f;
	private static final double LABEL_ABOVE = 1.5;
	// display choice, box read twice a sec
	private static final int CHECK_TICKS = 10;

	private final Supplier<Shaft> shaft;
	private final Supplier<WaypointSettings> settings;
	private final FossilWaypoint.Mined mined = new FossilWaypoint.Mined();
	private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
	private int ticks;

	public FossilWaypointRenderer(Supplier<Shaft> shaft, Supplier<WaypointSettings> settings) {
		this.shaft = shaft;
		this.settings = settings;
	}

	// client tick. reads the box only to hide it, the spot stays from fossil_spots.txt
	public void tick(Minecraft client) {
		ClientLevel level = client.level;
		if (level == null || !settings.get().isFossil() || ticks++ % CHECK_TICKS != 0) return;
		Shaft now = shaft.get();
		mined.update(now, FossilWaypoint.spot(now, FossilSpots.defaults()), (x, y, z) -> ShaftTracker.FOSSIL_BLOCKS
				.contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.set(x, y, z)).getBlock()).getPath()));
	}

	// LevelRenderEvents.BEFORE_GIZMOS, inside vanillas per frame collector
	public void render() {
		WaypointSettings shown = settings.get();
		if (!shown.isFossil()) return;
		Shaft now = shaft.get();
		Spot spot = FossilWaypoint.spot(now, FossilSpots.defaults());
		if (spot == null || mined.mined(now)) return;
		int rgb = shown.colorRgb();
		int stroke = 0xFF000000 | rgb;
		int fill = FILL_ALPHA | rgb;
		Vec3 eye = Minecraft.getInstance().gameRenderer.mainCamera().position();
		double distance = FossilWaypoint.distance(spot, eye.x, eye.y, eye.z);
		GizmoStyle style = FossilWaypoint.filled(distance) ? GizmoStyle.strokeAndFill(stroke, WIDTH, fill) : GizmoStyle.stroke(stroke, WIDTH);
		Gizmos.cuboid(new AABB(spot.minX(), spot.minY(), spot.minZ(), spot.maxX() + 1, spot.maxY() + 1, spot.maxZ() + 1), style)
				.setAlwaysOnTop();
		Vec3 label = new Vec3(spot.centerX(), spot.maxY() + 1 + LABEL_ABOVE, spot.centerZ());
		Gizmos.billboardText(FossilWaypoint.label(distance), label,
				TextGizmo.Style.forColorAndCentered(stroke).withScale(FossilWaypoint.textScale(eye.distanceTo(label)))).setAlwaysOnTop();
	}
}
