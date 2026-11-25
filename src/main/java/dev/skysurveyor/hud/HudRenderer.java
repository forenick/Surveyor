package dev.skysurveyor.hud;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import dev.skysurveyor.hud.HudLayout.Box;

public final class HudRenderer {
	static final int PADDING = 3;
	private static final int LINE_GAP = 1;
	private static final int HINT_GAP = 2;
	private static final int DARK = 0x80000000;
	// vanilla tooltip sprites overhang the text by 12px
	static final int FRAME_OVERHANG = 12;
	private static final String HINT = "§7(/sv hud)";

	public record Bounds(int x, int y, int width, int height) {
		public boolean contains(double px, double py) {
			return px >= x && px < x + width && py >= y && py < y + height;
		}

		public long overlapArea(Bounds o) {
			long w = Math.min(x + width, o.x + o.width) - Math.max(x, o.x);
			long h = Math.min(y + height, o.y + o.height) - Math.max(y, o.y);
			return w <= 0 || h <= 0 ? 0 : w * h;
		}

		public Bounds grown(int by) {
			return new Bounds(x - by, y - by, width + 2 * by, height + 2 * by);
		}
	}

	// a box measured once: text + right column widths per row, inner size w/ padding + hint, unscaled.
	// rim here so bounds + draw use the same one after a pack reload
	public record Measured(List<HudRow> rows, int[] textWidths, int[] rightWidths, int width, int height, float hintScale,
			int hintWidth, FrameRim.Rim rim) {
	}

	private HudRenderer() {
	}

	public static Measured measure(Font font, Box box, List<HudRow> rows) {
		float hintScale = hintScale(box);
		int hintWidth = hintWidth(font, box, hintScale);
		int[] textWidths = new int[rows.size()];
		int[] rightWidths = new int[rows.size()];
		int width = Math.max(HudRow.minWidth(rows), hintWidth);
		for (int i = 0; i < rows.size(); i++) {
			HudRow row = rows.get(i);
			textWidths[i] = row.text() == null ? 0 : font.width(row.text());
			int text = row.text() == null ? 0 : row.indent() + textWidths[i];
			if (row.columns() && !row.right().isEmpty()) {
				rightWidths[i] = font.width(row.right());
				text += HudRow.COLUMN_GAP + rightWidths[i];
			}
			width = Math.max(width, text);
		}
		int height = HudRow.height(rows, font.lineHeight, LINE_GAP) + 2 * PADDING + hintHeight(font, box, hintScale);
		return new Measured(rows, textWidths, rightWidths, width + 2 * PADDING, height, hintScale, hintWidth, rim(box));
	}

	public static Bounds bounds(Font font, Box box, List<HudRow> rows, int screenWidth, int screenHeight) {
		return bounds(measure(font, box, rows), box, screenWidth, screenHeight);
	}

	// own anchors only, stuck boxes go through HudPlace
	public static Bounds bounds(Measured measured, Box box, int screenWidth, int screenHeight) {
		HudPlace.Size size = size(measured, box);
		return new Bounds(box.anchorX.start(box.offsetX, size.width(), screenWidth),
				box.anchorY.start(box.offsetY, size.height(), screenHeight), size.width(), size.height());
	}

	// on screen, w/ scale + frame rim. in bounds, so snap + margin use it
	public static HudPlace.Size size(Measured measured, Box box) {
		FrameRim.Rim rim = measured.rim();
		return new HudPlace.Size(Math.round((measured.width() + rim.width()) * box.scale),
				Math.round((measured.height() + rim.height()) * box.scale));
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Box box, List<HudRow> rows, Bounds bounds) {
		draw(graphics, font, box, measure(font, box, rows), bounds);
	}

	public static void draw(GuiGraphicsExtractor graphics, Font font, Box box, Measured measured, Bounds bounds) {
		List<HudRow> rows = measured.rows();
		float hintScale = measured.hintScale();
		int hintWidth = measured.hintWidth();
		int width = measured.width();
		int height = measured.height();
		graphics.pose().pushMatrix();
		graphics.pose().translate(bounds.x(), bounds.y());
		graphics.pose().scale(box.scale, box.scale);
		FrameRim.Rim rim = measured.rim();
		graphics.pose().translate(rim.left(), rim.top());
		switch (box.background) {
			case DARK -> graphics.fill(0, 0, width, height, DARK);
			case FRAME -> drawFrame(graphics, width, height, -1);
			case NONE -> {
			}
		}
		int textWidth = width - 2 * PADDING;
		int y = PADDING;
		for (int i = 0; i < rows.size(); i++) {
			HudRow row = rows.get(i);
			int top = y;
			if (row.rule()) {
				graphics.fill(PADDING, y + HudRow.RULE_ABOVE, PADDING + textWidth, y + HudRow.RULE_ABOVE + 1, HudRow.RULE);
				y += HudRow.RULE_HEIGHT;
			}
			if (row.columns()) {
				drawColumns(graphics, font, row, measured.rightWidths()[i], y, textWidth);
			} else if (row.text() != null) {
				drawLine(graphics, font, row.text(), PADDING + (int) alignX(box.align, textWidth, measured.textWidths()[i]), y);
			}
			if (row.graph() != null) {
				drawGraph(graphics, row.graph(), box.align, PADDING, y + (row.text() == null ? 0 : font.lineHeight) + HudGraph.ABOVE, textWidth);
			}
			y = top + row.height(font.lineHeight);
			if (i < rows.size() - 1) y += row.gapBelow(LINE_GAP);
		}
		if (hintWidth > 0) {
			float x = PADDING + alignX(box.align, textWidth, hintWidth);
			graphics.pose().pushMatrix();
			graphics.pose().translate(x, y + HINT_GAP);
			graphics.pose().scale(hintScale, hintScale);
			graphics.text(font, HINT, 0, 0, 0xFFFFFFFF, true);
			graphics.pose().popMatrix();
		}
		graphics.pose().popMatrix();
	}

	private static float alignX(HudLayout.Align align, int room, float width) {
		return switch (align) {
			case LEFT -> 0;
			case CENTER -> (room - width) / 2;
			case RIGHT -> room - width;
		};
	}

	private static void drawColumns(GuiGraphicsExtractor graphics, Font font, HudRow row, int rightWidth, int y, int textWidth) {
		if (row.text() != null) drawLine(graphics, font, row.text(), PADDING + row.indent(), y);
		if (!row.right().isEmpty()) drawLine(graphics, font, row.right(), PADDING + textWidth - rightWidth, y);
	}

	private static void drawGraph(GuiGraphicsExtractor graphics, HudGraph graph, HudLayout.Align align, int x, int y, int width) {
		switch (graph) {
			case HudGraph.Bar bar -> {
				int filled = bar.filled(width);
				graphics.fill(x, y, x + width, y + bar.height(), HudGraph.Bar.TRACK);
				if (filled > 0) graphics.fill(x, y, x + filled, y + bar.height(), bar.argb());
			}
			case HudGraph.Chart chart -> {
				int w = chart.width();
				int left = x + (int) alignX(align, width, w);
				int bottom = y + chart.height();
				for (int i = 0; i < chart.columns().size(); i++) {
					int cx = left + i * (HudGraph.Chart.COLUMN + HudGraph.Chart.GAP);
					graphics.fill(cx, bottom - chart.columnHeight(i), cx + HudGraph.Chart.COLUMN, bottom, chart.columns().get(i).argb());
				}
				Integer lineY = chart.lineY();
				if (lineY == null) return;
				for (int dx = 0; dx < w; dx += 2 * HudGraph.Chart.DASH) {
					graphics.fill(left + dx, y + lineY, left + Math.min(dx + HudGraph.Chart.DASH, w), y + lineY + 1, HudGraph.Chart.LINE);
				}
			}
			case HudGraph.Line chart -> {
				int w = HudGraph.Line.WIDTH;
				int left = x + (int) alignX(align, width, w);
				Integer usual = chart.usualY();
				if (usual != null) {
					for (int dx = 0; dx < w; dx += 2 * HudGraph.Chart.DASH) {
						graphics.fill(left + dx, y + usual, left + Math.min(dx + HudGraph.Chart.DASH, w), y + usual + 1, HudGraph.Chart.LINE);
					}
				}
				// 1 px line, steps filled so it never breaks
				int last = chart.yAt(0);
				for (int dx = 0; dx < w; dx++) {
					int now = chart.yAt(dx);
					graphics.fill(left + dx, y + Math.min(last, now), left + dx + 1, y + Math.max(last, now) + 1, chart.argb());
					last = now;
				}
			}
		}
	}

	private static FrameRim.Rim rim(Box box) {
		return box.background == HudLayout.Background.FRAME ? FrameRim.get() : FrameRim.NONE;
	}

	// not via TooltipRenderUtil, iconographic recolours that w/ the last hovered item
	static void drawFrame(GuiGraphicsExtractor graphics, int width, int height, int color) {
		int x = PADDING - FRAME_OVERHANG;
		int w = width - 2 * PADDING + 2 * FRAME_OVERHANG;
		int h = height - 2 * PADDING + 2 * FRAME_OVERHANG;
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FrameRim.BACKGROUND, x, x, w, h, color);
		graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FrameRim.BORDER, x, x, w, h, color);
	}

	private static void drawLine(GuiGraphicsExtractor graphics, Font font, String line, int x, int y) {
		if (!line.contains(HudBoxText.HIDE_START)) {
			graphics.text(font, line, x, y, 0xFFFFFFFF, true);
			return;
		}
		List<String> pieces = HudBoxText.pieces(line);
		for (int i = 0; i < pieces.size(); i++) {
			if (i % 2 == 0) graphics.text(font, pieces.get(i), x, y, 0xFFFFFFFF, true);
			x += font.width(pieces.get(i));
		}
	}

	// ~0.6x, rounded so font pixels stay whole screen pixels
	private static float hintScale(Box box) {
		if (!box.hint()) return 0;
		float pixels = Minecraft.getInstance().getWindow().getGuiScale() * box.scale;
		if (pixels <= 0) return 0.5f;
		return Math.max(1, Math.round(pixels * 0.6f)) / pixels;
	}

	private static int hintWidth(Font font, Box box, float scale) {
		return box.hint() ? (int) Math.ceil(font.width(HINT) * scale) : 0;
	}

	// below the last line, font height w/o the gap under it
	private static int hintHeight(Font font, Box box, float scale) {
		return box.hint() ? HINT_GAP + (int) Math.ceil((font.lineHeight - 1) * scale) : 0;
	}
}
