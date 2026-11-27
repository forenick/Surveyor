package dev.skysurveyor.hud;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import dev.skysurveyor.hud.HudLayout.Anchor;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudRenderer.Bounds;

// client thread
public final class HudPlace {
	public enum Side {
		BELOW, ABOVE, RIGHT, LEFT;

		public boolean vertical() {
			return this == BELOW || this == ABOVE;
		}
	}

	public record Size(int width, int height) {
	}

	private HudPlace() {
	}

	// null = not stuck, box gone, or a loop
	public static Box parent(Box box, List<Box> boxes) {
		Box parent = find(box.stickTo, boxes);
		if (parent == null || box.stickSide == null) return null;
		Box up = parent;
		for (int i = 0; i <= boxes.size() && up != null; i++) {
			if (up == box) return null;
			up = up.stickSide == null ? null : find(up.stickTo, boxes);
		}
		return parent;
	}

	public static boolean follows(Box box, Box mover, List<Box> boxes) {
		// parent() is null on a loop, so this ends
		for (Box up = parent(box, boxes); up != null; up = parent(up, boxes)) {
			if (up == mover) return true;
		}
		return false;
	}

	private static Box find(String id, List<Box> boxes) {
		if (id == null) return null;
		for (Box box : boxes) {
			if (id.equals(box.id)) return box;
		}
		return null;
	}

	// hidden box takes no room, ones stuck to it take its place
	public static Map<Box, Bounds> place(List<Box> boxes, Function<Box, Size> sizes, Predicate<Box> shown, int screenWidth,
			int screenHeight) {
		Map<Box, Bounds> placed = new IdentityHashMap<>();
		for (Box box : boxes) {
			if (shown.test(box)) place(box, boxes, sizes, shown, screenWidth, screenHeight, placed);
		}
		return placed;
	}

	private static Bounds place(Box box, List<Box> boxes, Function<Box, Size> sizes, Predicate<Box> shown, int screenWidth,
			int screenHeight, Map<Box, Bounds> placed) {
		Bounds done = placed.get(box);
		if (done != null) return done;
		Size size = sizes.apply(box);
		int w = size.width();
		int h = size.height();
		Box parent = parent(box, boxes);
		Bounds bounds;
		if (parent == null) {
			bounds = new Bounds(box.anchorX.start(box.offsetX, w, screenWidth), box.anchorY.start(box.offsetY, h, screenHeight), w, h);
		} else {
			Bounds p = place(parent, boxes, sizes, shown, screenWidth, screenHeight, placed);
			int gap = shown.test(parent) ? HudLayout.MARGIN : 0;
			int pw = gap == 0 ? 0 : p.width();
			int ph = gap == 0 ? 0 : p.height();
			int x = switch (box.stickSide) {
				case RIGHT -> p.x() + pw + gap;
				case LEFT -> p.x() + p.width() - pw - gap - w;
				default -> box.anchorX.startIn(box.offsetX, w, p.x(), p.width());
			};
			int y = switch (box.stickSide) {
				case BELOW -> p.y() + ph + gap;
				case ABOVE -> p.y() + p.height() - ph - gap - h;
				default -> box.anchorY.startIn(box.offsetY, h, p.y(), p.height());
			};
			bounds = new Bounds(Anchor.clamp(x, w, screenWidth), Anchor.clamp(y, h, screenHeight), w, h);
		}
		placed.put(box, bounds);
		return bounds;
	}

	// across = which edges line up
	public static void stick(Box box, Box parent, Side side, Anchor across, Bounds parentBounds, Bounds bounds, int screenWidth,
			int screenHeight) {
		// along: own anchors kept as the spot w/o the other box
		free(box, bounds, screenWidth, screenHeight);
		box.stickTo = parent.id;
		box.stickSide = side;
		if (side.vertical()) {
			box.freeAnchor = box.anchorX;
			box.freeOffset = box.offsetX;
			box.anchorX = across;
			box.offsetX = across.offsetIn(bounds.x(), bounds.width(), parentBounds.x(), parentBounds.width());
		} else {
			box.freeAnchor = box.anchorY;
			box.freeOffset = box.offsetY;
			box.anchorY = across;
			box.offsetY = across.offsetIn(bounds.y(), bounds.height(), parentBounds.y(), parentBounds.height());
		}
	}

	public static void free(Box box, Bounds bounds, int screenWidth, int screenHeight) {
		clear(box);
		box.anchorX = Anchor.forPosition(bounds.x(), bounds.width(), screenWidth);
		box.anchorY = Anchor.forPosition(bounds.y(), bounds.height(), screenHeight);
		box.offsetX = box.anchorX.offset(bounds.x(), bounds.width(), screenWidth);
		box.offsetY = box.anchorY.offset(bounds.y(), bounds.height(), screenHeight);
	}

	// across back to the screen spot saved when it stuck
	static void unlink(Box box) {
		if (box.stickSide != null && box.freeAnchor != null && box.freeOffset != null) {
			if (box.stickSide.vertical()) {
				box.anchorX = box.freeAnchor;
				box.offsetX = box.freeOffset;
			} else {
				box.anchorY = box.freeAnchor;
				box.offsetY = box.freeOffset;
			}
		}
		clear(box);
	}

	static void clear(Box box) {
		box.stickTo = null;
		box.stickSide = null;
		box.freeAnchor = null;
		box.freeOffset = null;
	}
}
