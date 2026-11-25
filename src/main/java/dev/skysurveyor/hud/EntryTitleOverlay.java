package dev.skysurveyor.hud;

import java.util.function.Supplier;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.ARGB;

import dev.skysurveyor.data.FossilSpots;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.settings.EntryTitleSettings;

// draws EntryTitle where vanilla titles go, only our own text. client thread
public final class EntryTitleOverlay {
	// display choices, vanilla uses 4 + 2
	private static final float TITLE_SCALE = 3;
	private static final float LINE_SCALE = 1.5f;
	private static final int SIDE_GAP = 10;

	private final EntryTitle title = new EntryTitle(ShaftNames.defaults(), FossilSpots.defaults()::has);
	private final Supplier<EntryTitleSettings> settings;

	public EntryTitleOverlay(Supplier<EntryTitleSettings> settings) {
		this.settings = settings;
	}

	public void onEvent(ShaftEvent event) {
		title.onEvent(event, System.currentTimeMillis());
	}

	public void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		EntryTitleSettings shown = settings.get();
		if (!shown.isShow() || client.gui.hud.isHidden()) return;
		// still counts down behind a screen
		EntryTitle.Text text = title.text(System.currentTimeMillis(), shown.seconds());
		if (text == null || LiveHud.modScreenOpen()) return;
		draw(graphics, client.font, text);
	}

	static void draw(GuiGraphicsExtractor graphics, Font font, EntryTitle.Text text) {
		int color = ARGB.white(text.alpha());
		float room = graphics.guiWidth() - 2 * SIDE_GAP;
		float centerX = graphics.guiWidth() / 2f;
		float centerY = graphics.guiHeight() / 2f;
		float scale = fit(font.width(text.title()), room, TITLE_SCALE);
		drawLine(graphics, font, text.title(), centerX, centerY - (font.lineHeight + 1) * scale, scale, color);
		float y = centerY + 5;
		for (String line : text.lines()) {
			float lineScale = fit(font.width(line), room, LINE_SCALE);
			drawLine(graphics, font, line, centerX, y, lineScale, color);
			y += (font.lineHeight + 2) * lineScale;
		}
	}

	private static float fit(int width, float room, float most) {
		return width <= 0 ? most : Math.min(most, room / width);
	}

	private static void drawLine(GuiGraphicsExtractor graphics, Font font, String line, float centerX, float y, float scale, int color) {
		graphics.pose().pushMatrix();
		try {
			graphics.pose().translate(centerX, y);
			graphics.pose().scale(scale, scale);
			graphics.text(font, line, -font.width(line) / 2, 0, color, true);
		} finally {
			graphics.pose().popMatrix();
		}
	}
}
