package dev.skysurveyor.hud;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.hud.HudLayout.Anchor;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudPlace.Side;
import dev.skysurveyor.hud.HudPlace.Size;
import dev.skysurveyor.hud.HudRenderer.Bounds;
import dev.skysurveyor.hud.HudSnap.Rect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// 480x270 (1080p auto gui scale), margin 4. sizes made up
class HudPlaceTest {
	private static final int W = 480;
	private static final int H = 270;
	private final Map<String, Size> sizes = new HashMap<>();

	private Box box(String id, int w, int h) {
		Box box = new Box();
		box.id = id;
		sizes.put(id, new Size(w, h));
		return box;
	}

	private Box under(Box parent, String id, int w, int h) {
		Box box = box(id, w, h);
		box.stickTo = parent.id;
		box.stickSide = Side.BELOW;
		return box;
	}

	private Map<Box, Bounds> place(List<Box> boxes, Set<String> hidden) {
		return HudPlace.place(boxes, b -> sizes.get(b.id), b -> !hidden.contains(b.id), W, H);
	}

	@Test
	void stuckBoxMovesWhenTheOtherGrows() {
		Box top = box("top", 80, 40);
		top.offsetY = 10;
		Box below = under(top, "below", 60, 20);
		List<Box> boxes = List.of(below, top);
		assertEquals(new Bounds(4, 14 + 40 + 4, 60, 20), place(boxes, Set.of()).get(below));
		sizes.put("top", new Size(80, 75));
		assertEquals(new Bounds(4, 14 + 75 + 4, 60, 20), place(boxes, Set.of()).get(below));
	}

	@Test
	void everySideKeepsTheGap() {
		Box mid = box("mid", 100, 50);
		mid.anchorX = Anchor.MIDDLE;
		mid.anchorY = Anchor.MIDDLE;
		Box above = box("above", 40, 20);
		above.stickTo = "mid";
		above.stickSide = Side.ABOVE;
		Box left = box("left", 40, 20);
		left.stickTo = "mid";
		left.stickSide = Side.LEFT;
		Box right = box("right", 40, 20);
		right.stickTo = "mid";
		right.stickSide = Side.RIGHT;
		Map<Box, Bounds> placed = place(List.of(mid, above, left, right), Set.of());
		assertEquals(new Bounds(190, 110, 100, 50), placed.get(mid));
		assertEquals(new Bounds(190, 110 - 4 - 20, 40, 20), placed.get(above));
		assertEquals(new Bounds(190 - 4 - 40, 110, 40, 20), placed.get(left));
		assertEquals(new Bounds(290 + 4, 110, 40, 20), placed.get(right));
	}

	@Test
	void acrossFollowsTheSharedEdge() {
		Box top = box("top", 100, 40);
		top.anchorX = Anchor.END;
		Box below = under(top, "below", 60, 20);
		below.anchorX = Anchor.END;
		List<Box> boxes = List.of(top, below);
		assertEquals(476 - 60, place(boxes, Set.of()).get(below).x(), "right edges line up");
		sizes.put("top", new Size(140, 40));
		assertEquals(476 - 60, place(boxes, Set.of()).get(below).x());
		below.anchorX = Anchor.MIDDLE;
		below.offsetX = 5;
		assertEquals(476 - 140 + 40 + 5, place(boxes, Set.of()).get(below).x(), "centred, 5 right");
	}

	@Test
	void hiddenBoxTakesNoRoomDownTheChain() {
		Box session = box("session", 80, 30);
		Box spawn = under(session, "spawn", 80, 25);
		Box shaft = under(spawn, "shaft", 80, 60);
		Box money = under(shaft, "money", 80, 50);
		List<Box> boxes = List.of(session, spawn, shaft, money);
		Map<Box, Bounds> mines = place(boxes, Set.of("shaft"));
		assertEquals(4 + 30 + 4, mines.get(spawn).y());
		assertEquals(4 + 30 + 4 + 25 + 4, mines.get(money).y(), "right under the spawn box");
		Map<Box, Bounds> inShaft = place(boxes, Set.of("spawn"));
		assertEquals(4 + 30 + 4, inShaft.get(shaft).y(), "takes the spawn boxes place");
		assertEquals(4 + 30 + 4 + 60 + 4, inShaft.get(money).y());
	}

	@Test
	void hiddenBoxAboveTakesNoRoomEither() {
		Box bottom = box("bottom", 80, 30);
		bottom.anchorY = Anchor.END;
		Box mid = box("mid", 80, 25);
		mid.stickTo = "bottom";
		mid.stickSide = Side.ABOVE;
		Box top = box("top", 80, 20);
		top.stickTo = "mid";
		top.stickSide = Side.ABOVE;
		Map<Box, Bounds> placed = place(List.of(bottom, mid, top), Set.of("mid"));
		assertEquals(266 - 30 - 4 - 20, placed.get(top).y());
	}

	@Test
	void staysOnScreen() {
		Box top = box("top", 80, 200);
		Box below = under(top, "below", 60, 100);
		assertEquals(270 - 4 - 100, place(List.of(top, below), Set.of()).get(below).y());
	}

	@Test
	void loopsAndMissingBoxesFallBackToOwnAnchors() {
		Box a = box("a", 50, 20);
		Box b = under(a, "b", 50, 20);
		a.stickTo = "b";
		a.stickSide = Side.BELOW;
		Box lost = box("lost", 50, 20);
		lost.stickTo = "gone";
		lost.stickSide = Side.RIGHT;
		lost.offsetX = 100;
		List<Box> boxes = List.of(a, b, lost);
		assertNull(HudPlace.parent(a, boxes));
		assertNull(HudPlace.parent(b, boxes));
		assertNull(HudPlace.parent(lost, boxes));
		assertEquals(new Bounds(104, 4, 50, 20), place(boxes, Set.of()).get(lost));
	}

	@Test
	void followsUpTheChain() {
		Box a = box("a", 50, 20);
		Box b = under(a, "b", 50, 20);
		Box c = under(b, "c", 50, 20);
		List<Box> boxes = List.of(a, b, c);
		assertTrue(HudPlace.follows(c, a, boxes));
		assertTrue(HudPlace.follows(b, a, boxes));
		assertFalse(HudPlace.follows(a, c, boxes));
		assertFalse(HudPlace.follows(a, a, boxes));
	}

	@Test
	void stickThenPlaceGivesTheSameSpot() {
		Box parent = box("parent", 100, 40);
		parent.offsetX = 50;
		parent.offsetY = 60;
		Bounds p = new Bounds(54, 64, 100, 40);
		for (Anchor across : Anchor.values()) {
			Box child = box("child", 61, 20);
			Bounds at = new Bounds(70, 108, 61, 20);
			HudPlace.stick(child, parent, Side.BELOW, across, p, at, W, H);
			assertEquals(at, place(List.of(parent, child), Set.of()).get(child), across.name());
			Box side = box("side", 30, 21);
			Bounds beside = new Bounds(20, 70, 30, 21);
			HudPlace.stick(side, parent, Side.LEFT, across, p, beside, W, H);
			assertEquals(beside, place(List.of(parent, side), Set.of()).get(side), across.name());
		}
	}

	@Test
	void freeKeepsTheSpot() {
		Box top = box("top", 80, 40);
		Box below = under(top, "below", 60, 20);
		below.offsetX = 7;
		List<Box> boxes = List.of(top, below);
		Bounds at = place(boxes, Set.of()).get(below);
		HudPlace.free(below, at, W, H);
		assertNull(below.stickTo);
		assertNull(below.stickSide);
		assertEquals(at, place(boxes, Set.of()).get(below));
	}

	@Test
	void snapOnTheGapSticks() {
		Rect other = new Rect(4, 4, 80, 40, Anchor.START, Anchor.START);
		HudSnap.Result under = HudSnap.snap(new Rect(5, 50, 60, 20, Anchor.START, Anchor.START), List.of(other), W, H, 4);
		assertEquals(new HudSnap.Stick(0, Side.BELOW, null), under.stick(), "screen margin wins the x snap");
		HudSnap.Result right = HudSnap.snap(new Rect(86, 5, 60, 20, Anchor.START, Anchor.START), List.of(other), W, H, 4);
		assertEquals(Side.RIGHT, right.stick().side());
		Rect far = new Rect(200, 100, 80, 40, Anchor.MIDDLE, Anchor.MIDDLE);
		HudSnap.Result edge = HudSnap.snap(new Rect(201, 74, 60, 20, Anchor.START, Anchor.START), List.of(far), W, H, 4);
		assertEquals(new HudSnap.Stick(0, Side.ABOVE, Anchor.START), edge.stick(), "left edges shared");
		HudSnap.Result none = HudSnap.snap(new Rect(201, 60, 60, 20, Anchor.START, Anchor.START), List.of(far), W, H, 4);
		assertNull(none.stick());
	}

	@Test
	void goneBoxLeavesTheStuckBoxWhereItWas() {
		Box parent = box("parent", 100, 40);
		parent.anchorX = Anchor.END;
		parent.offsetX = 30;
		Box child = box("child", 60, 20);
		Bounds at = new Bounds(380, 48, 60, 20);
		HudPlace.stick(child, parent, Side.BELOW, Anchor.END, new Bounds(346, 4, 100, 40), at, W, H);
		HudLayout layout = HudLayout.defaults();
		layout.boxes.clear();
		layout.boxes.add(child);
		Box read = HudLayout.fromJson(layout.toJson()).boxes.get(0);
		assertNull(read.stickTo);
		assertNull(read.freeAnchor);
		assertEquals(at, place(List.of(read), Set.of()).get(read));
	}

	@Test
	void loopCutKeepsTheScreenSpot() {
		Box a = box("a", 50, 20);
		Box b = box("b", 50, 20);
		Bounds aAt = new Bounds(200, 100, 50, 20);
		HudPlace.stick(a, b, Side.RIGHT, Anchor.START, new Bounds(146, 90, 50, 20), aAt, W, H);
		HudPlace.stick(b, a, Side.LEFT, Anchor.START, aAt, new Bounds(146, 90, 50, 20), W, H);
		HudLayout layout = HudLayout.defaults();
		layout.boxes.clear();
		layout.boxes.addAll(List.of(a, b));
		HudLayout read = HudLayout.fromJson(layout.toJson());
		assertEquals(aAt, place(read.boxes, Set.of()).get(read.boxes.get(0)));
	}

	@Test
	void badLinksInAFileAreDropped() {
		HudLayout layout = HudLayout.defaults();
		layout.boxes.get(0).stickTo = "nope";
		layout.boxes.get(0).stickSide = Side.BELOW;
		layout.boxes.get(1).stickTo = layout.boxes.get(2).id;
		layout.boxes.get(1).stickSide = Side.BELOW;
		layout.boxes.get(2).stickTo = layout.boxes.get(1).id;
		layout.boxes.get(2).stickSide = Side.ABOVE;
		layout.boxes.get(3).stickTo = layout.boxes.get(0).id;
		layout.boxes.get(3).stickSide = Side.RIGHT;
		HudLayout read = HudLayout.fromJson(layout.toJson());
		assertNull(read.boxes.get(0).stickTo);
		assertNull(read.boxes.get(1).stickTo);
		// loop cut at the 1st box, other link fine
		assertEquals(Side.ABOVE, read.boxes.get(2).stickSide);
		assertEquals(layout.boxes.get(0).id, read.boxes.get(3).stickTo);
		assertEquals(Side.RIGHT, read.boxes.get(3).stickSide);
		assertEquals(read.toJson(), HudLayout.fromJson(read.toJson()).toJson());
	}
}
