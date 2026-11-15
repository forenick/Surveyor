package dev.skysurveyor.screen;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;

// drawn hover, not mouseEnter: survives rebuild under the mouse
public class HoverFlow extends FlowLayout {
	public static final int ROW = 0x1AFFFFFF;
	public static final int SMALL = 0x30FFFFFF;

	private final int hover;

	public HoverFlow(Sizing horizontal, Sizing vertical, int hover) {
		this(horizontal, vertical, hover, Algorithm.HORIZONTAL);
	}

	public HoverFlow(Sizing horizontal, Sizing vertical, int hover, Algorithm algorithm) {
		super(horizontal, vertical, algorithm);
		this.hover = hover;
		cursorStyle(CursorStyle.HAND);
	}

	@Override
	public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
		if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, hover);
		super.draw(graphics, mouseX, mouseY, partialTicks, delta);
	}

	public static class Box extends BoxComponent {
		private final int hover;

		public Box(Sizing horizontal, Sizing vertical, int hover) {
			super(horizontal, vertical);
			this.hover = hover;
			cursorStyle(CursorStyle.HAND);
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
			if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, hover);
		}
	}
}
