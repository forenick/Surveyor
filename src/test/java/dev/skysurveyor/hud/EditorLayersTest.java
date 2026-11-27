package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudRenderer.Bounds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EditorLayersTest {
	private static Box box(List<Box> boxes, String id) {
		return boxes.stream().filter(b -> b.id.equals(id)).findFirst().orElseThrow();
	}

	private static List<String> ids(List<Box> boxes) {
		return boxes.stream().map(b -> b.id).toList();
	}

	// default spawn + shaft box share one spot
	@Test
	void shownHereOnTop() {
		List<Box> boxes = HudLayout.defaults().boxes;
		List<String> saved = ids(boxes);
		List<Box> mines = EditorLayers.order(boxes, b -> b.visible(true, false));
		assertTrue(ids(mines).indexOf("spawn") > ids(mines).indexOf("shaft"), "mines: spawn over shaft " + ids(mines));
		List<Box> shaft = EditorLayers.order(boxes, b -> b.visible(false, true));
		assertTrue(ids(shaft).indexOf("shaft") > ids(shaft).indexOf("spawn"), "shaft: shaft box over spawn " + ids(shaft));
		// hub: neither shows, the mines one wins
		List<Box> hub = EditorLayers.order(boxes, b -> b.visible(false, false));
		assertTrue(ids(hub).indexOf("spawn") > ids(hub).indexOf("shaft"), "elsewhere: spawn over shaft " + ids(hub));
		assertEquals(saved, ids(boxes), "saved order untouched");
	}

	@Test
	void sameRankKeepsSavedOrder() {
		List<Box> boxes = HudLayout.defaults().boxes;
		List<Box> all = EditorLayers.order(boxes, b -> true);
		assertEquals(ids(boxes), ids(all));
	}

	@Test
	void clickPicksTheOneOnTop() {
		List<Box> boxes = HudLayout.defaults().boxes;
		Map<String, Bounds> at = Map.of("session", new Bounds(0, 0, 50, 40), "spawn", new Bounds(0, 54, 60, 30), "shaft",
				new Bounds(0, 54, 90, 80), "money", new Bounds(0, 140, 90, 60));
		for (boolean inShaft : new boolean[] {false, true}) {
			List<Box> order = EditorLayers.order(boxes, b -> b.visible(!inShaft, inShaft));
			Box hit = EditorLayers.hit(order, b -> at.get(b.id).contains(10, 60));
			assertEquals(inShaft ? "shaft" : "spawn", hit.id);
			// order is also the draw order, so the picked one is the one u see
			assertEquals(hit, order.stream().filter(b -> at.get(b.id).contains(10, 60)).reduce((a, b) -> b).orElseThrow());
		}
		assertEquals("shaft", EditorLayers.hit(EditorLayers.order(boxes, b -> b.visible(true, false)),
				b -> at.get(b.id).contains(80, 100)).id, "lower part of the shaft box still clicks");
		assertNull(EditorLayers.hit(boxes, b -> false));
	}

	@Test
	void hiddenLineOnlyWhenAllHidden() {
		List<Box> boxes = HudLayout.defaults().boxes;
		assertNull(EditorLayers.hiddenLine(boxes, true, false));
		assertNull(EditorLayers.hiddenLine(boxes, false, true));
		assertEquals("No box shows here. They show in the Mines and in shafts.", EditorLayers.hiddenLine(boxes, false, false));
		assertNull(EditorLayers.hiddenLine(List.of(), false, false));
		Box only = new Box();
		only.showInMines = false;
		only.showInShaft = true;
		only.showElsewhere = false;
		assertEquals("No box shows here. They show in shafts.", EditorLayers.hiddenLine(List.of(only), true, false));
		only.showInShaft = false;
		assertTrue(EditorLayers.hiddenLine(List.of(only), true, false).startsWith("No box shows anywhere"));
	}

	@Test
	void tagsNeverOverlap() {
		// spawn + shaft at one spot, session right above: old code put both tags on one spot
		Bounds session = new Bounds(4, 4, 110, 40);
		Bounds spawn = new Bounds(4, 58, 80, 30);
		Bounds shaft = new Bounds(4, 58, 140, 90);
		List<Bounds> blocked = List.of(session, spawn, shaft);
		List<Bounds> tags = EditorLayers.tags(List.of(spawn, shaft), blocked, 60, 10, 480, 270);
		List<Bounds> placed = new ArrayList<>();
		for (Bounds tag : tags) {
			if (tag == null) continue;
			for (Bounds b : blocked) assertEquals(0, tag.overlapArea(b.grown(1)), "tag on a box " + tag);
			for (Bounds other : placed) assertEquals(0, tag.overlapArea(other), "tag on a tag " + tag);
			assertTrue(tag.x() >= 0 && tag.y() >= 0 && tag.x() + tag.width() <= 480 && tag.y() + tag.height() <= 270);
			placed.add(tag);
		}
		assertNotNull(tags.get(1), "shaft box gets one below");
	}

	@Test
	void hintsDodgeBoxes() {
		Bounds hotbar = EditorLayers.hotbar(480, 270);
		Bounds clear = EditorLayers.freeSpot(340, 36, 480, 270, List.of(hotbar));
		assertEquals(0, clear.overlapArea(hotbar.grown(1)), "over the hotbar");
		assertEquals(270 - EditorLayers.HOTBAR_HEIGHT - 2 - 36, clear.y());
		// a box right there pushes it up
		Bounds box = new Bounds(100, 170, 200, 40);
		Bounds moved = EditorLayers.freeSpot(340, 36, 480, 270, List.of(hotbar, box));
		assertEquals(0, moved.overlapArea(box.grown(1)));
		assertTrue(moved.y() < box.y());
		// no room anywhere: null, cheapest still gives one
		Bounds wall = new Bounds(0, 0, 480, 270);
		assertNull(EditorLayers.freeSpot(340, 36, 480, 270, List.of(wall)));
		assertNotNull(EditorLayers.cheapestSpot(340, 36, 480, 270, List.of(wall)));
	}

	// 2nd click on the same spot goes down, then back to the top
	@Test
	void secondClickPicksTheBoxUnder() {
		List<String> backToFront = List.of("a", "b", "c", "d");
		java.util.function.Predicate<String> at = s -> !s.equals("c");
		assertEquals("b", EditorLayers.below(backToFront, at, "d"));
		assertEquals("a", EditorLayers.below(backToFront, at, "b"));
		assertEquals("d", EditorLayers.below(backToFront, at, "a"));
		assertNull(EditorLayers.below(List.of("a", "b"), s -> s.equals("b"), "b"));
		assertNull(EditorLayers.below(backToFront, at, "x"));
	}

	@Test
	void pickedBoxDrawsOnTop() {
		List<Box> boxes = HudLayout.defaults().boxes;
		Box spawn = boxes.stream().filter(b -> b.id.equals("spawn")).findFirst().orElseThrow();
		List<Box> order = EditorLayers.order(boxes, b -> b.visible(false, true), spawn);
		assertEquals(spawn, order.getLast());
		assertEquals(EditorLayers.order(boxes, b -> b.visible(false, true)), EditorLayers.order(boxes, b -> b.visible(false, true), null));
	}
}
