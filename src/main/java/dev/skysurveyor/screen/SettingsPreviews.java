package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.Font;

import dev.skysurveyor.hud.EntryTitle;
import dev.skysurveyor.hud.EntryTitleOverlay;
import dev.skysurveyor.hud.FossilWaypoint;
import dev.skysurveyor.hud.ToastRenderer;
import dev.skysurveyor.journal.SummaryText;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.settings.EntryTitleSettings;
import dev.skysurveyor.settings.Setting;
import dev.skysurveyor.settings.Settings;
import dev.skysurveyor.settings.SettingsList.Preview;
import dev.skysurveyor.settings.SummarySettings;
import dev.skysurveyor.settings.SummarySettings.Position;

import static dev.skysurveyor.screen.ScreenParts.label;

// help pane, drawn w/ the real renderers and made up numbers
final class SettingsPreviews {
	// display choices
	private static final float TITLE_SIZE = 0.5f;
	private static final int CHAT_BACK = 0x60000000;
	private static final int TITLE_BACK = 0x40000000;
	private static final int WAYPOINT_FILL = 0x30000000;

	interface Painter {
		void paint(OwoUIGraphics graphics, int x, int y);
	}

	static final class Drawn extends BaseUIComponent {
		private final Painter painter;

		Drawn(int width, int height, Painter painter) {
			this.painter = painter;
			horizontalSizing(Sizing.fixed(width));
			verticalSizing(Sizing.fixed(height));
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			painter.paint(graphics, x, y);
		}
	}

	private SettingsPreviews() {
	}

	static List<UIComponent> of(Preview preview, Settings settings, Font font, int width) {
		List<UIComponent> out = new ArrayList<>();
		switch (preview) {
			case TOAST -> toast(out, settings.summary(), font, width);
			case TITLE -> title(out, settings.entryTitle(), font, width);
			case WAYPOINT -> waypoint(out, settings.waypoints().isFossil(), settings.waypoints().colorRgb(), font);
		}
		return out;
	}

	private static void caption(List<UIComponent> out, String text, int width) {
		LabelComponent caption = label("§8" + text);
		caption.horizontalSizing(Sizing.fixed(width));
		caption.margins(Insets.top(out.isEmpty() ? 6 : 8));
		out.add(caption);
	}

	private static void toast(List<UIComponent> out, SummarySettings summary, Font font, int width) {
		if (summary.show().toast()) {
			caption(out, "The card, " + (summary.position() == Position.MOVABLE ? "where you put it" : "top right") + ", for "
					+ Setting.Number.format(summary.seconds()) + "s", width);
			ToastText text = ToastText.example();
			ToastRenderer.Size size = ToastRenderer.size(font, text, summary.style());
			float scale = Math.min(1, width / (float) size.width());
			out.add(new Drawn(Math.round(size.width() * scale), Math.round(size.height() * scale), (graphics, x, y) -> {
				graphics.pose().pushMatrix();
				graphics.pose().translate(x, y);
				graphics.pose().scale(scale, scale);
				ToastRenderer.draw(graphics, font, text, summary.style(), size, 1);
				graphics.pose().popMatrix();
			}));
		}
		if (summary.show().chat()) {
			caption(out, "In chat", width);
			List<String> lines = SummaryText.example();
			int widest = 0;
			for (String line : lines) widest = Math.max(widest, font.width(line));
			int pad = 3;
			float scale = Math.min(1, (width - 2 * pad) / (float) widest);
			int lineHeight = font.lineHeight;
			int height = Math.round(lines.size() * lineHeight * scale) + 2 * pad;
			out.add(new Drawn(width, height, (graphics, x, y) -> {
				graphics.fill(x, y, x + width, y + height, CHAT_BACK);
				graphics.pose().pushMatrix();
				graphics.pose().translate(x + pad, y + pad);
				graphics.pose().scale(scale, scale);
				for (int i = 0; i < lines.size(); i++) graphics.text(font, lines.get(i), 0, i * lineHeight, 0xFFFFFFFF, true);
				graphics.pose().popMatrix();
			}));
		}
		if (!summary.show().toast() && !summary.show().chat()) caption(out, "Nothing shows. The journal still saves the shaft.", width);
	}

	private static void title(List<UIComponent> out, EntryTitleSettings shown, Font font, int width) {
		if (!shown.isShow()) {
			caption(out, "No title.", width);
			return;
		}
		caption(out, "On screen for " + Setting.Number.format(shown.seconds()) + "s", width);
		EntryTitle.Text text = EntryTitle.example(shown.isVerdict());
		int pad = 6;
		int height = Math.round(EntryTitleOverlay.height(font, text, TITLE_SIZE)) + 2 * pad;
		float top = (font.lineHeight + 1) * 3 * TITLE_SIZE;
		out.add(new Drawn(width, height, (graphics, x, y) -> {
			graphics.fill(x, y, x + width, y + height, TITLE_BACK);
			EntryTitleOverlay.draw(graphics, font, text, x + width / 2f, y + pad + top, width - 2 * pad, TITLE_SIZE);
		}));
	}

	private static void waypoint(List<UIComponent> out, boolean on, int rgb, Font font) {
		if (!on) return;
		caption(out, "In the shaft", 200);
		String label = FossilWaypoint.label(23);
		int boxW = 40;
		int boxH = 24;
		int width = Math.max(boxW, font.width(label)) + 4;
		int height = 10 + boxH + 2;
		out.add(new Drawn(width, height, (graphics, x, y) -> {
			int color = 0xFF000000 | rgb;
			graphics.text(font, label, x + (width - font.width(label)) / 2, y, color, true);
			int bx = x + (width - boxW) / 2;
			int by = y + 11;
			graphics.fill(bx, by, bx + boxW, by + boxH, WAYPOINT_FILL | rgb);
			graphics.fill(bx, by, bx + boxW, by + 1, color);
			graphics.fill(bx, by + boxH - 1, bx + boxW, by + boxH, color);
			graphics.fill(bx, by, bx + 1, by + boxH, color);
			graphics.fill(bx + boxW - 1, by, bx + boxW, by + boxH, color);
		}));
	}
}
