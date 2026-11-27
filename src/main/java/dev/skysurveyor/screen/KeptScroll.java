package dev.skysurveyor.screen;

import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;

// owo has no offset getter
final class KeptScroll extends ScrollContainer<FlowLayout> {
	static final int BAR = 2;
	// so text doesnt run under the bar
	static final int GUTTER = BAR + 1;
	// null = match by position
	private String tag;
	// -1 = none, size unknown till 1st draw
	private int revealTop = -1;
	private int revealBottom;
	private int revealContext;
	private boolean laidOut;

	KeptScroll(Sizing horizontalSizing, Sizing verticalSizing, FlowLayout child) {
		super(ScrollDirection.VERTICAL, horizontalSizing, verticalSizing, child);
		scrollbarThiccness(BAR);
		// owos fades out, cut row looked like the end
		scrollbar((graphics, x, y, w, h, trackX, trackY, trackW, trackH, lastInteract, direction, active) -> {
			if (active) graphics.fill(x, y, x + w, y + h, 0x80FFFFFF);
		});
	}

	void paddedWithBar(int padding) {
		padding(Insets.of(padding, padding, padding, padding - GUTTER));
		child().padding(Insets.right(GUTTER));
	}

	double offset() {
		return scrollOffset;
	}

	void offset(double offset) {
		scrollOffset = offset;
		currentScrollPosition = offset;
	}

	String tag() {
		return tag;
	}

	void tag(String tag) {
		this.tag = tag;
	}

	// childs pixels, no move if in view
	void reveal(int top, int bottom, int context) {
		revealTop = top;
		revealBottom = bottom;
		revealContext = context;
		if (laidOut) applyReveal();
	}

	@Override
	public void layout(Size space) {
		super.layout(space);
		laidOut = true;
		if (revealTop >= 0) applyReveal();
	}

	@Override
	public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
		revealTop = -1;
		super.draw(graphics, mouseX, mouseY, partialTicks, delta);
	}

	private void applyReveal() {
		int view = height - padding.get().vertical();
		if (view <= 0) return;
		int top = revealTop - revealContext;
		int bottom = revealBottom + revealContext;
		if (bottom - top > view) {
			top = revealTop;
			bottom = revealBottom;
		}
		double offset = scrollOffset;
		if (top < offset) offset = top;
		else if (bottom > offset + view) offset = bottom - view;
		offset(Math.clamp(offset, 0, maxScroll));
	}
}
