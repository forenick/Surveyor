package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;

import dev.skysurveyor.hud.HudLayout.Anchor;

public final class HudSnap {
	public static final int DISTANCE = 4;
	private static final int OVERHANG = 3;
	// mc font: digit 6px w/ spacing, line 9px
	private static final int LABEL_WIDTH = 6;
	private static final int LABEL_HEIGHT = 9;

	public record Rect(double x, double y, double width, double height, Anchor anchorX, Anchor anchorY) {
		Rect transposed() {
			return new Rect(y, x, height, width, anchorY, anchorX);
		}
	}

	public record Guide(int x, int y, int width, int height, String label, int labelX, int labelY) {
		Guide transposed() {
			return new Guide(y, x, height, width, label, labelY, labelX);
		}
	}

	// dropped on the gap next to others[other]. cross = edge shared w/ it on the other axis, null = none
	public record Stick(int other, HudPlace.Side side, Anchor cross) {
	}

	// anchor null = editor decides
	public record Result(double x, double y, Anchor anchorX, Anchor anchorY, List<Guide> guides, Stick stick) {
	}

	// edge 0 start, 0.5 mid, 1 end. gapStart = stacked. index in others, -1 = screen
	private record Target(double edge, double value, Double gapStart, int index) {
	}

	private record Hit(double start, Target target) {
	}

	private HudSnap() {
	}

	public static Result snap(Rect box, List<Rect> others, int screenWidth, int screenHeight, int margin) {
		Hit hx = snapAxis(box, others, screenWidth, margin);
		List<Rect> othersT = others.stream().map(Rect::transposed).toList();
		Hit hy = snapAxis(box.transposed(), othersT, screenHeight, margin);
		Rect placed = new Rect(hx.start(), hy.start(), box.width(), box.height(), box.anchorX(), box.anchorY());

		List<Guide> guides = new ArrayList<>();
		if (hx.target() != null) {
			guides.add(guide(placed, hx.target(), others, screenHeight, margin, LABEL_WIDTH, LABEL_HEIGHT));
		}
		if (hy.target() != null) {
			guides.add(guide(placed.transposed(), hy.target(), othersT, screenWidth, margin, LABEL_HEIGHT, LABEL_WIDTH).transposed());
		}
		// stacked wins over side by side
		Stick stick = stick(hy, hx, HudPlace.Side.BELOW, HudPlace.Side.ABOVE);
		if (stick == null) stick = stick(hx, hy, HudPlace.Side.RIGHT, HudPlace.Side.LEFT);
		return new Result(placed.x(), placed.y(), anchor(hx, others), anchor(hy, othersT), guides, stick);
	}

	private static Stick stick(Hit along, Hit across, HudPlace.Side after, HudPlace.Side before) {
		Target t = along.target();
		if (t == null || t.gapStart() == null) return null;
		Target c = across.target();
		boolean shared = c != null && c.index() == t.index() && c.gapStart() == null;
		return new Stick(t.index(), t.edge() == 0 ? after : before, shared ? Anchor.ofEdge(c.edge()) : null);
	}

	private static Hit snapAxis(Rect box, List<Rect> others, int screen, int margin) {
		List<Target> targets = new ArrayList<>();
		// screen first, wins ties
		targets.add(new Target(0, margin, null, -1));
		targets.add(new Target(1, screen - margin, null, -1));
		targets.add(new Target(0.5, screen / 2.0, null, -1));
		for (int i = 0; i < others.size(); i++) {
			Rect o = others.get(i);
			targets.add(new Target(0, o.x(), null, i));
			targets.add(new Target(1, o.x() + o.width(), null, i));
			targets.add(new Target(0.5, o.x() + o.width() / 2, null, i));
			if (sideBySide(box, o)) {
				targets.add(new Target(0, o.x() + o.width() + margin, o.x() + o.width(), i));
				targets.add(new Target(1, o.x() - margin, o.x() - margin, i));
			}
		}
		Target best = null;
		double bestDistance = Double.MAX_VALUE;
		for (Target t : targets) {
			double start = t.value() - t.edge() * box.width();
			// skip unreachable, guide would be wrong
			if (start < margin || start + box.width() > screen - margin) continue;
			double distance = Math.abs(start - box.x());
			if (distance <= DISTANCE && distance < bestDistance) {
				best = t;
				bestDistance = distance;
			}
		}
		return best == null ? new Hit(box.x(), null) : new Hit(best.value() - best.edge() * box.width(), best);
	}

	private static boolean sideBySide(Rect box, Rect o) {
		return box.y() < o.y() + o.height() + DISTANCE && o.y() < box.y() + box.height() + DISTANCE;
	}

	// take other box anchor only if it uses the same edge
	private static Anchor anchor(Hit hit, List<Rect> others) {
		Target t = hit.target();
		if (t == null || t.gapStart() != null) return null;
		Anchor edge = Anchor.ofEdge(t.edge());
		if (t.index() < 0) return edge;
		return others.get(t.index()).anchorX() == edge ? edge : null;
	}

	// middle of where 2 spans meet on the other axis, gap guide + editor link
	static int middle(double aStart, double aLength, double bStart, double bLength) {
		return (int) Math.floor((Math.max(aStart, bStart) + Math.min(aStart + aLength, bStart + bLength)) / 2);
	}

	private static Guide guide(Rect box, Target t, List<Rect> others, int crossLength, int margin, int labelAlong,
			int labelAcross) {
		Rect o = t.index() < 0 ? null : others.get(t.index());
		if (t.gapStart() != null) {
			int middle = middle(box.y(), box.height(), o.y(), o.height());
			int gap = (int) Math.round(t.gapStart());
			int labelAt = (int) Math.round(t.gapStart() + margin / 2.0 - labelAlong / 2.0);
			int above = (int) Math.floor(Math.min(box.y(), o.y())) - labelAcross - 1;
			int labelAcrossAt = above >= 0 ? above : (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + 2;
			return new Guide(gap, middle, margin, 1, Integer.toString(margin), labelAt, labelAcrossAt);
		}
		// just before start edge, just after end edge
		int column = (int) Math.floor(t.value()) - (t.edge() == 0 ? 1 : 0);
		if (o == null) return new Guide(column, 0, 1, crossLength, null, 0, 0);
		int top = (int) Math.floor(Math.min(box.y(), o.y())) - OVERHANG;
		int bottom = (int) Math.ceil(Math.max(box.y() + box.height(), o.y() + o.height())) + OVERHANG;
		return new Guide(column, top, 1, bottom - top, null, 0, 0);
	}
}
