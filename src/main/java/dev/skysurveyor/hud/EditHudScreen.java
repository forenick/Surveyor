package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.DiscreteSliderComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.TextAreaComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.joml.Vector2i;
import org.lwjgl.glfw.GLFW;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.hud.HudLayout.Align;
import dev.skysurveyor.hud.HudLayout.Anchor;
import dev.skysurveyor.hud.HudLayout.Background;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.screen.PackButton;
import dev.skysurveyor.screen.SafeLabel;
import dev.skysurveyor.settings.SummarySettings;

public final class EditHudScreen extends BaseOwoScreen<FlowLayout> {
	// opaque, boxes behind it bled through the text
	private static final int PANEL_FILL = 0xFF141414;
	private static final int PANEL_OUTLINE = 0x30FFFFFF;
	private static final Surface PANEL = Surface.flat(PANEL_FILL).and(Surface.outline(PANEL_OUTLINE));
	private static final Surface MENU = Surface.flat(0xF0101010).and(Surface.outline(0xFFA0A0A0));
	// no outline, so it doesnt look like a field
	private static final Surface PREVIEW = Surface.flat(0x8C000000)
			.and((graphics, c) -> graphics.fill(c.x(), c.y(), c.x() + 2, c.y() + c.height(), 0x40FFFFFF));
	private static final Surface DIVIDER = (graphics, c) -> graphics.fill(c.x(), c.y(), c.x() + c.width(), c.y() + 1,
			0x1CFFFFFF);
	// fits 480x270, grows on wide screens (gui scale 2)
	private static final int PANEL_MIN = 204;
	private static final int PANEL_MAX = 280;
	private static final int ROW_LABEL = 62;
	private static final int KEY = 38;
	// action buttons / choices + line rows. fits 480x270
	private static final int BUTTON = 16;
	private static final int CHOICE = 14;
	private static final int MENU_SWATCH = 10;
	private static final int TIP_WIDTH = 240;
	// tooltip frame sticks out ~3px
	private static final int TIP_MARGIN = 5;
	private static final Identifier REMOVE_ICON = Identifier.withDefaultNamespace("player_list/remove_player");
	private static final int OUTLINE = 0x70FFFFFF;
	private static final int OUTLINE_SELECTED = 0xFFFFFF55;
	// light, so the preview stays readable
	private static final int HIDDEN_SHADE = 0x60000000;
	private static final int GUIDE = 0xFFFF55FF;
	private static final int DROP_LINE = 0xFFFFFF55;
	private static final int DRAGGED_LINE = 0x30FFFFFF;
	private static final int HOVER = 0x20FFFFFF;
	// box name keeps at least this much before the page name gets cut
	private static final int CRUMB_MIN = 40;
	private static final Surface GRIP = (graphics, c) -> {
		int x = c.x() + (c.width() - 3) / 2;
		int y = c.y() + (c.height() - 5) / 2;
		for (int dx = 0; dx <= 2; dx += 2) {
			for (int dy = 0; dy <= 4; dy += 2) graphics.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, 0xFFA0A0A0);
		}
	};
	// batches scroll/nudge spam into 1 undo step
	private static final long SETTLE_MILLIS = 400;
	// 8 wide pixel icons, same in every font
	private static final String[] RESET_ICON = {
			"..XXXX.X",
			".X....XX",
			"X....XXX",
			"X.......",
			"X.......",
			"X......X",
			".X....X.",
			"..XXXX.."};
	private static final String[] PENCIL_ICON = {
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
	// also the size slider step
	private static final float SCALE_STEP = 0.1f;
	private static final int PADDING = 4;
	private static final int[][] TOOLBAR_SPOTS = {{50, 0}, {50, 100}, {0, 0}, {100, 0}, {0, 100}, {100, 100}};
	private static final Anchor[][] NEW_BOX_SPOTS = {{Anchor.START, Anchor.MIDDLE}, {Anchor.END, Anchor.MIDDLE},
			{Anchor.START, Anchor.END}, {Anchor.END, Anchor.END}, {Anchor.MIDDLE, Anchor.END}, {Anchor.MIDDLE, Anchor.MIDDLE}};

	private static final int[] COLORS = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	private static final String[] COLOR_NAMES = {"Black", "Dark Blue", "Dark Green", "Dark Aqua", "Dark Red",
			"Dark Purple", "Gold", "Gray", "Dark Gray", "Blue", "Green", "Aqua", "Red", "Light Purple", "Yellow", "White"};
	private static final String HIDDEN_NOTE = "hidden";
	// display choice, fits beside the panel at 960x540
	private static final int HELP_CARD_WIDTH = 330;
	private static final String HELP_CARD = "§eHow the HUD editor works\n"
			+ "§fBoxes §7Drag a box to move it. It snaps to the screen edges and other boxes, hold Ctrl to place it "
			+ "freely. Arrow keys nudge the selected box, scrolling on it resizes it. Click a box to edit it.\n"
			+ "§fLines §7Add line shows every value, hover one to see what it counts. Click a line to change its "
			+ "label, range, format and colours. Drag the grip to move it, untick to hide it.\n"
			+ "§fRanges §7Shaft is the shaft you're in, or the last one while you're outside. A session ends after 15 "
			+ "min without mining in the Dwarven Mines or shafting. Per hour is per hour of this session. "
			+ "All-time is your whole journal.\n"
			+ "§fColours §7A box has a title, label and value colour, a line can have its own. A small square in the "
			+ "line list means the line has its own.\n"
			+ "§fShow in §7Picks where a box shows: in shafts, in the Mines, elsewhere. A box that wouldn't show "
			+ "where you are now says \"hidden here\".\n"
			+ "§fAdvanced §7On a line's page, Mode Advanced: type the whole line with &e colours, {values} and maths like "
			+ "{coins / shafts}, plus colour rules like {cold} <= -80 &c. The list beside it types them for you.\n"
			+ "§fPresets §7+ Preset adds a ready-made box, Reset brings back the three default boxes.\n"
			+ "§fSaving §7Changes save by themselves. Ctrl+Z undo, Ctrl+Shift+Z redo, Esc steps back.";
	private static final String TEXT_HINT = "§8&e colours, {values}, maths like {coins / shafts}. Click the list to type them.";
	private static final String RULES_HELP = "One rule a line: a {value}, a compare, a number or {value}, a colour. F.e. "
			+ "{cold} <= -80 &c. A rule colours that value in the text, the first match wins. Maths works on both sides: "
			+ "{coins / shafts} >= 2M &a. Numbers: 1:30, 90s, 2m next to a time, 2.5M, 1,5M, 150k.";
	private static final int TEXT_LIMIT = 256;
	private static final int RULES_LIMIT = 512;
	// value list beside the panel, display choice
	private static final int LIST_MIN = 170;
	private static final int LIST_MAX = 190;
	private static final int RANGE_TEXT = 0xAAAAAA;
	private static final int FORMAT_TEXT = 0x6A6A6A;
	private static final int FORMAT_OFF = 0x333333;
	// right of the mouse if it fits, else left, always on screen
	private static final ClientTooltipPositioner FIT = (screenW, screenH, x, y, w, h) -> new Vector2i(
			clamp(x + 12 + w <= screenW - TIP_MARGIN ? x + 12 : x - 12 - w, w, screenW),
			clamp(y - 12, h, screenH));

	private final Screen parent;
	private final LiveHud hud;
	private final Map<Box, HudRenderer.Bounds> drawn = new IdentityHashMap<>();
	// one instance till its moved, drawn is by identity
	private Box defaultToast;
	private FlowLayout root;
	private FlowLayout toolbar;
	private LabelComponent toolbarTitle;
	private FlowLayout panel;
	private PanelScroll panelScroll;
	// last frame, so a rebuild doesnt flicker
	private int panelHeight = 200;
	// box id + page, so each page keeps its scroll
	private final Map<String, Double> scrollMemory = new HashMap<>();
	private String panelKey;
	private int scrollToRow = -1;
	private DiscreteSliderComponent scaleSlider;
	private ButtonComponent resetButton;
	private ButtonComponent undoButton;
	private ButtonComponent redoButton;
	private final List<Field> fields = new ArrayList<>();
	private final List<TextBoxComponent> homeFields = new ArrayList<>();
	private Box panelBox;
	private final List<FlowLayout> lineRows = new ArrayList<>();
	private final List<UIComponent> lineHandles = new ArrayList<>();
	private int lineDrag = -1;
	private int lineGap;
	private boolean mouseHeld;
	private HudHistory history;
	private String unsettled;
	private long unsettledSince;
	// tooltips drawn by us, owo's dont wrap or stay on screen -> might have to look into this later tho
	private final Map<UIComponent, List<FormattedCharSequence>> toolbarTips = new IdentityHashMap<>();
	private final Map<UIComponent, List<FormattedCharSequence>> panelTips = new IdentityHashMap<>();
	private final Map<UIComponent, List<FormattedCharSequence>> menuTips = new IdentityHashMap<>();
	private Map<UIComponent, List<FormattedCharSequence>> tipTarget = panelTips;
	private boolean guideOpen;
	private ButtonComponent guideButton;
	private List<FormattedCharSequence> guideLines;
	// screen + panel the lines were made for
	private int guideWrap;
	private boolean guideBeside;
	private boolean resetArmed;
	private boolean deleteArmed;
	private ButtonComponent deleteButton;
	private FlowLayout deleteRow;
	// page state, reset when another box is picked
	private String pageBox;
	private boolean picking;
	// by index bc undo makes new line objects
	private int editedIndex = -1;
	private LabelComponent preview;
	private Line previewLine;
	// unclipped, so clip only runs on change
	private String previewRaw;
	// Advanced line page
	private FreeTextArea textArea;
	private FreeTextArea rulesArea;
	// value list clicks type here
	private boolean typingRules;
	private LabelComponent textHint;
	private String textHintText;
	private FlowLayout ruleHints;
	private List<String> ruleHintLines = List.of();
	private boolean listOpen = true;
	private FlowLayout listPanel;
	private FlowLayout listContent;
	private FlowLayout mineGroup;
	private String mineSource;
	private String listSearch = "";
	private final List<Chip> formatChips = new ArrayList<>();
	private boolean formatsOff;
	private String hintSource;
	private boolean hintNoRoom;
	// rule hints + placeholder redo only when text, rules or data change
	private String hintRules;
	private HudData hintData;
	private String placeholderSource;
	private HudData placeholderData;
	private String placeholderText;
	private String noRoomFor;
	private FlowLayout menu;
	private UIComponent menuAnchor;
	// menu picks happen on press, rest of that press isnt a text drag
	private boolean swallowDrag;
	// 2nd press of a double click would hit the new page
	private boolean rebuiltByClick;
	private int toolbarSpot = -1;
	private Box selected;
	private Box dragging;
	private double grabX;
	private double grabY;
	private List<HudSnap.Guide> guides = List.of();
	private double scrollLeft;
	private HudLayout shownLayout;

	public EditHudScreen(Screen parent, LiveHud hud) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
		this.hud = hud;
		this.history = new HudHistory(hud.layout().toJson());
	}

	@Override
	protected OwoUIAdapter<FlowLayout> createAdapter() {
		return OwoUIAdapter.create(this, UIContainers::verticalFlow);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void build(FlowLayout root) {
		this.root = root;
		root.padding(Insets.of(PADDING));
		root.horizontalAlignment(HorizontalAlignment.CENTER);
		toolbar = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		moveToolbar(0);
		toolbar.surface(PANEL);
		toolbar.padding(Insets.of(3));
		toolbar.gap(4);
		toolbar.verticalAlignment(VerticalAlignment.CENTER);
		toolbarTitle = label("§fEdit §bHUD");
		toolbarTitle.margins(Insets.of(0, 0, 2, 6));
		toolbar.child(toolbarTitle);
		undoButton = iconButton(false, b -> undo(false));
		tip(toolbarTips, undoButton, "Undo (Ctrl+Z)");
		toolbar.child(undoButton);
		redoButton = iconButton(true, b -> undo(true));
		tip(toolbarTips, redoButton, "Redo (Ctrl+Shift+Z)");
		toolbar.child(redoButton);
		ButtonComponent newBox = button("+ New box", b -> newBox());
		tip(toolbarTips, newBox, "Add an empty box and pick its first line");
		toolbar.child(newBox);
		ButtonComponent preset = button("+ Preset", this::presetMenu);
		tip(toolbarTips, preset, "Add a ready-made box");
		toolbar.child(preset);
		guideButton = button("?", b -> openGuide(!guideOpen));
		guideButton.horizontalSizing(Sizing.fixed(BUTTON));
		tip(toolbarTips, guideButton, "How the editor works");
		toolbar.child(guideButton);
		ButtonComponent done = button("Done", b -> onClose());
		tip(toolbarTips, done, "Changes save by themselves");
		toolbar.child(done);
		armReset(false);
		root.child(toolbar);
		rebuildPanel();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (hud.layout() != shownLayout) {
			// late file load only, undo/Reset set it themselves
			if (shownLayout != null) {
				selected = null;
				dragging = null;
				lineDrag = -1;
				guides = List.of();
				history = new HudHistory(hud.layout().toJson());
				unsettled = null;
				rebuildPanel();
			}
			shownLayout = hud.layout();
		}
		for (TextBoxComponent field : homeFields) {
			field.moveCursorToEnd(false);
			field.moveCursorToStart(false);
		}
		homeFields.clear();
		syncFields();
		refreshLinePage();
		recordChanges(false);
		undoButton.active(history.canUndo());
		redoButton.active(history.canRedo());
		drawn.clear();
		for (Box box : hud.layout().boxes) {
			List<String> lines = lines(box);
			HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines, width, height);
			drawn.put(box, bounds);
			HudRenderer.draw(graphics, font, box, lines, bounds);
			if (!hud.visible(box)) drawHidden(graphics, bounds);
			graphics.outline(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2,
					box == selected ? OUTLINE_SELECTED : OUTLINE);
		}
		Box toast = toastBox();
		if (toast != null) {
			ToastText example = ToastText.example();
			ToastRenderer.Size size = ToastRenderer.size(font, example, toastStyle());
			HudRenderer.Bounds bounds = ToastRenderer.bounds(size, toast, width, height);
			drawn.put(toast, bounds);
			ToastRenderer.draw(graphics, font, example, toastStyle(), size, toast, bounds, 1);
			graphics.outline(bounds.x() - 1, bounds.y() - 1, bounds.width() + 2, bounds.height() + 2,
					toast == selected ? OUTLINE_SELECTED : OUTLINE);
		}
		for (HudSnap.Guide guide : guides) {
			graphics.fill(guide.x(), guide.y(), guide.x() + guide.width(), guide.y() + guide.height(), GUIDE);
			if (guide.label() != null) graphics.text(font, guide.label(), guide.labelX(), guide.labelY(), GUIDE, true);
		}
		if (selected == null) {
			// short, fits 480 wide (1080p auto scale)
			String[] hint = {"§7Drag a box to move it, hold Ctrl to place it freely, click it to edit",
					"§7Scroll on the selected box to resize it, arrow keys nudge it",
					"§7Changes save by themselves. Click ? in the toolbar for help"};
			for (int i = 0; i < hint.length; i++) {
				graphics.text(font, hint[i], (width - font.width(hint[i])) / 2, height - 64 + i * 11, 0xFFFFFFFF, true);
			}
		}
		placeToolbar();
		// panel draws over boxes from here
		graphics.nextStratum();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		fitPanel();
		fitList();
		fitMenu();
		drawRulesPlaceholder(graphics);
		if (lineDrag >= 0) {
			scrollWhileDragging(mouseY);
			graphics.nextStratum();
			drawLineDrag(graphics);
		}
		drawTip(graphics, mouseX, mouseY);
	}

	private static int clamp(int pos, int size, int screen) {
		return Math.max(TIP_MARGIN, Math.min(pos, screen - TIP_MARGIN - size));
	}

	// replaces owo's tooltip, smallest hovered component wins
	private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (dragging != null || lineDrag >= 0) return;
		Map<UIComponent, List<FormattedCharSequence>> tips = menu != null && menu.isInBoundingBox(mouseX, mouseY) ? menuTips
				: inPanel(mouseX, mouseY) || inList(mouseX, mouseY) ? panelTips
				: toolbar.isInBoundingBox(mouseX, mouseY) ? toolbarTips : Map.of();
		List<FormattedCharSequence> lines = null;
		long smallest = Long.MAX_VALUE;
		for (Map.Entry<UIComponent, List<FormattedCharSequence>> tip : tips.entrySet()) {
			UIComponent c = tip.getKey();
			long area = (long) c.width() * c.height();
			if (area < smallest && c.isInBoundingBox(mouseX, mouseY)) {
				smallest = area;
				lines = tip.getValue();
			}
		}
		if (lines != null) {
			graphics.setTooltipForNextFrame(font, lines, FIT, mouseX, mouseY, true);
		} else if (guideOpen) {
			drawGuide(graphics, mouseX, mouseY);
		}
	}

	// beside the panel if it fits there, else over it. never over the toolbar
	private void drawGuide(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean toolbarTop = toolbar.y() < height / 2;
		int top = (toolbarTop ? toolbar.y() + toolbar.height() + 2 : 0) + TIP_MARGIN;
		int bottom = (toolbarTop ? height : toolbar.y() - 2) - TIP_MARGIN;
		boolean panelRight = panelScroll != null && panelScroll.x() > width / 2;
		int px = panelScroll == null ? 0 : panelRight ? panelScroll.x() : panelScroll.x() + panelScroll.width();
		int beside = panelScroll == null ? 0 : (panelRight ? px : width - px) - 8 - 2 * TIP_MARGIN - 6;
		int full = width - 2 * TIP_MARGIN - 8;
		int key = java.util.Objects.hash(width, height, px, panelRight);
		if (guideLines == null || guideWrap != key) {
			guideWrap = key;
			guideBeside = false;
			Component text = Component.literal(HELP_CARD);
			List<Integer> wraps = new ArrayList<>();
			for (int w : new int[] {HELP_CARD_WIDTH, 400}) {
				if (w <= beside) wraps.add(-w);
			}
			if (beside > 0 && beside < 400) wraps.add(-beside);
			wraps.add(Math.min(HELP_CARD_WIDTH, full));
			wraps.add(Math.min(400, full));
			wraps.add(full);
			for (int w : wraps) {
				guideLines = font.split(text, Math.abs(w));
				guideBeside = w < 0;
				// 10px a line + frame
				if (guideLines.size() * 10 + 8 <= bottom - top) break;
			}
		}
		boolean side = guideBeside;
		graphics.setTooltipForNextFrame(font, guideLines, (screenW, screenH, x, y, w, h) -> new Vector2i(
				clamp(!side ? (screenW - w) / 2 : panelRight ? px - 8 - w : px + 8, w, screenW),
				clamp(toolbarTop ? top : Math.max(top, bottom - h), h, screenH)), mouseX, mouseY, true);
	}

	private void tip(UIComponent c, String text) {
		tip(tipTarget, c, text);
	}

	private void tip(Map<UIComponent, List<FormattedCharSequence>> target, UIComponent c, String text) {
		target.put(c, font.split(Component.literal(text), TIP_WIDTH));
	}

	// panel doesnt scroll by itself during a line drag
	private void scrollWhileDragging(int mouseY) {
		if (panelScroll == null || lineRows.isEmpty()) return;
		int top = panelScroll.y();
		int bottom = top + panelScroll.height();
		FlowLayout last = lineRows.getLast();
		// only as far as the lines go
		if (mouseY < top + 12 && lineRows.getFirst().y() < top + 4) {
			panelScroll.offset(Math.max(0, panelScroll.offset() - 3));
		} else if (mouseY > bottom - 12 && last.y() + last.height() > bottom - 4) {
			panelScroll.offset(panelScroll.offset() + 3);
		}
		updateLineGap(mouseY);
	}

	private void updateLineGap(double mouseY) {
		lineGap = 0;
		for (FlowLayout row : lineRows) {
			if (mouseY > row.y() + row.height() / 2.0) lineGap++;
		}
	}

	// badge, not a dark cover, so edits stay readable
	private void drawHidden(GuiGraphicsExtractor graphics, HudRenderer.Bounds bounds) {
		graphics.fill(bounds.x(), bounds.y(), bounds.x() + bounds.width(), bounds.y() + bounds.height(), HIDDEN_SHADE);
		String badge = "hidden here";
		int w = font.width(badge) + 4;
		int x = Math.max(0, Math.min(width - w, bounds.x() + bounds.width() - w));
		int y = bounds.y() >= font.lineHeight + 3 ? bounds.y() - font.lineHeight - 3 : bounds.y() + bounds.height() + 2;
		graphics.fill(x, y, x + w, y + font.lineHeight + 1, 0xC0000000);
		graphics.text(font, "§7" + badge, x + 2, y + 1, 0xFFFFFFFF, false);
	}

	private void drawLineDrag(GuiGraphicsExtractor graphics) {
		if (lineDrag >= lineRows.size()) return;
		FlowLayout from = lineRows.get(lineDrag);
		graphics.fill(from.x(), from.y(), from.x() + from.width(), from.y() + from.height(), DRAGGED_LINE);
		if (lineGap == lineDrag || lineGap == lineDrag + 1) return;
		FlowLayout last = lineRows.getLast();
		// rows 1px apart, bar covers it +1 each side
		int y = lineGap < lineRows.size() ? lineRows.get(lineGap).y() - 1 : last.y() + last.height();
		graphics.fill(from.x(), y - 1, from.x() + from.width(), y + 1, DROP_LINE);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		// prior typing/scrolling = own undo step
		syncFields();
		recordChanges(true);
		mouseHeld = true;
		swallowDrag = false;
		boolean left = click.button() == 0;
		boolean rebuilt = rebuiltByClick;
		rebuiltByClick = false;
		if (doubled && rebuilt && overPanel(click.x(), click.y())) return true;
		if (menu != null) {
			// rows + checkboxes act on any button, panel is left click only
			if (menu.isInBoundingBox(click.x(), click.y())) return !left || super.mouseClicked(click, doubled);
			// 1st click outside only closes it
			closeMenu();
			return true;
		}
		if (resetArmed && !resetButton.isInBoundingBox(click.x(), click.y())) armReset(false);
		if (deleteArmed && !deleteButton.isInBoundingBox(click.x(), click.y())) armDelete(false);
		int handle = left ? lineHandleAt(click.x(), click.y()) : -1;
		if (handle >= 0) {
			root.focusHandler().focus(null, null);
			lineDrag = handle;
			lineGap = handle;
			return true;
		}
		if (overPanel(click.x(), click.y())) return !left || super.mouseClicked(click, doubled);
		if (!left) return false;
		root.focusHandler().focus(null, null);
		Box hit = null;
		// last drawn = on top
		for (Box box : hud.layout().boxes) {
			HudRenderer.Bounds bounds = drawn.get(box);
			if (bounds != null && bounds.contains(click.x(), click.y())) hit = box;
		}
		Box toast = toastBox();
		HudRenderer.Bounds toastBounds = toast == null ? null : drawn.get(toast);
		if (toastBounds != null && toastBounds.contains(click.x(), click.y())) {
			// never moved = default spot, becomes a real one now
			if (hud.layout().toast == null) {
				hud.layout().toast = toast;
				defaultToast = null;
			}
			hit = toast;
		}
		if (hit != selected) scrollLeft = 0;
		selected = hit;
		if (hit != null) {
			HudRenderer.Bounds bounds = drawn.get(hit);
			dragging = hit;
			grabX = click.x() - bounds.x();
			grabY = click.y() - bounds.y();
			removePanel();
		} else {
			rebuildPanel();
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent drag, double dx, double dy) {
		if (swallowDrag) return true;
		if (lineDrag >= 0) {
			updateLineGap(drag.y());
			return true;
		}
		if (dragging == null) return super.mouseDragged(drag, dx, dy);
		HudRenderer.Bounds bounds = drawn.get(dragging);
		if (bounds == null) return true;
		int x = (int) Math.round(drag.x() - grabX);
		int y = (int) Math.round(drag.y() - grabY);
		Anchor anchorX = null;
		Anchor anchorY = null;
		guides = List.of();
		if (!minecraft.hasControlDown()) {
			List<HudSnap.Rect> others = new ArrayList<>();
			drawn.forEach((box, b) -> {
				if (box != dragging) others.add(new HudSnap.Rect(b.x(), b.y(), b.width(), b.height(), box.anchorX, box.anchorY));
			});
			HudSnap.Result snap = HudSnap.snap(new HudSnap.Rect(x, y, bounds.width(), bounds.height(), dragging.anchorX,
					dragging.anchorY), others, width, height, HudLayout.MARGIN);
			x = (int) Math.round(snap.x());
			y = (int) Math.round(snap.y());
			anchorX = snap.anchorX();
			anchorY = snap.anchorY();
			guides = snap.guides();
		}
		x = Anchor.clamp(x, bounds.width(), width);
		y = Anchor.clamp(y, bounds.height(), height);
		dragging.anchorX = anchorX != null ? anchorX : Anchor.forPosition(x, bounds.width(), width);
		dragging.anchorY = anchorY != null ? anchorY : Anchor.forPosition(y, bounds.height(), height);
		dragging.offsetX = dragging.anchorX.offset(x, bounds.width(), width);
		dragging.offsetY = dragging.anchorY.offset(y, bounds.height(), height);
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		// panel would scroll away from it
		if (menu != null && !menu.isInBoundingBox(mouseX, mouseY)) closeMenu();
		HudRenderer.Bounds bounds = selected == null ? null : drawn.get(selected);
		if (vertical == 0 || bounds == null || overPanel(mouseX, mouseY) || !bounds.contains(mouseX, mouseY)) {
			return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
		// touchpads send tiny amounts, add up to a notch
		if (Math.signum(vertical) != Math.signum(scrollLeft)) scrollLeft = 0;
		scrollLeft += vertical;
		// nudge so ten 0.1s (0.9999999) reach a notch
		int steps = (int) (scrollLeft + Math.copySign(1e-6, scrollLeft));
		if (steps == 0) return true;
		scrollLeft -= steps;
		if (Math.abs(scrollLeft) < 1e-6) scrollLeft = 0;
		float scale = Math.round((selected.scale + steps * SCALE_STEP) * 10) / 10f;
		selected.scale = Math.max(HudLayout.MIN_SCALE, Math.min(HudLayout.MAX_SCALE, scale));
		if (scaleSlider != null) scaleSlider.setFromDiscreteValue(selected.scale);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent key) {
		// esc goes back one step before it closes the editor
		if (key.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (menu != null) {
				closeMenu();
				return true;
			}
			if (guideOpen) {
				openGuide(false);
				return true;
			}
			if (picking || editedIndex >= 0) {
				backToLines();
				return true;
			}
		}
		// also while typing, title field has no own undo
		if (key.key() == GLFW.GLFW_KEY_Z && key.hasControlDownWithQuirk()) {
			undo(key.hasShiftDown());
			return true;
		}
		if (selected != null && !typing()) {
			int step = key.hasShiftDown() ? 10 : 1;
			int dx = key.isLeft() ? -step : key.isRight() ? step : 0;
			int dy = key.isUp() ? -step : key.isDown() ? step : 0;
			if (dx != 0 || dy != 0) {
				// measured now, key repeat beats frame rate
				HudRenderer.Bounds bounds = boundsOf(selected);
				int x = Anchor.clamp(bounds.x() + dx, bounds.width(), width);
				int y = Anchor.clamp(bounds.y() + dy, bounds.height(), height);
				selected.offsetX = selected.anchorX.offset(x, bounds.width(), width);
				selected.offsetY = selected.anchorY.offset(y, bounds.height(), height);
				return true;
			}
		}
		return super.keyPressed(key);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent release) {
		mouseHeld = false;
		swallowDrag = false;
		if (lineDrag >= 0) {
			int from = lineDrag;
			lineDrag = -1;
			if (panelBox != null) panelBox.moveLine(from, lineGap);
			rebuildPanel();
			return true;
		}
		if (dragging == null) return super.mouseReleased(release);
		dragging = null;
		guides = List.of();
		rebuildPanel();
		return true;
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void removed() {
		super.removed();
		syncFields();
		hud.save();
	}

	// new button each time, owo doesnt re-measure a changed label
	private void armReset(boolean armed) {
		resetArmed = armed;
		if (resetButton != null) {
			dropFocusIn(resetButton);
			toolbarTips.remove(resetButton);
			toolbar.removeChild(resetButton);
		}
		resetButton = button(armed ? "§cReally reset all?" : "Reset all", b -> {
			if (!resetArmed) {
				armReset(true);
				return;
			}
			armReset(false);
			HudLayout reset = HudLayout.defaults();
			// boxes only, toast spot stays
			reset.toast = hud.layout().toast;
			hud.setLayout(reset);
			// set here so it doesnt look like a late file load
			shownLayout = hud.layout();
			selected = null;
			rebuildPanel();
		});
		tip(toolbarTips, resetButton, armed ? "Click again to replace all boxes with the three default ones. Ctrl+Z brings yours back."
				: "Replace all boxes with the three default ones");
		resetButton.margins(Insets.left(6));
		toolbar.child(resetButton);
	}

	private void newBox() {
		Box box = new Box();
		box.id = newBoxId();
		box.title = "New box";
		box.showTitle = true;
		placeFree(box);
		addAndOpen(box, true);
	}

	// pick = straight to the line picker
	private void addAndOpen(Box box, boolean pick) {
		hud.layout().boxes.add(box);
		selected = box;
		scrollLeft = 0;
		pageBox = box.id;
		editedIndex = -1;
		picking = pick;
		rebuildPanel();
	}

	private void presetMenu(ButtonComponent anchor) {
		openMenu(anchor, m -> {
			m.child(menuTitle("Add a ready-made box"));
			for (HudLayout.Preset preset : HudLayout.presets()) {
				FlowLayout entry = menuEntry("§f" + preset.name(), () -> addPreset(preset));
				tip(entry, preset.description());
				m.child(entry);
			}
		});
	}

	private void addPreset(HudLayout.Preset preset) {
		closeMenu();
		Box box = preset.box().get();
		if (hud.layout().boxes.stream().anyMatch(b -> box.id.equals(b.id))) box.id = newBoxId();
		// own spot if free
		if (!free(box)) {
			box.offsetX = 0;
			box.offsetY = 0;
			placeFree(box);
		}
		addAndOpen(box, false);
	}

	// first free spot, else the last one
	private void placeFree(Box box) {
		for (Anchor[] spot : NEW_BOX_SPOTS) {
			box.anchorX = spot[0];
			box.anchorY = spot[1];
			if (free(box)) return;
		}
	}

	private boolean free(Box box) {
		HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, lines(box), width, height).grown(HudLayout.MARGIN);
		return drawn.values().stream().noneMatch(b -> bounds.overlapArea(b) > 0);
	}

	private String newBoxId() {
		for (int n = 1;; n++) {
			String id = "box" + n;
			if (hud.layout().boxes.stream().noneMatch(b -> id.equals(b.id))) return id;
		}
	}

	private void armDelete(boolean armed) {
		deleteArmed = armed;
		if (deleteRow == null) return;
		if (deleteButton != null) {
			dropFocusIn(deleteButton);
			panelTips.remove(deleteButton);
			deleteRow.removeChild(deleteButton);
		}
		Box box = panelBox;
		deleteButton = iconTextButton(REMOVE, armed ? "§cReally delete?" : "§cDelete box", null, b -> {
			if (!deleteArmed) {
				armDelete(true);
				return;
			}
			deleteArmed = false;
			hud.layout().boxes.remove(box);
			selected = null;
			rebuildPanel();
		});
		tip(deleteButton, armed ? "Click again to delete this box. Ctrl+Z brings it back."
				: "Delete this box. Ctrl+Z brings it back.");
		deleteRow.child(deleteButton);
	}

	private record Field(Supplier<String> value, Consumer<String> apply) {
	}

	// polled bc caxton cancels onValueChange
	private void syncFields() {
		for (Field field : fields) field.apply().accept(field.value().get());
	}

	// waits out drags, mouse down, title typing. now = record right away
	private void recordChanges(boolean now) {
		String json = hud.layout().toJson();
		if (json.equals(history.current())) {
			unsettled = null;
			return;
		}
		if (!now) {
			if (dragging != null || lineDrag >= 0 || mouseHeld || typing()) return;
			long time = Util.getMillis();
			if (!json.equals(unsettled)) {
				unsettled = json;
				unsettledSince = time;
				return;
			}
			if (time - unsettledSince < SETTLE_MILLIS) return;
		}
		unsettled = null;
		history.record(json);
		hud.save();
	}

	// owo mixins make EditBox + MultiLineEditBox greedy, javac cant see the 2nd
	private boolean typing() {
		return root.focusHandler().focused() instanceof GreedyInputUIComponent;
	}

	// owo keeps a removed component focused, enter/space would still press it
	private void dropFocusIn(UIComponent removed) {
		for (UIComponent c = root.focusHandler().focused(); c != null; c = c.parent()) {
			if (c == removed) {
				root.focusHandler().focus(null, null);
				return;
			}
		}
	}

	private static List<String> lineValues(Box box) {
		return box.lines.stream().map(line -> line.value).toList();
	}

	private void undo(boolean redo) {
		syncFields();
		recordChanges(true);
		String json = redo ? history.redo() : history.undo();
		if (json == null) return;
		String selectedId = selected == null ? null : selected.id;
		List<String> lineValues = selected == null ? null : lineValues(selected);
		HudLayout layout = HudLayout.fromJson(json);
		hud.setLayout(layout);
		shownLayout = layout;
		selected = null;
		for (Box box : layout.boxes) {
			if (box.id.equals(selectedId)) selected = box;
		}
		if (HudLayout.TOAST_ID.equals(selectedId)) selected = layout.toast;
		// lines moved, the index may point at another line now
		if (selected != null && !lineValues(selected).equals(lineValues)) editedIndex = -1;
		dragging = null;
		lineDrag = -1;
		guides = List.of();
		if (resetArmed) armReset(false);
		rebuildPanel();
		hud.save();
	}

	// null = toast isnt movable, nothing to place
	private Box toastBox() {
		if (!Surveyor.SETTINGS.get().summary().movableToast()) return null;
		if (hud.layout().toast != null) return hud.layout().toast;
		if (defaultToast == null) defaultToast = HudLayout.defaultToast();
		return defaultToast;
	}

	private static SummarySettings.Style toastStyle() {
		return Surveyor.SETTINGS.get().summary().style();
	}

	private HudRenderer.Bounds boundsOf(Box box) {
		if (HudLayout.TOAST_ID.equals(box.id)) return ToastRenderer.bounds(font, ToastText.example(), toastStyle(), box, width, height);
		return HudRenderer.bounds(font, box, lines(box), width, height);
	}

	private void buildToastPage() {
		panel.child(label("§fShaft toast"));
		LabelComponent what = label("§7Shows for a few seconds when you leave a shaft. Drag it to move it, scroll on it to resize it.");
		what.horizontalSizing(Sizing.fixed(inner()));
		panel.child(what);
		LabelComponent where = label("§7Style, time and Top right or Movable are in Settings, Journal.");
		where.horizontalSizing(Sizing.fixed(inner()));
		where.margins(Insets.top(4));
		panel.child(where);
	}

	private List<String> lines(Box box) {
		List<String> lines = HudBoxText.lines(box, hud.data(), true);
		return lines.isEmpty() ? List.of("§8(empty)") : lines;
	}

	// not while dragging or reset armed, so nothing jumps under the mouse
	private void placeToolbar() {
		if (dragging != null || resetArmed || toolbar.width() == 0) return;
		int best = 0;
		long bestCost = Long.MAX_VALUE;
		for (int i = 0; i < TOOLBAR_SPOTS.length; i++) {
			int x = PADDING + Math.round(TOOLBAR_SPOTS[i][0] / 100f * (width - 2 * PADDING - toolbar.width()));
			int y = PADDING + Math.round(TOOLBAR_SPOTS[i][1] / 100f * (height - 2 * PADDING - toolbar.height()));
			long cost = coverCost(new HudRenderer.Bounds(x, y, toolbar.width(), toolbar.height()));
			if (cost < bestCost) {
				best = i;
				bestCost = cost;
			}
			if (cost == 0) break;
		}
		moveToolbar(best);
	}

	private void moveToolbar(int spot) {
		if (spot == toolbarSpot) return;
		toolbarSpot = spot;
		toolbar.positioning(Positioning.relative(TOOLBAR_SPOTS[spot][0], TOOLBAR_SPOTS[spot][1]));
	}

	private long coverCost(HudRenderer.Bounds area) {
		long cost = 0;
		for (Map.Entry<Box, HudRenderer.Bounds> drawnBox : drawn.entrySet()) {
			HudRenderer.Bounds outlined = drawnBox.getValue().grown(1);
			long covered = area.overlapArea(outlined);
			cost += covered;
			if (covered == (long) outlined.width() * outlined.height()) cost += 1_000_000;
			// never over the box being edited if theres another spot
			if (covered > 0 && drawnBox.getKey() == selected) cost += 10_000_000;
		}
		if (panelScroll != null && area.overlapArea(
				new HudRenderer.Bounds(panelScroll.x(), panelScroll.y(), panelScroll.width(), panelScroll.height())) > 0) {
			cost += 100_000_000;
		}
		if (listPanel != null && area.overlapArea(new HudRenderer.Bounds(listPanel.x(), listPanel.y(), listPanel.width(), listPanel.height())) > 0) {
			cost += 100_000_000;
		}
		return cost;
	}

	private boolean overPanel(double x, double y) {
		return toolbar.isInBoundingBox(x, y) || inPanel(x, y) || inList(x, y) || (menu != null && menu.isInBoundingBox(x, y));
	}

	// empty part under the content lets boxes show + get clicked
	private boolean inPanel(double x, double y) {
		return panelScroll != null && panelScroll.isInBoundingBox(x, y) && y < panelBottom();
	}

	private int panelBottom() {
		return Math.min(panelScroll.y() + panelScroll.height(), panel.y() + panel.height());
	}

	// sizes known after layout
	private void fitPanel() {
		if (panelScroll == null || panelScroll.width() == 0) return;
		// gui scale changed w/ the panel open
		if (builtPanelWidth != panelWidth()) {
			rebuildPanel();
			return;
		}
		// no room beside it = leave the top for the toolbar
		int beside = listPanel == null ? 0 : listWidth() + 2;
		boolean narrow = toolbar.width() + panelScroll.width() + beside + 3 * PADDING > width;
		// title goes first, so the panel keeps the full height at 480 wide
		if (narrow && toolbarTitle.parent() != null) {
			toolbar.removeChild(toolbarTitle);
			return;
		}
		int reserve = narrow ? toolbar.height() + PADDING : 0;
		// screen height, not content, a content fit jumps around
		int fit = height - 2 * PADDING - reserve;
		if (fit != panelHeight || panelScroll.height() != fit) {
			panelHeight = fit;
			panelScroll.verticalSizing(Sizing.fixed(fit));
			return;
		}
		if (scrollToRow >= 0) {
			if (scrollToRow < lineRows.size()) showRow(lineRows.get(scrollToRow));
			scrollToRow = -1;
		}
	}

	private void showRow(UIComponent row) {
		double offset = panelScroll.offset();
		int top = row.y() - panelScroll.y();
		int bottom = top + row.height();
		if (bottom > panelScroll.height() - 4) offset += bottom - panelScroll.height() + 8;
		else if (top < 4) offset -= 8 - top;
		panelScroll.offset(Math.max(0, offset));
	}

	private static final class PanelScroll extends ScrollContainer<FlowLayout> {
		PanelScroll(int height, FlowLayout content) {
			super(ScrollDirection.VERTICAL, Sizing.content(), Sizing.fixed(height), content);
		}

		double offset() {
			return scrollOffset;
		}

		void offset(double offset) {
			scrollOffset = offset;
			currentScrollPosition = offset;
		}

		// owo only clamps the target, clamp drawn pos too or a shorter panel overshoots
		@Override
		public void layout(Size space) {
			super.layout(space);
			scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
			currentScrollPosition = Math.max(0, Math.min(currentScrollPosition, maxScroll + .5));
		}
	}

	private static final class HoverRow extends FlowLayout {
		HoverRow() {
			super(Sizing.fill(100), Sizing.content(), Algorithm.HORIZONTAL);
			gap(2);
			verticalAlignment(VerticalAlignment.CENTER);
			padding(Insets.vertical(1));
			cursorStyle(CursorStyle.HAND);
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, HOVER);
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
		}
	}

	// equal cells, so options never wrap one by one
	private final class Grid extends FlowLayout {
		private final int cell;

		Grid(int columns, int keyWidth) {
			super(Sizing.fixed(inner() - keyWidth - 2), Sizing.content(), Algorithm.LTR_TEXT);
			gap(2);
			cell = (inner() - keyWidth - 2 - (columns - 1) * 2) / columns;
		}

		ButtonComponent add(ButtonComponent button) {
			button.horizontalSizing(Sizing.fixed(cell));
			child(button);
			return button;
		}
	}

	private static final class FreeTextArea extends TextAreaComponent {
		// rules = 1 rule a line, text = 1 HUD line
		private final boolean multiline;

		FreeTextArea(Sizing horizontal, Sizing vertical, boolean multiline) {
			super(horizontal, vertical);
			this.multiline = multiline;
		}

		int cursor() {
			return editBox.cursor();
		}

		int limit() {
			return editBox.characterLimit();
		}

		void set(String text, int cursor) {
			text(text);
			editBox.setSelecting(false);
			editBox.seekCursor(Whence.ABSOLUTE, cursor);
		}

		void select(int from, int to) {
			editBox.setSelecting(false);
			editBox.seekCursor(Whence.ABSOLUTE, from);
			editBox.setSelecting(true);
			editBox.seekCursor(Whence.ABSOLUTE, to);
			editBox.setSelecting(false);
		}

		@Override
		public boolean keyPressed(KeyEvent input) {
			if (!multiline && (input.key() == GLFW.GLFW_KEY_ENTER || input.key() == GLFW.GLFW_KEY_KP_ENTER)) return true;
			if (input.isCycleFocus()) return false;
			// vanilla paste keeps newlines + \r
			if (input.isPaste()) {
				String paste = Minecraft.getInstance().keyboardHandler.getClipboard().replace("\r", "");
				editBox.insertText(multiline ? paste : paste.replace('\n', ' '));
				return true;
			}
			return super.keyPressed(input);
		}

		// vanilla eats the wheel even w/ nothing to scroll, panel should scroll
		@Override
		public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
			return maxScrollAmount() > 0 && super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
		}
	}

	private int builtPanelWidth;

	private int panelWidth() {
		return Math.clamp(width * 30 / 100, PANEL_MIN, PANEL_MAX);
	}

	// panel minus its padding
	private int inner() {
		return panelWidth() - 10;
	}

	private void removePanel() {
		syncFields();
		if (panelScroll != null) {
			dropFocusIn(panelScroll);
			scrollMemory.put(panelKey, panelScroll.offset());
			root.removeChild(panelScroll);
		}
		if (listPanel != null) {
			dropFocusIn(listPanel);
			root.removeChild(listPanel);
		}
		closeMenu();
		panelScroll = null;
		panel = null;
		panelBox = null;
		panelTips.clear();
		listPanel = null;
		listContent = null;
		mineGroup = null;
		mineSource = null;
		formatChips.clear();
		textArea = null;
		rulesArea = null;
		textHint = null;
		textHintText = null;
		ruleHints = null;
		ruleHintLines = List.of();
		scaleSlider = null;
		fields.clear();
		homeFields.clear();
		lineRows.clear();
		lineHandles.clear();
		deleteButton = null;
		deleteRow = null;
		deleteArmed = false;
		preview = null;
		previewLine = null;
		previewRaw = null;
		hintSource = null;
		hintNoRoom = false;
		hintRules = null;
		hintData = null;
		placeholderSource = null;
		placeholderData = null;
		placeholderText = null;
		noRoomFor = null;
	}

	private void rebuildPanel() {
		if (selected == null || !selected.id.equals(pageBox)) {
			picking = false;
			editedIndex = -1;
		}
		if (selected != null && editedIndex >= selected.lines.size()) editedIndex = -1;
		removePanel();
		if (selected == null) {
			pageBox = null;
			return;
		}
		Box box = selected;
		pageBox = box.id;
		panelBox = box;
		panelKey = box.id + "/" + (picking ? "picker" : editedIndex >= 0 ? "line" + editedIndex : "box");
		builtPanelWidth = panelWidth();
		panel = UIContainers.verticalFlow(Sizing.fixed(builtPanelWidth), Sizing.content());
		panel.padding(Insets.of(5));
		panel.gap(1);
		panelScroll = new PanelScroll(panelHeight, panel);
		panelScroll.surface((graphics, c) -> {
			int h = panelBottom() - c.y();
			graphics.fill(c.x(), c.y(), c.x() + c.width(), c.y() + h, PANEL_FILL);
			graphics.drawRectOutline(c.x(), c.y(), c.width(), h, PANEL_OUTLINE);
		});
		panelScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		panelScroll.scrollbarThiccness(2);
		panelScroll.offset(scrollMemory.getOrDefault(panelKey, 0.0));
		// measured now, after undo the box wasnt drawn yet
		HudRenderer.Bounds bounds = boundsOf(box);
		boolean boxOnRight = bounds.x() + bounds.width() / 2 > width / 2;
		panelScroll.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));
		if (box == hud.layout().toast) buildToastPage();
		else if (picking) buildPicker(box);
		else if (editedIndex >= 0) buildLinePage(box, editedIndex);
		else buildBoxPage(box);
		// after the content, owo lays out right away and would clamp the offset to 0
		root.child(panelScroll);
		rebuiltByClick = true;
	}

	private void openLine(int index) {
		editedIndex = index;
		typingRules = false;
		scrollMemory.remove(pageBox + "/line" + index);
		rebuildPanel();
	}

	private void backToLines() {
		scrollToRow = editedIndex;
		editedIndex = -1;
		picking = false;
		rebuildPanel();
	}

	private void buildBoxPage(Box box) {
		panel.child(crumbs(box, null));
		FlowLayout linesHead = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		linesHead.verticalAlignment(VerticalAlignment.CENTER);
		linesHead.child(label("§fLines §8(" + box.lines.size() + ")"));
		ButtonComponent addLine = button("+ Add line", b -> {
			picking = true;
			rebuildPanel();
		});
		tip(addLine, "Add any value to this box");
		linesHead.child(spacer());
		linesHead.child(addLine);
		panel.child(linesHead);
		for (int i = 0; i < box.lines.size(); i++) panel.child(lineRow(box, i));
		if (box.lines.isEmpty()) panel.child(label("§7No lines yet. Add one to show a value."));

		panel.child(heading("Box"));
		TextBoxComponent title = textField(box.title, 64, text -> box.title = text);
		tip(title, "Box title. {shaft_type} shows the shaft's type.");
		FlowLayout titleRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		titleRow.gap(3);
		titleRow.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout showTitle = check(null, box.showTitle, checked -> box.showTitle = checked);
		tip(showTitle, "Show the title");
		titleRow.child(showTitle);
		titleRow.child(title);
		titleColor(box, titleRow);
		panel.child(titleRow);

		DiscreteSliderComponent scale = UIComponents.discreteSlider(Sizing.expand(), HudLayout.MIN_SCALE, HudLayout.MAX_SCALE);
		scale.decimalPlaces(1);
		scale.setFromDiscreteValue(box.scale);
		scale.verticalSizing(Sizing.fixed(CHOICE));
		scale.scrollStep(SCALE_STEP / (HudLayout.MAX_SCALE - HudLayout.MIN_SCALE));
		scale.message(text -> Component.literal(text + "×"));
		scale.onChanged().subscribe(value -> box.scale = (float) scale.discreteValue());
		tip(scale, "Box size. You can also scroll on the box.");
		scaleSlider = scale;
		panel.child(row("Size", scale));

		Grid background = new Grid(Background.values().length, ROW_LABEL);
		for (Background option : Background.values()) {
			background.add(choice(name(option), box.background == option, () -> box.background = option));
		}
		panel.child(row("Background", background));

		Grid align = new Grid(Align.values().length, ROW_LABEL);
		for (Align option : Align.values()) {
			align.add(choice(name(option), box.align == option, () -> box.align = option));
		}
		panel.child(row("Align", align));

		FlowLayout places = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		places.child(place("Shafts", box.showInShaft, on -> box.showInShaft = on, "In a Glacite Mineshaft"));
		places.child(place("Mines", box.showInMines, on -> box.showInMines = on, "In the Dwarven Mines, outside a shaft"));
		places.child(place("Elsewhere", box.showElsewhere, on -> box.showElsewhere = on,
				"Anywhere else: the Hub, other islands, other servers"));
		FlowLayout shows = row("Show in", places);
		shows.verticalAlignment(VerticalAlignment.TOP);
		// label level w/ the 1st checkbox
		shows.children().getFirst().margins(Insets.top(4));
		shows.margins(Insets.top(1));
		panel.child(shows);

		panel.child(boxColor(box, true, box.labelColor, code -> box.labelColor = code, box.labelBold, b -> box.labelBold = b));
		panel.child(boxColor(box, false, box.valueColor, code -> box.valueColor = code, box.valueBold, b -> box.valueBold = b));

		deleteRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		deleteRow.margins(Insets.top(5));
		panel.child(deleteRow);
		armDelete(false);
	}

	// right of the title field
	private void titleColor(Box box, FlowLayout row) {
		ButtonComponent color = colorButton(box.titleColorOrDefault(), null, code -> box.titleColor = "e".equals(code) ? null : code);
		tip(color, "Title colour of this box: " + COLOR_NAMES[colorIndex(box.titleColorOrDefault())]);
		row.child(color);
		boolean bold = box.titleBoldOrDefault();
		ButtonComponent boldButton = button((bold ? "§e" : "§f") + "§lB", b -> {
			box.titleBold = bold ? false : null;
			rebuildPanel();
		});
		boldButton.verticalSizing(Sizing.fixed(CHOICE));
		tip(boldButton, "Bold");
		row.child(boldButton);
	}

	private FlowLayout boxColor(Box box, boolean labels, String current, Consumer<String> setColor, boolean bold,
			Consumer<Boolean> setBold) {
		String name = labels ? "Labels" : "Values";
		FlowLayout row = row(name);
		row.margins(Insets.top(1));
		ButtonComponent color = colorButton(current, null, setColor);
		tip(color, name + " colour of this box: " + COLOR_NAMES[colorIndex(current)]
				+ ". Lines with their own colour keep it.");
		row.child(color);
		ButtonComponent boldButton = button((bold ? "§e" : "§f") + "§lB", b -> {
			setBold.accept(!bold);
			rebuildPanel();
		});
		boldButton.verticalSizing(Sizing.fixed(CHOICE));
		tip(boldButton, "Bold");
		row.child(boldButton);
		// custom text lines use their own codes, not these
		List<Line> own = box.lines.stream().filter(l -> !l.advancedMode() && (labels ? l.labelColor : l.valueColor) != null).toList();
		if (!own.isEmpty()) {
			ButtonComponent all = button("All lines", b -> {
				for (Line line : own) {
					if (labels) line.labelColor = null;
					else line.valueColor = null;
				}
				rebuildPanel();
			});
			all.verticalSizing(Sizing.fixed(CHOICE));
			tip(all, (own.size() == 1 ? "1 line has" : own.size() + " lines have") + " its own " + name.toLowerCase()
					+ " colour, so this one doesn't reach " + (own.size() == 1 ? "it" : "them")
					+ ". Click to use the box's colour on every line. Ctrl+Z undoes it.");
			row.child(all);
		}
		return row;
	}

	private void buildPicker(Box box) {
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(4);
		header.verticalAlignment(VerticalAlignment.CENTER);
		ButtonComponent back = button("‹ Back", b -> backToLines());
		tip(back, "Back without adding (Esc)");
		header.child(back);
		header.child(label("§7Pick a value to add"));
		panel.child(crumbs(box, "Add a line"));
		panel.child(header);
		for (HudValue.Group group : HudValue.Group.values()) {
			panel.child(section(group.displayName()));
			for (HudValue value : HudValue.values()) {
				if (value.group() != group) continue;
				HoverRow row = new HoverRow();
				int nameWidth = 88 + (inner() - (PANEL_MIN - 10)) / 2;
				LabelComponent name = label(clip("§f" + value.displayName(), nameWidth - 2));
				name.horizontalSizing(Sizing.fixed(nameWidth));
				row.child(name);
				LabelComponent shows = label(clip(HudBoxText.line(box, new Line(value), hud.data(), true), inner() - nameWidth - 20));
				shows.horizontalSizing(Sizing.expand());
				row.child(shows);
				if (box.lines.stream().anyMatch(line -> line.hudValue() == value)) {
					LabelComponent have = label("§a✔");
					tip(have, "Already in this box. You can add it again, f.e. with another range.");
					row.child(have);
				}
				tip(row, valueTip(value));
				row.mouseDown().subscribe((click, doubled) -> {
					box.lines.add(new Line(value));
					picking = false;
					openLine(box.lines.size() - 1);
					return true;
				});
				panel.child(row);
			}
		}
	}

	private static String valueTip(HudValue value) {
		StringBuilder tip = new StringBuilder("§f").append(value.displayName()).append("\n§7").append(value.description());
		if (value.ranges().size() > 1) {
			tip.append("\n§8Ranges: ");
			tip.append(String.join(", ", value.ranges().stream().map(HudValue.Range::displayName).toList()));
		}
		return tip.append("\n§eClick to add").toString();
	}

	private FlowLayout lineRow(Box box, int index) {
		Line line = box.lines.get(index);
		HoverRow row = new HoverRow();
		row.verticalSizing(Sizing.fixed(CHOICE));
		row.gap(4);
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(CHOICE - 2));
		grip.surface(GRIP);
		grip.cursorStyle(CursorStyle.MOVE);
		tip(grip, "Drag to move this line");
		row.child(grip);
		SmallCheckboxComponent shown = UIComponents.smallCheckbox(null);
		shown.checked(line.shown);
		shown.onChanged().subscribe(checked -> {
			line.shown = checked;
			rebuildPanel();
		});
		tip(shown, "Show this line");
		row.child(shown);
		String text = HudBoxText.line(box, line, hud.data(), true);
		if (!line.shown) text = "§8" + strip(text);
		// grip, checkbox, marker, gaps + slack so it never wraps
		LabelComponent look = label(clip(text, inner() - 40));
		look.horizontalSizing(Sizing.expand());
		tip(look, "Click to change the label, range, format and colours");
		row.child(look);
		if (!line.advancedMode() && (line.labelColor != null || line.valueColor != null)) {
			FlowLayout own = UIContainers.horizontalFlow(Sizing.fixed(8), Sizing.fixed(8));
			String label = line.labelColor;
			String value = line.valueColor;
			own.surface((graphics, c) -> {
				int x = c.x();
				int y = c.y();
				graphics.fill(x, y, x + 8, y + 8, 0xFF000000);
				// label left, value right, one colour fills it
				int left = COLORS[colorIndex(label != null ? label : value)];
				int right = COLORS[colorIndex(value != null ? value : label)];
				graphics.fill(x + 1, y + 1, x + 4, y + 7, 0xFF000000 | left);
				graphics.fill(x + 4, y + 1, x + 7, y + 7, 0xFF000000 | right);
			});
			List<String> parts = new ArrayList<>();
			if (label != null) parts.add("labels " + COLOR_NAMES[colorIndex(label)]);
			if (value != null) parts.add("values " + COLOR_NAMES[colorIndex(value)]);
			tip(own, "Own colour: " + String.join(", ", parts) + ". The box's colours don't change this line. "
					+ "Click the line to change it.");
			row.child(own);
		}
		UIComponent marker;
		if (line.advancedMode()) {
			FlowLayout pencil = UIContainers.horizontalFlow(Sizing.fixed(8), Sizing.fixed(PENCIL_ICON.length));
			pencil.surface((graphics, c) -> drawIcon(graphics, PENCIL_ICON, c.x(), c.y(), false, 0xFF55FFFF));
			marker = pencil;
		} else {
			marker = label("§8›");
		}
		marker.margins(Insets.right(2));
		if (line.advancedMode()) tip(marker, "Advanced: typed text and colour rules");
		row.child(marker);
		row.mouseDown().subscribe((click, doubled) -> {
			openLine(index);
			return true;
		});
		lineRows.add(row);
		lineHandles.add(grip);
		return row;
	}

	private void buildLinePage(Box box, int index) {
		Line line = box.lines.get(index);
		HudValue value = line.hudValue();
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(3);
		header.verticalAlignment(VerticalAlignment.CENTER);
		ButtonComponent back = button("‹ Back", b -> backToLines());
		tip(back, "Back to the box (Esc)");
		header.child(back);
		ButtonComponent remove = iconTextButton(REMOVE, "§cRemove", null, b -> {
			box.lines.remove(line);
			editedIndex = -1;
			scrollToRow = Math.min(index, box.lines.size() - 1);
			rebuildPanel();
		});
		tip(remove, "Remove this line. Ctrl+Z brings it back.");
		header.child(spacer());
		header.child(remove);
		panel.child(crumbs(box, value.displayName()));
		panel.child(header);

		FlowLayout look = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		look.surface(PREVIEW);
		look.padding(Insets.of(3, 3, 5, 3));
		look.margins(Insets.vertical(3));
		preview = label("");
		previewLine = line;
		look.child(preview);
		if (!line.shown) {
			look.child(spacer());
			LabelComponent hidden = label("§7" + HIDDEN_NOTE);
			tip(hidden, "This line is hidden. Tick it in the box's line list to show it.");
			look.child(hidden);
		}
		tip(look, "§f" + value.displayName() + "\n§7" + value.description());
		panel.child(look);

		boolean advanced = line.advancedMode();
		Grid modes = new Grid(2, KEY);
		tip(modes.add(choice("Easy", !advanced, () -> {
			if (!line.advancedMode()) return;
			line.settle(box);
			line.advanced = false;
		})), "Pick a label, range, format and colour");
		tip(modes.add(choice("Advanced", advanced, () -> {
			if (line.advancedMode()) return;
			line.settle(box);
			line.advanced = true;
		})), "Type the whole line yourself: colours, {values}, maths and colour rules. Starts from this line plus its "
				+ "ready-made rules.");
		panel.child(keyRow("Mode", modes));
		if (advanced) buildAdvanced(box, line);
		else buildEasy(box, line);
	}

	private void buildEasy(Box box, Line line) {
		HudValue value = line.hudValue();
		if (line.text != null || line.colorRules != null) {
			LabelComponent kept = label("§8Your Advanced text and rules are kept. Reset there starts again from this.");
			kept.maxWidth(inner());
			panel.child(kept);
		}
		panel.child(heading("Label"));
		FlowLayout labelRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		labelRow.gap(3);
		labelRow.verticalAlignment(VerticalAlignment.CENTER);
		TextBoxComponent prefix = textField(line.prefix, 64, typed -> line.prefix = typed);
		tip(prefix, "The text before the value. Spaces count.");
		labelRow.child(prefix);
		ButtonComponent labelColor = colorButton(line.labelColor, box.labelColor, code -> line.labelColor = code);
		tip(labelColor, "Label colour. \"Box\" follows the box's label colour.");
		labelRow.child(labelColor);
		panel.child(labelRow);
		if (line.range() == HudValue.Range.SHAFT) {
			LabelComponent note = label("§8\"This shaft\" in the label says \"Last shaft\" while you're outside a shaft.");
			note.maxWidth(inner());
			note.margins(Insets.top(1));
			panel.child(note);
		}

		panel.child(heading("Value"));
		List<HudValue.Range> ranges = value.ranges();
		if (ranges.size() > 1) {
			Grid options = new Grid(2, KEY);
			for (HudValue.Range r : ranges) {
				tip(options.add(choice(r.displayName(), line.range() == r, () -> line.setRange(r))), r.description() + ".");
			}
			panel.child(keyRow("Range", options));
		}
		List<String> formats = HudFormat.options(value.kind());
		if (formats.size() > 1 && value.hasFormatChoice()) {
			Grid options = new Grid(formats.size(), KEY);
			for (int i = 0; i < formats.size(); i++) {
				int format = i;
				tip(options.add(choice(formats.get(i), line.format == i, () -> line.format = format)),
						"Shows as " + HudFormat.example(value.kind(), i, HudBoxText.suffix(value, line.range())));
			}
			panel.child(keyRow("Format", options));
		}
		ButtonComponent valueColor = colorButton(line.valueColor, box.valueColor, value.defaultColor(),
				code -> line.valueColor = code);
		tip(valueColor, "Value colour. \"Box\" follows the box's value colour.");
		panel.child(keyRow("Colour", valueColor));
		if (value.followsShaft(line.range())) panel.child(outsideRow(line, false));
	}

	private FlowLayout outsideRow(Line line, boolean advanced) {
		String which = advanced ? "shaft values in this line show " : "show ";
		Grid outside = new Grid(2, KEY);
		tip(outside.add(choice("Last shaft", !line.dash(), () -> line.dashOutside = null)),
				"Outside a shaft, " + which + "the last shaft's number");
		tip(outside.add(choice("-", line.dash(), () -> line.dashOutside = true)), "Outside a shaft, " + which + "-");
		return keyRow("Outside", outside);
	}

	private void buildAdvanced(Box box, Line line) {
		FlowLayout textHead = heading("Text");
		textHead.child(spacer());
		ButtonComponent values = button(listOpen ? "§eValues" : "Values", b -> {
			listOpen = !listOpen;
			rebuildPanel();
		});
		values.verticalSizing(Sizing.fixed(CHOICE));
		tip(values, listOpen ? "Hide the value list" : "Show the value list beside this panel: values, maths and colours to click");
		textHead.child(values);
		ButtonComponent reset = iconTextButton(pixels(RESET_ICON), "Reset", null, b -> {
			line.resetAdvanced(box);
			line.advanced = true;
			rebuildPanel();
		});
		reset.verticalSizing(Sizing.fixed(CHOICE));
		reset.margins(Insets.left(2));
		tip(reset, "Back to the Easy look plus the ready-made rules. Ctrl+Z undoes it.");
		textHead.child(reset);
		panel.child(textHead);

		String start = line.advancedText(box);
		textArea = new FreeTextArea(Sizing.fill(100), Sizing.fixed(3 * font.lineHeight + 8), false);
		textArea.setCharacterLimit(TEXT_LIMIT);
		textArea.text(start);
		textArea.margins(Insets.top(2));
		String[] last = {start};
		fields.add(new Field(textArea::getValue, typed -> {
			// pasted newlines, 1 HUD line
			if (typed.indexOf('\n') >= 0 || typed.indexOf('\r') >= 0) {
				typed = typed.replace("\r", "").replace('\n', ' ');
				textArea.text(typed);
			}
			if (typed.equals(last[0])) return;
			last[0] = typed;
			line.settle(box);
			line.text = typed;
		}));
		panel.child(textArea);
		textHint = label(TEXT_HINT);
		textHint.maxWidth(inner());
		textHintText = TEXT_HINT;
		panel.child(textHint);
		panel.child(outsideRow(line, true));

		FlowLayout rulesHead = heading("Colour rules");
		tip(rulesHead, RULES_HELP);
		panel.child(rulesHead);
		String rules = String.join("\n", line.advancedRules());
		rulesArea = new FreeTextArea(Sizing.fill(100), Sizing.fixed(4 * font.lineHeight + 8), true);
		rulesArea.setCharacterLimit(RULES_LIMIT);
		rulesArea.text(rules);
		rulesArea.margins(Insets.top(2));
		tip(rulesArea, RULES_HELP);
		String[] lastRules = {rules};
		fields.add(new Field(rulesArea::getValue, typed -> {
			typed = typed.replace("\r", "");
			if (typed.equals(lastRules[0])) return;
			lastRules[0] = typed;
			line.settle(box);
			line.colorRules = ruleLines(typed);
		}));
		panel.child(rulesArea);
		ButtonComponent addRule = button("+ Rule", b -> addRule(box, line));
		tip(addRule, "Type a rule for the first number in the text, with its number now");
		addRule.margins(Insets.top(2));
		panel.child(addRule);
		ruleHints = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		ruleHints.margins(Insets.top(2));
		panel.child(ruleHints);
		if (listOpen) buildValueList();
	}

	// field lines, trailing empty ones left out
	private static List<String> ruleLines(String typed) {
		List<String> lines = new ArrayList<>(List.of(typed.split("\n", -1)));
		while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
		return lines;
	}

	private void addRule(Box box, Line line) {
		List<String> tags = HudBoxText.numberTags(textArea.getValue());
		if (tags.isEmpty()) return;
		String now = typedNumber(tags.getFirst());
		String rules = rulesArea.getValue();
		String lead = (rules.isEmpty() || rules.endsWith("\n") ? rules : rules + "\n") + "{" + tags.getFirst() + "} > ";
		line.settle(box);
		line.colorRules = ruleLines(lead + now + " &c");
		typingRules = true;
		rebuildPanel();
		if (rulesArea == null) return;
		rulesArea.select(lead.length(), lead.length() + now.length());
		root.focusHandler().focus(rulesArea, UIComponent.FocusSource.MOUSE_CLICK);
		swallowDrag = true;
	}

	// the number now, as a player would type it
	private String typedNumber(String tag) {
		HudExpr.Parsed parsed = HudExpr.parse(tag);
		HudExpr.Result r;
		try {
			r = parsed.error() == null ? HudExpr.eval(parsed.node(), HudBoxText.values(hud.data(), true)) : null;
		} catch (HudExpr.Error e) {
			r = null;
		}
		if (r == null || r.value() == null) return "0";
		double v = r.value();
		return switch (r.type()) {
			case TIME -> HudFormat.thresholdText(HudValue.Kind.TIME, (long) v);
			case COINS -> HudFormat.format(HudValue.Kind.COINS, 0, v);
			case COLD -> Long.toString(Math.round(v));
			case INTEGER -> Math.abs(v) >= 10_000 ? HudFormat.format(HudValue.Kind.INTEGER, 1, Math.round(v)) : Long.toString(Math.round(v));
			default -> Math.abs(v - Math.rint(v)) < 1e-9 ? Long.toString(Math.round(v)) : HudFormat.format(HudValue.Kind.DECIMAL, 0, v);
		};
	}

	private int listWidth() {
		return Math.clamp(width * 20 / 100, LIST_MIN, LIST_MAX);
	}

	// beside the panel, own scroll. keys on top, values below
	private void buildValueList() {
		int w = listWidth();
		int inner = w - 8;
		listPanel = UIContainers.verticalFlow(Sizing.fixed(w), Sizing.fixed(panelHeight));
		listPanel.surface(PANEL);
		listPanel.padding(Insets.of(4));
		listPanel.gap(2);
		// headings + gaps would pass clicks to boxes below
		listPanel.mouseDown().subscribe((click, doubled) -> true);
		FlowLayout head = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		head.child(label("§fValues"));
		head.child(spacer());
		head.child(label("§8click to type"));
		listPanel.child(head);

		FlowLayout keys = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		keys.gap(1);
		for (String k : new String[] {">", ">=", "<", "<=", "=", "!="}) keys.child(key(k, " " + k + " ", "Compare, for colour rules"));
		for (String k : new String[] {"+", "-", "*", "/"}) keys.child(key(k, " " + k + " ", "Maths, inside { }"));
		keys.child(key("(", "(", "Maths, inside { }"));
		keys.child(key(")", ")", "Maths, inside { }"));
		listPanel.child(keys);

		FlowLayout swatches = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		swatches.gap(1);
		int size = Math.min(MENU_SWATCH, (inner - 15) / 16);
		for (int i = 0; i < 16; i++) {
			String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
			FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(size), Sizing.fixed(size));
			swatch.surface(Surface.flat(0xFF000000 | COLORS[i]).and(Surface.outline(0xFF000000)));
			swatch.cursorStyle(CursorStyle.HAND);
			tip(swatch, COLOR_NAMES[i] + " (&" + code + ")");
			swatch.mouseDown().subscribe((click, doubled) -> {
				typeRaw("&" + code);
				return true;
			});
			swatches.child(swatch);
		}
		listPanel.child(swatches);

		FlowLayout styles = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		styles.gap(1);
		String[][] codes = {{"&l", "Bold"}, {"&o", "Italic"}, {"&n", "Underline"}, {"&m", "Strikethrough"},
				{"&r", "Box label colour"}, {"&v", "Box value colour"}};
		for (String[] code : codes) styles.child(key(code[0], code[0], code[1] + ", until the next colour"));
		listPanel.child(styles);

		TextBoxComponent search = textField(listSearch, 32, typed -> {
			listSearch = typed;
			fillValues();
		});
		search.verticalSizing(Sizing.fixed(CHOICE));
		search.setHint(Component.literal("Search"));
		tip(search, "Search values by name");
		listPanel.child(search);

		listContent = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		listContent.padding(Insets.right(3));
		ScrollContainer<FlowLayout> scroll = UIContainers.verticalScroll(Sizing.fill(100), Sizing.expand(), listContent);
		scroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		scroll.scrollbarThiccness(2);
		listPanel.child(scroll);
		fillValues();
		root.child(listPanel);
	}

	private Chip key(String text, String typed, String tooltip) {
		Chip key = new Chip(text, 0xFFFFFF, () -> typeRaw(typed));
		key.surface(Surface.flat(0xFF3A3A3A).and(Surface.outline(0xFF000000)));
		key.padding(Insets.of(1, 1, 3, 3));
		tip(key, tooltip);
		return key;
	}

	private void fillValues() {
		if (listContent == null) return;
		listContent.clearChildren();
		formatChips.clear();
		mineGroup = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		mineSource = null;
		listContent.child(mineGroup);
		String query = listSearch.strip().toLowerCase(Locale.ROOT);
		int inner = listWidth() - 8 - 3;
		for (HudValue.Group group : HudValue.Group.values()) {
			List<HudValue> shown = new ArrayList<>();
			for (HudValue value : HudValue.values()) {
				if (value.group() == group && matches(value, query)) shown.add(value);
			}
			if (shown.isEmpty()) continue;
			listContent.child(section(group.displayName()));
			for (HudValue value : shown) {
				HoverRow row = new HoverRow();
				String now = strip(HudBoxText.paint(HudBoxText.text(value, value.defaultRange(), 0, hud.data(), true), "7", ""));
				String nowText = clip("§8" + now, inner / 3);
				row.child(label(clip("§f{" + value.tag() + "}", inner - font.width(nowText) - 4)));
				row.child(spacer());
				row.child(label(nowText));
				tip(row, "§f" + value.displayName() + "\n§7" + value.description() + "\n§eClick to type {" + value.tag() + "}");
				row.mouseDown().subscribe((click, doubled) -> {
					typeValue(value.tag());
					return true;
				});
				listContent.child(row);
				if (value.ranges().size() > 1) {
					FlowLayout options = optionRow();
					for (HudValue.Range range : value.ranges()) {
						Chip chip = new Chip(":" + range.tagName(), RANGE_TEXT, () -> typeOption(value, range.tagName()));
						tip(chip, "{" + value.tag() + ":" + range.tagName() + "}: " + range.description()
								+ ". Now " + strip(HudBoxText.paint(HudBoxText.text(value, range, 0, hud.data(), true), "7", "")));
						options.child(chip);
					}
					listContent.child(options);
				}
				List<String> formats = HudFormat.names(value.kind());
				if (value.hasFormatChoice() && !formats.isEmpty()) {
					FlowLayout options = optionRow();
					for (int i = 0; i < formats.size(); i++) {
						String format = formats.get(i);
						Chip chip = new Chip(":" + format, FORMAT_TEXT, () -> {
							if (!typingRules) typeOption(value, format);
						});
						tip(chip, "{" + value.tag() + ":" + format + "} shows as "
								+ HudFormat.example(value.kind(), i, HudBoxText.suffix(value, value.defaultRange()))
								+ ". Formats don't matter in rules.");
						formatChips.add(chip);
						options.child(chip);
					}
					listContent.child(options);
				}
			}
		}
		formatsOff = false;
	}

	private FlowLayout optionRow() {
		FlowLayout row = UIContainers.ltrTextFlow(Sizing.fill(100), Sizing.content());
		row.margins(Insets.of(0, 1, 6, 0));
		row.gap(3);
		return row;
	}

	private static boolean matches(HudValue value, String query) {
		return query.isEmpty() || value.tag().contains(query) || value.id().contains(query)
				|| value.displayName().toLowerCase(Locale.ROOT).contains(query);
	}

	// tags in the text as written, a rule typed from these always matches
	private void fillMine(String text) {
		mineSource = text;
		mineGroup.clearChildren();
		List<String> tags = HudBoxText.numberTags(text);
		if (tags.isEmpty()) return;
		mineGroup.child(section("In this line"));
		int inner = listWidth() - 8 - 3;
		for (String tag : tags) {
			HoverRow row = new HoverRow();
			row.child(label(clip("§f{" + tag + "}", inner)));
			tip(row, "Click to type it, f.e. to start a rule for it");
			row.mouseDown().subscribe((click, doubled) -> {
				typeValue(tag);
				return true;
			});
			mineGroup.child(row);
		}
	}

	private FreeTextArea typingArea() {
		return typingRules && rulesArea != null ? rulesArea : textArea;
	}

	private void typeValue(String tag) {
		typeEdit(area -> HudTyping.type(area.getValue(), area.cursor(), tag));
	}

	private void typeOption(HudValue value, String option) {
		typeEdit(area -> HudTyping.option(area.getValue(), area.cursor(), value, option));
	}

	private void typeRaw(String text) {
		typeEdit(area -> HudTyping.insert(area.getValue(), area.cursor(), text));
	}

	private void typeEdit(java.util.function.Function<FreeTextArea, HudTyping.Edit> edit) {
		closeMenu();
		FreeTextArea area = typingArea();
		if (area == null) return;
		HudTyping.Edit typed = edit.apply(area);
		if (typed.text().length() > area.limit()) noRoomFor = area.getValue();
		else area.set(typed.text(), typed.cursor());
		root.focusHandler().focus(area, UIComponent.FocusSource.MOUSE_CLICK);
		swallowDrag = true;
	}

	// click keeps the help open beside the panel while typing
	private void openGuide(boolean open) {
		guideOpen = open;
		guideButton.setMessage(Component.literal(open ? "§e?" : "?"));
	}

	// live, the text fields are polled
	private void refreshLinePage() {
		if (preview != null) {
			String text = HudBoxText.line(panelBox, previewLine, hud.data(), true);
			if (!previewLine.shown) text = "§8" + strip(text);
			if (!text.equals(previewRaw)) {
				previewRaw = text;
				// padding, + note and gap if hidden
				int room = inner() - 8 - (previewLine.shown ? 0 : font.width(HIDDEN_NOTE) + 4);
				preview.text(Component.literal(clip(text, room)));
			}
		}
		if (textArea == null) return;
		UIComponent focused = root.focusHandler().focused();
		if (focused == textArea) typingRules = false;
		else if (focused == rulesArea) typingRules = true;
		if (formatsOff != typingRules) {
			formatsOff = typingRules;
			for (Chip chip : formatChips) chip.color(typingRules ? FORMAT_OFF : FORMAT_TEXT);
		}
		String source = textArea.getValue();
		if (mineGroup != null && !source.equals(mineSource)) fillMine(source);
		if (noRoomFor != null && !noRoomFor.equals(source) && !noRoomFor.equals(rulesArea.getValue())) noRoomFor = null;
		boolean noRoom = noRoomFor != null;
		// same object = same text, the poll only swaps it on change
		boolean textChanged = source != hintSource;
		if (textChanged || noRoom != hintNoRoom) {
			hintSource = source;
			hintNoRoom = noRoom;
			List<String> problems = HudBoxText.problems(source);
			String problem = problems.isEmpty() ? "" : problems.getFirst().replace("§", "")
					+ (problems.size() > 1 ? " (and " + (problems.size() - 1) + " more)" : "");
			String hint = noRoom ? "§cNo room for that, the text is " + TEXT_LIMIT + " and the rules " + RULES_LIMIT + " characters max."
					: problems.isEmpty() ? TEXT_HINT
					: "§c" + problem + (problem.endsWith("?") ? " " : ". ") + "It shows as typed.";
			if (!hint.equals(textHintText)) {
				textHintText = hint;
				textHint.text(Component.literal(hint));
			}
		}
		String rulesText = rulesArea.getValue();
		HudData data = hud.data();
		if (!textChanged && rulesText == hintRules && data == hintData) return;
		hintRules = rulesText;
		hintData = data;
		List<String> hints = HudRules.hints(ruleLines(rulesText), source, HudBoxText.values(data, true));
		if (!hints.equals(ruleHintLines)) {
			ruleHintLines = hints;
			ruleHints.clearChildren();
			for (String hint : hints) {
				LabelComponent label = label(hint);
				label.maxWidth(inner());
				ruleHints.child(label);
			}
		}
	}

	// grey example in the empty rules field
	private void drawRulesPlaceholder(GuiGraphicsExtractor graphics) {
		if (rulesArea == null || menu != null || !rulesArea.getValue().isEmpty() || panelScroll == null) return;
		int y = rulesArea.y() + 4;
		if (y < panelScroll.y() || y + font.lineHeight > panelBottom()) return;
		String source = textArea.getValue();
		HudData data = hud.data();
		if (source != placeholderSource || data != placeholderData) {
			placeholderSource = source;
			placeholderData = data;
			List<String> tags = HudBoxText.numberTags(source);
			String example = tags.isEmpty() ? "{coins} > 2M &a" : "{" + tags.getFirst() + "} > " + typedNumber(tags.getFirst()) + " &c";
			placeholderText = clip("§8f.e. " + example, rulesArea.width() - 8);
		}
		graphics.text(font, placeholderText, rulesArea.x() + 4, y, 0xFFFFFFFF, false);
	}

	private void openMenu(UIComponent anchor, Consumer<FlowLayout> fill) {
		closeMenu();
		tipTarget = menuTips;
		menu = UIContainers.verticalFlow(Sizing.content(), Sizing.content());
		menu.surface(MENU);
		menu.padding(Insets.of(2));
		// headings + gaps would pass clicks to the panel below
		menu.mouseDown().subscribe((click, doubled) -> true);
		menu.mouseScroll().subscribe((x, y, amount) -> true);
		menuAnchor = anchor;
		try {
			fill.accept(menu);
		} finally {
			tipTarget = panelTips;
		}
		menu.positioning(Positioning.absolute(anchor.x() - root.x() - PADDING,
				anchor.y() + anchor.height() + 1 - root.y() - PADDING));
		// owo lays it out right away, so no frame below the screen
		root.child(menu);
		fitMenu();
		rebuiltByClick = true;
	}

	// kept on screen, sizes known after layout
	private void fitMenu() {
		if (menu == null || menu.width() == 0) return;
		int x = Math.max(PADDING, Math.min(menu.x(), width - PADDING - menu.width()));
		int y = menu.y();
		if (y + menu.height() > height - PADDING) y = Math.max(PADDING, menuAnchor.y() - 1 - menu.height());
		if (x != menu.x() || y != menu.y()) menu.positioning(Positioning.absolute(x - root.x() - PADDING, y - root.y() - PADDING));
	}

	// beside the panel, same height. sizes known after layout
	private void fitList() {
		if (listPanel == null || panelScroll == null || panelScroll.width() == 0) return;
		boolean panelRight = panelScroll.x() > width / 2;
		int x = panelRight ? panelScroll.x() - 2 - listPanel.width() : panelScroll.x() + panelScroll.width() + 2;
		int y = panelScroll.y();
		if (x != listPanel.x() || y != listPanel.y()) listPanel.positioning(Positioning.absolute(x - root.x() - PADDING, y - root.y() - PADDING));
		if (listPanel.height() != panelScroll.height()) listPanel.verticalSizing(Sizing.fixed(panelScroll.height()));
	}

	private boolean inList(double x, double y) {
		return listPanel != null && listPanel.isInBoundingBox(x, y);
	}

	private void closeMenu() {
		if (menu == null) return;
		menuTips.clear();
		root.removeChild(menu);
		menu = null;
		menuAnchor = null;
	}

	private static LabelComponent menuTitle(String text) {
		LabelComponent title = label("§7" + text);
		title.margins(Insets.of(2, 1, 2, 2));
		return title;
	}

	private static FlowLayout menuEntry(String text, Runnable action) {
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

	// small clickable text w/ a hover, keys + options in the value list
	private final class Chip extends FlowLayout {
		private final LabelComponent label;

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

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
			if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, HOVER);
		}
	}

	private TextBoxComponent textField(String text, int maxLength, Consumer<String> apply) {
		TextBoxComponent field = UIComponents.textBox(Sizing.expand());
		field.verticalSizing(Sizing.fixed(BUTTON));
		field.setMaxLength(maxLength);
		field.text(text);
		String[] last = {text};
		fields.add(new Field(field::getValue, typed -> {
			if (typed.equals(last[0])) return;
			last[0] = typed;
			apply.accept(typed);
		}));
		// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
		homeFields.add(field);
		return field;
	}

	private int lineHandleAt(double x, double y) {
		// rows scrolled out of view keep their bounds, under the toolbar on narrow screens
		if (!inPanel(x, y)) return -1;
		for (int i = 0; i < lineHandles.size(); i++) {
			if (lineHandles.get(i).isInBoundingBox(x, y)) return i;
		}
		return -1;
	}

	// boxColor null = no "Box" choice. shows the colour it ends up as
	private ButtonComponent colorButton(String current, String boxColor, Consumer<String> setColor) {
		return colorButton(current, boxColor, null, setColor);
	}

	// valueDefault = the values own colour, offered back in the menu
	private ButtonComponent colorButton(String current, String boxColor, String valueDefault, Consumer<String> setColor) {
		String text = current == null ? "§fBox §7▾" : "§7▾";
		int shown = colorIndex(current != null ? current : boxColor);
		ButtonComponent button = drawnButton("Colour: " + COLOR_NAMES[shown], b -> colorMenu(b, current, boxColor, valueDefault, setColor));
		button.verticalSizing(Sizing.fixed(CHOICE));
		button.horizontalSizing(Sizing.fixed(4 + 8 + 3 + font.width(text) + 4));
		button.renderer((graphics, b, delta) -> {
			PackButton.RENDERER.draw(graphics, b, delta);
			int x = b.getX() + 4;
			int y = b.getY() + (b.getHeight() - 8) / 2;
			graphics.fill(x - 1, y - 1, x + 9, y + 9, 0xFF000000);
			graphics.fill(x, y, x + 8, y + 8, 0xFF000000 | COLORS[shown]);
			graphics.text(font, text, x + 11, y, 0xFFFFFFFF, true);
		});
		return button;
	}

	private void colorMenu(UIComponent anchor, String current, String boxColor, String valueDefault,
			Consumer<String> setColor) {
		openMenu(anchor, m -> {
			Consumer<String> pick = code -> {
				closeMenu();
				setColor.accept(code);
				rebuildPanel();
			};
			if (boxColor != null) {
				m.child(menuEntry((current == null ? "§e" : "§f") + "Box colour §7(" + COLOR_NAMES[colorIndex(boxColor)] + ")",
						() -> pick.accept(null)));
			}
			if (valueDefault != null) {
				m.child(menuEntry((valueDefault.equals(current) ? "§e" : "§f") + "Default colour §7("
						+ COLOR_NAMES[colorIndex(valueDefault)] + ")", () -> pick.accept(valueDefault)));
			}
			swatchGrid(m, current, pick);
		});
	}

	// dark row + light row, like the code order
	private void swatchGrid(FlowLayout menu, String selected, Consumer<String> pick) {
		for (int half = 0; half < 2; half++) {
			FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
			swatches.gap(2);
			swatches.margins(Insets.of(2, 1, 3, 3));
			for (int i = half * 8; i < half * 8 + 8; i++) {
				String code = String.valueOf(HudLayout.COLOR_CODES.charAt(i));
				FlowLayout swatch = UIContainers.horizontalFlow(Sizing.fixed(MENU_SWATCH), Sizing.fixed(MENU_SWATCH));
				swatch.surface(Surface.flat(0xFF000000 | COLORS[i])
						.and(Surface.outline(code.equals(selected) ? OUTLINE_SELECTED : 0xFF000000)));
				tip(swatch, COLOR_NAMES[i]);
				swatch.cursorStyle(CursorStyle.HAND);
				swatch.mouseDown().subscribe((click, doubled) -> {
					pick.accept(code);
					return true;
				});
				swatches.child(swatch);
			}
			menu.child(swatches);
		}
	}

	private static int colorIndex(String code) {
		return Math.max(0, HudLayout.COLOR_CODES.indexOf(code == null || code.isEmpty() ? 'f' : code.charAt(0)));
	}

	private FlowLayout keyRow(String name, UIComponent child) {
		FlowLayout row = row(name, child);
		row.children().getFirst().horizontalSizing(Sizing.fixed(KEY));
		row.margins(Insets.vertical(1));
		return row;
	}

	// HUD › box › page. here null = the box page
	private FlowLayout crumbs(Box box, String here) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.bottom(2));
		String gap = "§8 › ";
		String name = strip(HudBoxText.tags(box.title, hud.data(), true)).strip();
		if (name.isEmpty()) name = "Box";
		// crumb padding 2px each
		int room = inner() - font.width("HUD") - font.width(gap) - 4;
		Crumb home = new Crumb("HUD", () -> {
			selected = null;
			rebuildPanel();
		});
		tip(home, "All boxes. Closes this panel, click a box to open it again.");
		row.child(home);
		row.child(label(gap));
		if (here == null) {
			row.child(label(clip("§f" + name, room)));
			return row;
		}
		room -= font.width(gap) + 2;
		String last = clip("§f" + here, room - Math.min(font.width(name), CRUMB_MIN));
		Crumb boxCrumb = new Crumb(clip(name, room - font.width(last)), this::backToLines);
		tip(boxCrumb, "Back to the box (Esc)");
		row.child(boxCrumb);
		row.child(label(gap));
		row.child(label(last));
		return row;
	}

	private final class Crumb extends FlowLayout {
		Crumb(String text, Runnable action) {
			super(Sizing.content(), Sizing.content(), Algorithm.HORIZONTAL);
			padding(Insets.horizontal(1));
			cursorStyle(CursorStyle.HAND);
			child(label("§7" + text));
			mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) return false;
				action.run();
				return true;
			});
		}

		@Override
		public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
			if (isInBoundingBox(mouseX, mouseY)) graphics.fill(x, y, x + width, y + height, HOVER);
			super.draw(graphics, mouseX, mouseY, partialTicks, delta);
		}
	}

	private static FlowLayout heading(String text) {
		FlowLayout heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		heading.surface(DIVIDER);
		heading.padding(Insets.top(4));
		heading.margins(Insets.top(4));
		heading.verticalAlignment(VerticalAlignment.CENTER);
		heading.child(label("§f" + text));
		return heading;
	}

	// own label w/ a gap, clicking it ticks the box too. text null = box only
	private FlowLayout check(String text, boolean on, Consumer<Boolean> set) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		row.gap(4);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.vertical(1));
		SmallCheckboxComponent box = UIComponents.smallCheckbox(null);
		box.checked(on);
		box.onChanged().subscribe(set::accept);
		row.child(box);
		if (text != null) {
			LabelComponent name = label(text);
			name.cursorStyle(CursorStyle.HAND);
			name.mouseDown().subscribe((click, doubled) -> {
				if (click.button() != 0) return false;
				box.checked(!box.checked());
				return true;
			});
			row.child(name);
		}
		return row;
	}

	private FlowLayout row(String name, UIComponent... children) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(2);
		row.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent label = label("§7" + name);
		label.horizontalSizing(Sizing.fixed(ROW_LABEL));
		row.child(label);
		for (var child : children) row.child(child);
		return row;
	}

	private ButtonComponent choice(String text, boolean on, Runnable action) {
		ButtonComponent choice = button((on ? "§e" : "§f") + text, b -> {
			action.run();
			rebuildPanel();
		});
		choice.verticalSizing(Sizing.fixed(CHOICE));
		return choice;
	}

	// cut to width w/ "…", never mid § code. binary search, caxton shapes every width call
	private String clip(String text, int max) {
		if (font.width(text) <= max) return text;
		int low = 0;
		int high = text.length();
		while (low < high) {
			int mid = (low + high + 1) >>> 1;
			if (font.width(text.substring(0, codeSafe(text, mid)) + "…") <= max) low = mid;
			else high = mid - 1;
		}
		return text.substring(0, codeSafe(text, low)) + "…";
	}

	private static int codeSafe(String text, int cut) {
		return cut > 0 && text.charAt(cut - 1) == '§' ? cut - 1 : cut;
	}

	private static String strip(String text) {
		return text.replaceAll("§.", "");
	}

	private FlowLayout place(String name, boolean on, Consumer<Boolean> set, String tooltip) {
		FlowLayout check = check(name, on, set);
		tip(check, tooltip);
		return check;
	}

	// pixels bc fonts ↶ is tiny
	private static ButtonComponent iconButton(boolean redo, Consumer<ButtonComponent> action) {
		ButtonComponent button = drawnButton(redo ? "Redo" : "Undo", action);
		button.horizontalSizing(Sizing.fixed(BUTTON + 4));
		button.renderer((graphics, b, delta) -> {
			PackButton.RENDERER.draw(graphics, b, delta);
			drawIcon(graphics, UNDO_ICON, b.getX() + (b.getWidth() - 8) / 2, b.getY() + (b.getHeight() - UNDO_ICON.length) / 2,
					redo, b.isActive() ? 0xFFFFFFFF : 0xFF707070);
		});
		return button;
	}

	private interface Icon {
		void draw(GuiGraphicsExtractor graphics, int x, int y, int height);
	}

	// no font glyph, caxton safe
	private static final Icon REMOVE = (graphics, x, y, h) -> graphics.blitSprite(RenderPipelines.GUI_TEXTURED, REMOVE_ICON, x,
			y + (h - 7) / 2, 8, 7);

	private static Icon pixels(String[] icon) {
		return (graphics, x, y, h) -> drawIcon(graphics, icon, x, y + (h - icon.length) / 2, false, 0xFFFFFFFF);
	}

	// 8 wide icon + text. text "" = icon only
	private ButtonComponent iconTextButton(Icon icon, String text, String name, Consumer<ButtonComponent> action) {
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

	private static void drawIcon(GuiGraphicsExtractor graphics, String[] icon, int x0, int y0, boolean mirror, int color) {
		for (int row = 0; row < icon.length; row++) {
			for (int col = 0; col < 8; col++) {
				if (icon[row].charAt(col) != 'X') continue;
				int x = x0 + (mirror ? 7 - col : col);
				graphics.fill(x, y0 + row, x + 1, y0 + row + 1, color);
			}
		}
	}

	private static ButtonComponent button(String text, Consumer<ButtonComponent> action) {
		ButtonComponent button = PackButton.button(Component.literal(text), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	// owo draws the message too, so empty msg + narrator name
	private static ButtonComponent drawnButton(String name, Consumer<ButtonComponent> action) {
		ButtonComponent button = new DrawnButton(strip(name), action);
		button.horizontalSizing(Sizing.content(4));
		button.verticalSizing(Sizing.fixed(BUTTON));
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

	private static FlowLayout spacer() {
		return UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1));
	}

	private static LabelComponent section(String text) {
		LabelComponent label = label("§7" + text);
		label.margins(Insets.top(4));
		return label;
	}

	private static LabelComponent label(String text) {
		return new SafeLabel(Component.literal(text));
	}

	private static String name(Enum<?> option) {
		String lower = option.name().toLowerCase(Locale.ROOT);
		return option == Align.CENTER ? "Centre" : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
	}
}
