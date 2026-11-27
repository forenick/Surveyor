package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudRenderer.Bounds;

// editor only, hud.json order + the game's hud stay as they are
final class EditorLayers {
	// hearts + armour row over the hotbar
	static final int HOTBAR_HEIGHT = 50;
	static final int HOTBAR_HALF = 91;

	private EditorLayers() {
	}

	// back to front. shown here on top, then boxes for the mines (spawn over the shaft box outside both)
	static List<Box> order(List<Box> boxes, Predicate<Box> shownHere) {
		List<Box> order = new ArrayList<>(boxes);
		order.sort(Comparator.comparingInt(box -> shownHere.test(box) ? 2 : box.showInMines ? 1 : 0));
		return order;
	}

	// picked box on top, so u see what u edit
	static List<Box> order(List<Box> boxes, Predicate<Box> shownHere, Box picked) {
		List<Box> order = order(boxes, shownHere);
		if (picked != null && order.remove(picked)) order.add(picked);
		return order;
	}

	// front one wins, same as drawn
	static <T> T hit(List<T> backToFront, Predicate<T> contains) {
		T hit = null;
		for (T t : backToFront) {
			if (contains.test(t)) hit = t;
		}
		return hit;
	}

	// 2nd click on the picked box: next one under it there, wraps. null = nothing else there
	static <T> T below(List<T> backToFront, Predicate<T> contains, T picked) {
		int at = backToFront.indexOf(picked);
		if (at < 0) return null;
		for (int i = 1; i < backToFront.size(); i++) {
			T t = backToFront.get(Math.floorMod(at - i, backToFront.size()));
			if (contains.test(t)) return t;
		}
		return null;
	}

	// null = some box shows here, no line
	static String hiddenLine(List<Box> boxes, boolean inMines, boolean inShaft) {
		if (boxes.isEmpty() || boxes.stream().anyMatch(box -> box.visible(inMines, inShaft))) return null;
		List<String> places = new ArrayList<>();
		if (boxes.stream().anyMatch(box -> box.showInMines)) places.add("in the Mines");
		if (boxes.stream().anyMatch(box -> box.showInShaft)) places.add("in shafts");
		if (boxes.stream().anyMatch(box -> box.showElsewhere)) places.add("elsewhere");
		if (places.isEmpty()) return "No box shows anywhere. Click one and pick Show in.";
		String where = places.size() == 1 ? places.getFirst()
				: String.join(", ", places.subList(0, places.size() - 1)) + " and " + places.getLast();
		return "No box shows here. They show " + where + ".";
	}

	// above or below each box, never on a box, the hints or another tag. null = no free spot
	static List<Bounds> tags(List<Bounds> hidden, List<Bounds> blocked, int tagWidth, int tagHeight, int screenWidth,
			int screenHeight) {
		List<Bounds> taken = new ArrayList<>();
		for (Bounds b : blocked) taken.add(b.grown(1));
		List<Bounds> tags = new ArrayList<>();
		for (Bounds b : hidden) {
			int right = b.x() + b.width() - tagWidth;
			// outline is 1 px out, tag 1 px past that
			int above = b.y() - 2 - tagHeight;
			int below = b.y() + b.height() + 2;
			Bounds tag = null;
			for (int[] spot : new int[][] {{right, above}, {right, below}, {b.x(), above}, {b.x(), below}}) {
				int x = Math.max(0, Math.min(screenWidth - tagWidth, spot[0]));
				Bounds at = new Bounds(x, spot[1], tagWidth, tagHeight);
				if (at.y() < 0 || at.y() + tagHeight > screenHeight) continue;
				if (taken.stream().allMatch(t -> t.overlapArea(at) == 0)) {
					tag = at;
					break;
				}
			}
			tags.add(tag);
			if (tag != null) taken.add(tag.grown(1));
		}
		return tags;
	}

	// over the hotbar if free, else the lowest free spot. null = none free
	static Bounds freeSpot(int width, int height, int screenWidth, int screenHeight, List<Bounds> blocked) {
		for (Bounds at : spots(width, height, screenWidth, screenHeight)) {
			if (cost(at, blocked) == 0) return at;
		}
		return null;
	}

	static Bounds cheapestSpot(int width, int height, int screenWidth, int screenHeight, List<Bounds> blocked) {
		Bounds best = null;
		long bestCost = Long.MAX_VALUE;
		for (Bounds at : spots(width, height, screenWidth, screenHeight)) {
			long cost = cost(at, blocked);
			if (cost < bestCost) {
				best = at;
				bestCost = cost;
			}
		}
		return best;
	}

	// bottom up, middle then right then left. step = display choice
	private static List<Bounds> spots(int width, int height, int screenWidth, int screenHeight) {
		int[] xs = {(screenWidth - width) / 2, screenWidth - width - 2, 2};
		List<Bounds> spots = new ArrayList<>();
		for (int y = screenHeight - HOTBAR_HEIGHT - 2 - height; y >= 2; y -= 4) {
			for (int x : xs) spots.add(new Bounds(Math.max(0, x), y, width, height));
		}
		if (spots.isEmpty()) spots.add(new Bounds(Math.max(0, xs[0]), 0, width, height));
		return spots;
	}

	private static long cost(Bounds at, List<Bounds> blocked) {
		long cost = 0;
		for (Bounds b : blocked) cost += at.overlapArea(b.grown(1));
		return cost;
	}

	static Bounds hotbar(int screenWidth, int screenHeight) {
		return new Bounds(screenWidth / 2 - HOTBAR_HALF, screenHeight - HOTBAR_HEIGHT, 2 * HOTBAR_HALF, HOTBAR_HEIGHT);
	}
}
