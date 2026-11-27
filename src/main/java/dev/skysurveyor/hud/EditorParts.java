package dev.skysurveyor.hud;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.UISounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import dev.skysurveyor.hud.HudLayout.Align;
import dev.skysurveyor.screen.HoverFlow;
import dev.skysurveyor.screen.PackButton;
import dev.skysurveyor.screen.ScreenParts;
import dev.skysurveyor.screen.Switch;

import static dev.skysurveyor.screen.ScreenParts.label;

final class EditorParts {
	// opaque, boxes bled through
	static final int PANEL_FILL = 0xFF141414;
	static final int PANEL_OUTLINE = 0x30FFFFFF;
	static final Surface PANEL = Surface.flat(PANEL_FILL).and(Surface.outline(PANEL_OUTLINE));
	static final Surface DIVIDER = (graphics, c) -> graphics.fill(c.x(), c.y(), c.x() + c.width(), c.y() + 1, 0x1CFFFFFF);
	// fits 480x270
	static final int BUTTON = 16;
	static final int CHOICE = 14;
	static final int MENU_SWATCH = 10;
	// display choice
	static final int TIP_WIDTH = 240;
	static final int HOVER = 0x20FFFFFF;
	static final int OUTLINE_SELECTED = 0xFFFFFF55;
	static final int RANGE_TEXT = 0xAAAAAA;
	static final String[] COLOR_NAMES = {"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red",
			"Dark Purple", "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};
	// pixel icons, same in every font
	static final String[] RESET_ICON = {
			"..XXXX.X",
			".X....XX",
			"X....XXX",
			"X.......",
			"X.......",
			"X......X",
			".X....X.",
			"..XXXX.."};
	static final String[] PENCIL_ICON = {
			"......X.",
			".....XXX",
			"....XXX.",
			"...XXX..",
			"..XXX...",
			".XXX....",
			".XX.....",
			"X......."};
	private static final String[] UNDO_ICON = {
			"...X....",
			"..XX....",
			".XXXXXX.",
			"..XX...X",
			"...X...X",
			".......X",
			"....XXX."};
	private static final Identifier REMOVE_ICON = Identifier.withDefaultNamespace("player_list/remove_player");
	private static final Identifier CHECK_ICON = icon("check_icon");

	interface Icon {
		void draw(GuiGraphicsExtractor graphics, int x, int y, int height);
	}

	// no font glyph, caxton safe
	static final Icon REMOVE = (graphics, x, y, h) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, REMOVE_ICON, x,
			y + (h - 7) / 2, 8, 7);

	private EditorParts() {
	}

	static List<FormattedCharSequence> tipLines(Font font, String text) {
		return font.split(Component.literal(text), TIP_WIDTH);
	}

	static Icon pixels(String[] icon) {
		return (graphics, x, y, h) -> drawIcon(graphics, icon, x, y + (h - icon.length) / 2, false, 0xFFFFFFFF);
	}

	static void drawIcon(GuiGraphicsExtractor graphics, String[] icon, int x0, int y0, boolean mirror, int color) {
		for (int row = 0; row < icon.length; row++) {
			for (int col = 0; col < 8; col++) {
				if (icon[row].charAt(col) != 'X') continue;
				int x = x0 + (mirror ? 7 - col : col);
				graphics.fill(x, y0 + row, x + 1, y0 + row + 1, color);
			}
		}
	}

	static ButtonComponent button(String text, Consumer<ButtonComponent> action) {
		ButtonComponent button = PackButton.button(Component.literal(text), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	// owo draws the msg too, so empty + narrator name
	static ButtonComponent drawnButton(String name, Consumer<ButtonComponent> action) {
		ButtonComponent button = new DrawnButton(ScreenParts.strip(name), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	// pixels bc fonts ↶ is tiny
	static ButtonComponent undoButton(boolean redo, Consumer<ButtonComponent> action) {
		ButtonComponent button = drawnButton(redo ? "Redo" : "Undo", action);
		button.horizontalSizing(Sizing.fixed(BUTTON + 4));
		button.renderer((graphics, b, delta) -> {
			PackButton.RENDERER.draw(graphics, b, delta);
			drawIcon(graphics, UNDO_ICON, b.getX() + (b.getWidth() - 8) / 2, b.getY() + (b.getHeight() - UNDO_ICON.length) / 2,
					redo, b.isActive() ? 0xFFFFFFFF : 0xFF707070);
		});
		return button;
	}

	// text "" = icon only
	static ButtonComponent iconTextButton(Font font, Icon icon, String text, String name, Consumer<ButtonComponent> action) {
		ButtonComponent button = drawnButton(text.isEmpty() ? name : text, action);
		button.horizontalSizing(Sizing.fixed(text.isEmpty() ? CHOICE + 2 : 5 + 8 + 4 + font.width(text) + 5));
		button.renderer((graphics, b, delta) -> {
			PackButton.RENDERER.draw(graphics, b, delta);
			int x = text.isEmpty() ? b.getX() + (b.getWidth() - 8) / 2 : b.getX() + 5;
			icon.draw(graphics, x, b.getY(), b.getHeight());
			if (!text.isEmpty()) graphics.text(font, text, x + 12, b.getY() + (b.getHeight() - 8) / 2, 0xFFFFFFFF, true);
		});
		return button;
	}

	private static final class DrawnButton extends PackButton {
		private final String name;

		DrawnButton(String name, Consumer<ButtonComponent> action) {
			super(Component.empty(), action);
			this.name = name;
		}

		@Override
		protected MutableComponent createNarrationMessage() {
			return wrapDefaultNarrationMessage(Component.literal(name));
		}
	}

	static FlowLayout spacer() {
		return UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1));
	}

	static LabelComponent section(String text) {
		LabelComponent label = label("§7" + text);
		label.margins(Insets.top(4));
		return label;
	}

	static FlowLayout heading(String text) {
		FlowLayout heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		heading.surface(DIVIDER);
		heading.padding(Insets.top(4));
		heading.margins(Insets.top(4));
		heading.verticalAlignment(VerticalAlignment.CENTER);
		heading.child(label("§f" + text));
		return heading;
	}

	// switch + its name, the name clicks too
	static FlowLayout toggle(String text, boolean on, Consumer<Boolean> set) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.vertical(1));
		Switch toggle = new Switch(on, false, set::accept);
		row.child(toggle);
		LabelComponent name = label(text);
		name.cursorStyle(CursorStyle.HAND);
		name.mouseDown().subscribe((click, doubled) -> {
			if (click.button() != 0) return false;
			toggle.toggle();
			return true;
		});
		row.child(name);
		return row;
	}

	static Icon texture(Identifier texture) {
		return (graphics, x, y, h) -> graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y + (h - 8) / 2, 0, 0, 8, 8, 8, 8);
	}

	static Identifier icon(String name) {
		return Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/" + name + ".png");
	}

	// small box for the dense line list, green tick = on, never an X
	static final class TickBox extends BaseUIComponent {
		static final int SIZE = 10;
		private final Consumer<Boolean> changed;
		private boolean on;

		TickBox(boolean on, Consumer<Boolean> changed) {
			this.on = on;
			this.changed = changed;
			cursorStyle(CursorStyle.HAND);
			sizing(Sizing.fixed(SIZE), Sizing.fixed(SIZE));
		}

		@Override
		public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
			if (click.button() != 0) return super.onMouseDown(click, doubled);
			on = !on;
			UISounds.playButtonSound();
			changed.accept(on);
			super.onMouseDown(click, doubled);
			return true;
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			graphics.fill(x, y, x + SIZE, y + SIZE, hovered ? 0xFFFFFFFF : 0xFF8B8B8B);
			graphics.fill(x + 1, y + 1, x + SIZE - 1, y + SIZE - 1, 0xFF000000);
			if (on) graphics.blit(RenderPipelines.GUI_TEXTURED, CHECK_ICON, x + 1, y + 1, 0, 0, 8, 8, 8, 8);
		}
	}

	// left click only
	static FlowLayout crumb(String text, Runnable action) {
		FlowLayout crumb = new HoverFlow(Sizing.content(), Sizing.content(), HOVER);
		crumb.padding(Insets.horizontal(1));
		crumb.child(label("§7" + text));
		crumb.mouseDown().subscribe((click, doubled) -> {
			if (click.button() != 0) return false;
			action.run();
			return true;
		});
		return crumb;
	}

	static LabelComponent menuTitle(String text) {
		LabelComponent title = label("§7" + text);
		title.margins(Insets.of(2, 1, 2, 2));
		return title;
	}

	static FlowLayout menuEntry(String text, Runnable action) {
		HoverRow row = new HoverRow();
		row.horizontalSizing(Sizing.content());
		row.padding(Insets.of(1, 1, 3, 6));
		row.child(label(text));
		row.mouseDown().subscribe((click, doubled) -> {
			action.run();
			return true;
		});
		return row;
	}

	static String name(Enum<?> option) {
		String lower = option.name().toLowerCase(Locale.ROOT);
		return option == Align.CENTER ? "Centre" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}

	// name, then what it adds in grey, wrapped
	static FlowLayout menuEntry(String text, String note, int width, Runnable action) {
		HoverFlow entry = new HoverFlow(Sizing.fixed(width), Sizing.content(), HOVER, FlowLayout.Algorithm.VERTICAL);
		entry.padding(Insets.of(2, 2, 3, 3));
		entry.child(label(text));
		LabelComponent grey = label("§7" + note);
		grey.horizontalSizing(Sizing.fixed(width - 6));
		entry.child(grey);
		entry.mouseDown().subscribe((click, doubled) -> {
			action.run();
			return true;
		});
		return entry;
	}

	static final class HoverRow extends HoverFlow {
		HoverRow() {
			super(Sizing.fill(100), Sizing.content(), HOVER);
			gap(2);
			verticalAlignment(VerticalAlignment.CENTER);
			padding(Insets.vertical(1));
		}
	}

	static final class Chip extends FlowLayout {
		// faint edge like the calculator's chips, display choice
		static final int PAD = 3;
		private static final int EDGE = 0x30FFFFFF;
		private final LabelComponent label;
		private boolean outlined;

		Chip(String text, int color, Runnable action) {
			super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);
			cursorStyle(CursorStyle.HAND);
			label = label(text);
			color(color);
			child(label);
			mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) return false;
				action.run();
				return true;
			});
		}

		void color(int color) {
			label.color(Color.ofRgb(color));
		}

		// reads as a button
		Chip outlined() {
			outlined = true;
			padding(Insets.of(2, 1, PAD, PAD));
			return this;
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
			if (outlined) graphics.drawRectOutline(x, y, width, height, EDGE);
			if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, HOVER);
		}
	}
}
