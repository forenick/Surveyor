package dev.skysurveyor.screen;

import java.util.function.Consumer;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;

// on = green, off = grey, never red (mockups/ui-system-v1.html)
public final class Switch extends BaseUIComponent {
	// display choices
	public static final int TRACK_WIDTH = 20;
	public static final int TRACK_HEIGHT = 10;
	private static final int KNOB = TRACK_HEIGHT - 2;
	private static final int GAP = 4;
	private static final int TRACK_ON = 0xFF1F6B1F;
	private static final int TRACK_OFF = 0xFF3A3A3A;
	private static final int KNOB_ON = 0xFF55FF55;
	private static final int KNOB_OFF = 0xFF9A9A9A;

	private final boolean labelled;
	private final Consumer<Boolean> changed;
	private boolean on;

	// labelled = On / Off left of the track
	public Switch(boolean on, boolean labelled, Consumer<Boolean> changed) {
		this.on = on;
		this.labelled = labelled;
		this.changed = changed;
		cursorStyle(CursorStyle.HAND);
		horizontalSizing(Sizing.content());
		verticalSizing(Sizing.content());
	}

	public boolean on() {
		return on;
	}

	// no event, for a refresh from outside
	public void on(boolean value) {
		on = value;
	}

	public static int width(Font font, boolean labelled) {
		return (labelled ? labelWidth(font) + GAP : 0) + TRACK_WIDTH;
	}

	private static int labelWidth(Font font) {
		return Math.max(font.width("On"), font.width("Off"));
	}

	@Override
	protected int determineHorizontalContentSize(Sizing sizing) {
		return width(Minecraft.getInstance().font, labelled);
	}

	@Override
	protected int determineVerticalContentSize(Sizing sizing) {
		return TRACK_HEIGHT;
	}

	// a click, also from its name beside it
	public void toggle() {
		on = !on;
		UISounds.playButtonSound();
		changed.accept(on);
	}

	@Override
	public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
		if (click.button() != 0) return super.onMouseDown(click, doubled);
		toggle();
		super.onMouseDown(click, doubled);
		return true;
	}

	@Override
	public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
		Font font = Minecraft.getInstance().font;
		int trackX = x + width - TRACK_WIDTH;
		int trackY = y + (height - TRACK_HEIGHT) / 2;
		if (labelled) graphics.text(font, on ? "On" : "Off", x, y + (height - 8) / 2, on ? 0xFFFFFFFF : 0xFFAAAAAA, true);
		graphics.fill(trackX, trackY, trackX + TRACK_WIDTH, trackY + TRACK_HEIGHT, hovered ? 0xFFFFFFFF : 0xFF000000);
		graphics.fill(trackX + 1, trackY + 1, trackX + TRACK_WIDTH - 1, trackY + TRACK_HEIGHT - 1, on ? TRACK_ON : TRACK_OFF);
		int knobX = on ? trackX + TRACK_WIDTH - 1 - KNOB : trackX + 1;
		int knobY = trackY + 1;
		graphics.fill(knobX, knobY, knobX + KNOB, knobY + KNOB, on ? KNOB_ON : KNOB_OFF);
		// bevel like a button
		graphics.fill(knobX, knobY, knobX + KNOB, knobY + 1, 0x45FFFFFF);
		graphics.fill(knobX, knobY + 1, knobX + 1, knobY + KNOB, 0x45FFFFFF);
		graphics.fill(knobX, knobY + KNOB - 1, knobX + KNOB, knobY + KNOB, 0x70000000);
		graphics.fill(knobX + KNOB - 1, knobY, knobX + KNOB, knobY + KNOB - 1, 0x70000000);
	}
}
