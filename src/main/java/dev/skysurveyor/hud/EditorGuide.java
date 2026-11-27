package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import dev.skysurveyor.journal.Breaks;
import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.screen.ScreenParts;

final class EditorGuide {
	// display choice, fits beside panel at 960x540
	private static final int CARD_WIDTH = 330;
	// display choice
	private static final int CARD_WIDE = 400;
	// display choice
	private static final int GAP = 3;
	// display choice
	private static final int SCROLL_STEP = 12;
	// short on purpose, advanced syntax lives on the Advanced page
	private static final String TEXT = "§eHow the HUD editor works\n"
			+ "§fBoxes §7Drag a box to move it. It snaps to edges and other boxes, Ctrl places it freely. Drop it right under or "
			+ "next to another box and it sticks to it. Scroll on a box to resize it, arrow keys nudge it. Click a box again to "
			+ "pick the one under it.\n"
			+ "§fLines §7Click a box, then + Add line. Hover a value to see what it counts. Click a line to change its label, "
			+ "period, format and colours. Drag its grip to move it, untick it to hide it.\n"
			+ "§fPeriods §7This shaft is the shaft you're in, or the last one. Session ends after "
			+ Journal.SESSION_GAP_MILLIS / 60_000 + " min without mining or shafting. Per hour leaves out breaks over "
			+ Breaks.BREAK_MILLIS / 60_000 + " min. All-time is your whole journal.\n"
			+ "§fShow in §7Each box shows in shafts, in the Mines, elsewhere, or any mix. Boxes that don't show where you "
			+ "stand are dimmed and sit under the ones that do.\n"
			+ "§fMore §7A line can show a bar or chart (Show), have its own colours, or be typed by hand with maths and "
			+ "colour rules (Mode, Advanced).\n"
			+ "§fSaving §7Changes save by themselves. Ctrl+Z undoes, Esc steps back. + Preset adds a ready-made box, Reset all "
			+ "brings back the defaults.";

	private final EditHudScreen screen;
	private boolean open;
	// null = gap before a heading
	private List<FormattedCharSequence> lines;
	private int height;
	private int scroll;
	// last drawn card, for the wheel
	private int x;
	private int y;
	private int w;
	private int h;
	// what the lines were wrapped for
	private int wrap;
	private boolean beside;

	EditorGuide(EditHudScreen screen) {
		this.screen = screen;
	}

	boolean isOpen() {
		return open;
	}

	void open(boolean open) {
		this.open = open;
		scroll = 0;
		h = 0;
	}

	// kept while a tip hides it
	boolean over(double mouseX, double mouseY) {
		return h > 0 && mouseX >= x - 4 && mouseX < x + w + 4 && mouseY >= y - 4 && mouseY < y + h + 4;
	}

	void scroll(double vertical) {
		scroll = EditorFit.clampScroll(scroll - (int) Math.round(vertical * SCROLL_STEP), height, h);
	}

	// panel null = no panel open
	void draw(GuiGraphicsExtractor graphics, UIComponent toolbar, UIComponent panel) {
		Font font = screen.getFont();
		int width = screen.width;
		int screenHeight = screen.height;
		boolean toolbarTop = toolbar.y() < screenHeight / 2;
		int top = (toolbarTop ? toolbar.y() + toolbar.height() + 2 : 0) + ScreenParts.TIP_MARGIN;
		int bottom = (toolbarTop ? screenHeight : toolbar.y() - 2) - ScreenParts.TIP_MARGIN;
		boolean panelRight = panel != null && panel.x() > width / 2;
		int px = panel == null ? 0 : panelRight ? panel.x() : panel.x() + panel.width();
		int room = panel == null ? 0 : (panelRight ? px : width - px) - 8 - 2 * ScreenParts.TIP_MARGIN - 6;
		int full = width - 2 * ScreenParts.TIP_MARGIN - 8;
		int fits = bottom - top - 8;
		int key = Objects.hash(width, screenHeight, px, panelRight);
		if (lines == null || wrap != key) {
			wrap = key;
			beside = false;
			List<Integer> wraps = new ArrayList<>();
			for (int ww : new int[] {CARD_WIDTH, CARD_WIDE}) {
				if (ww <= room) wraps.add(-ww);
			}
			if (room > 0 && room < CARD_WIDE) wraps.add(-room);
			wraps.add(Math.min(CARD_WIDTH, full));
			wraps.add(Math.min(CARD_WIDE, full));
			wraps.add(full);
			for (int ww : wraps) {
				wrap(font, Math.abs(ww));
				beside = ww < 0;
				if (height <= fits) break;
			}
		}
		int visible = Math.max(10, Math.min(height, fits));
		boolean scrolls = visible < height;
		scroll = EditorFit.clampScroll(scroll, height, visible);
		int textWidth = 0;
		for (FormattedCharSequence line : lines) {
			if (line != null) textWidth = Math.max(textWidth, font.width(line));
		}
		// room for the scrollbar
		int cardW = textWidth + (scrolls ? 4 : 0);
		int cardX = ScreenParts.clamp(!beside ? (width - cardW) / 2 : panelRight ? px - 8 - cardW : px + 8, cardW, width);
		int cardY = ScreenParts.clamp(toolbarTop ? top : Math.max(top, bottom - visible), visible, screenHeight);
		graphics.nextStratum();
		TooltipRenderUtil.extractTooltipBackground(graphics, cardX, cardY, cardW, visible, null);
		graphics.enableScissor(cardX, cardY, cardX + cardW, cardY + visible);
		int lineY = cardY - scroll;
		for (FormattedCharSequence line : lines) {
			if (line == null) {
				lineY += GAP;
				continue;
			}
			if (lineY + 10 > cardY && lineY < cardY + visible) graphics.text(font, line, cardX, lineY, 0xFFFFFFFF, true);
			lineY += 10;
		}
		graphics.disableScissor();
		if (scrolls) {
			// like the panel's scrollbar
			int thumb = Math.max(8, visible * visible / height);
			int thumbY = cardY + (visible - thumb) * scroll / (height - visible);
			graphics.fill(cardX + cardW - 2, thumbY, cardX + cardW, thumbY + thumb, 0x80FFFFFF);
		}
		x = cardX;
		y = cardY;
		w = cardW;
		h = visible;
	}

	private void wrap(Font font, int width) {
		List<FormattedCharSequence> wrapped = new ArrayList<>();
		int total = 0;
		String[] paragraphs = TEXT.split("\n");
		for (int i = 0; i < paragraphs.length; i++) {
			if (i > 0) {
				wrapped.add(null);
				total += GAP;
			}
			for (FormattedCharSequence line : font.split(Component.literal(paragraphs[i]), width)) {
				wrapped.add(line);
				total += 10;
			}
		}
		lines = wrapped;
		height = total;
	}
}
