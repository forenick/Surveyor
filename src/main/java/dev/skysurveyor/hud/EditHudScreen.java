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
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Size;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.inject.GreedyInputUIComponent;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.hud.EditorParts.HoverRow;
import dev.skysurveyor.hud.HudLayout.Align;
import dev.skysurveyor.hud.HudLayout.Anchor;
import dev.skysurveyor.hud.HudLayout.Background;
import dev.skysurveyor.hud.HudLayout.Box;
import dev.skysurveyor.hud.HudLayout.Line;
import dev.skysurveyor.hud.HudLayout.ShowAs;
import dev.skysurveyor.journal.ToastText;
import dev.skysurveyor.screen.ModScreen;
import dev.skysurveyor.screen.PackButton;
import dev.skysurveyor.screen.ScreenParts;
import dev.skysurveyor.settings.SummarySettings;

import static dev.skysurveyor.hud.EditorParts.BUTTON;
import static dev.skysurveyor.hud.EditorParts.CHOICE;
import static dev.skysurveyor.hud.EditorParts.COLOR_NAMES;
import static dev.skysurveyor.hud.EditorParts.OUTLINE_SELECTED;
import static dev.skysurveyor.hud.EditorParts.PANEL;
import static dev.skysurveyor.hud.EditorParts.PANEL_FILL;
import static dev.skysurveyor.hud.EditorParts.PANEL_OUTLINE;
import static dev.skysurveyor.hud.EditorParts.PENCIL_ICON;
import static dev.skysurveyor.hud.EditorParts.REMOVE;
import static dev.skysurveyor.hud.EditorParts.RESET_ICON;
import static dev.skysurveyor.hud.EditorParts.button;
import static dev.skysurveyor.hud.EditorParts.check;
import static dev.skysurveyor.hud.EditorParts.crumb;
import static dev.skysurveyor.hud.EditorParts.drawIcon;
import static dev.skysurveyor.hud.EditorParts.drawnButton;
import static dev.skysurveyor.hud.EditorParts.heading;
import static dev.skysurveyor.hud.EditorParts.iconTextButton;
import static dev.skysurveyor.hud.EditorParts.menuEntry;
import static dev.skysurveyor.hud.EditorParts.menuTitle;
import static dev.skysurveyor.hud.EditorParts.name;
import static dev.skysurveyor.hud.EditorParts.pixels;
import static dev.skysurveyor.hud.EditorParts.spacer;
import static dev.skysurveyor.hud.EditorParts.undoButton;
import static dev.skysurveyor.screen.ScreenParts.label;
import static dev.skysurveyor.screen.ScreenParts.strip;

public final class EditHudScreen extends BaseOwoScreen<FlowLayout> implements ModScreen {
	// no outline, so it doesnt look like a field
	private static final Surface PREVIEW = Surface.flat(0x8C000000)
			.and((graphics, c) -> graphics.fill(c.x(), c.y(), c.x() + 2, c.y() + c.height(), 0x40FFFFFF));
	// fits 480x270, grows on wide screens (gui scale 2)
	private static final int PANEL_MIN = 204;
	private static final int PANEL_MAX = 280;
	// text columns, measured in build(). these are the vanilla font minimums
	private static final int ROW_LABEL_MIN = 62;
	private static final int KEY_MIN = 38;
	// every row() and keyRow() name, caxton fonts are wider
	private static final String[] ROW_NAMES = {"Size", "Background", "Align", "Show in", "Labels", "Values"};
	private static final String[] KEY_NAMES = {"Mode", "Shaft", "Corpse", "PB", "Period", "Format", "Colour", "Show", "Rows",
			"Amount", "Spent", "Outside"};
	private static final int OUTLINE = 0x70FFFFFF;
	// light, so the preview stays readable
	private static final int HIDDEN_SHADE = 0x60000000;
	private static final int GUIDE = 0xFFFF55FF;
	private static final int DROP_LINE = 0xFFFFFF55;
	private static final int DRAGGED_LINE = 0x30FFFFFF;
	// box name keeps at least this much before the page name gets cut
	private static final int CRUMB_MIN = 40;
	// batches scroll/nudge spam into 1 undo step
	private static final long SETTLE_MILLIS = 400;
	// also the size slider step
	private static final float SCALE_STEP = 0.1f;
	static final int PADDING = 4;
	private static final int[][] TOOLBAR_SPOTS = {{50, 0}, {50, 100}, {0, 0}, {100, 0}, {0, 100}, {100, 100}};
	// no right middle, hypixels scoreboard sits there
	private static final Anchor[][] NEW_BOX_SPOTS = {{Anchor.START, Anchor.MIDDLE}, {Anchor.START, Anchor.START},
			{Anchor.START, Anchor.END}, {Anchor.END, Anchor.END}, {Anchor.MIDDLE, Anchor.END}, {Anchor.MIDDLE, Anchor.MIDDLE}};

	private static final String HIDDEN_NOTE = "hidden";
	// display choice
	private static final int LINK = 6;
	// short, fits 480 wide (1080p auto scale)
	private static final String[] HINTS = {"§7Drag a box to move it, hold Ctrl to place it freely, click it to edit",
			"§7Scroll on the selected box to resize it, arrow keys nudge it",
			"§7Changes save by themselves. Click ? in the toolbar for help"};
	// from the bottom
	private static final int HINT_UP = 64;
	// hint lines backdrop, panel colour but see-through
	private static final int HINT_FILL = 0xC0000000 | (PANEL_FILL & 0xFFFFFF);
	// value list beside the panel, display choice
	private static final int LIST_MIN = 170;
	private static final int LIST_MAX = 190;
	// line row period tag from here, display choice
	private static final int TAG_MIN_PANEL = 240;

	private final Screen parent;
	private final LiveHud hud;
	private final Map<Box, HudRenderer.Bounds> drawn = new IdentityHashMap<>();
	// measured boxes, dropped on any edit or new hud data
	private final Map<Box, HudRenderer.Measured> measured = new IdentityHashMap<>();
	private HudData measuredData;
	// one instance till its moved, drawn is by identity
	private Box defaultToast;
	private FlowLayout root;
	private FlowLayout toolbar;
	private LabelComponent toolbarTitle;
	private final EditorMenu menu = new EditorMenu(this);
	private final AddLinePage addLinePage = new AddLinePage(this);
	// the built panel, new one on every rebuild
	private Page page = new Page();
	// last frame, so a rebuild doesnt flicker
	private int panelHeight = 200;
	// box id + page, so each page keeps its scroll
	private final Map<String, Double> scrollMemory = new HashMap<>();
	private int scrollToRow = -1;
	private ButtonComponent resetButton;
	private ButtonComponent undoButton;
	private ButtonComponent redoButton;
	private int lineDrag = -1;
	private int lineGap;
	private boolean mouseHeld;
	private HudHistory history;
	private String unsettled;
	private long unsettledSince;
	// any input or polled text since the last json check. idle = no json each frame
	private boolean dirty;
	// tooltips drawn by us, owo's dont wrap or stay on screen -> might have to look into this later tho
	private final Map<UIComponent, List<FormattedCharSequence>> toolbarTips = new IdentityHashMap<>();
	private int rowLabel = ROW_LABEL_MIN;
	private int keyWidth = KEY_MIN;
	// colour buttons on this line page
	private String boxWord = "Box";
	private final EditorGuide help = new EditorGuide(this);
	private ButtonComponent guideButton;
	private boolean resetArmed;
	// page state, reset when another box is picked
	private String pageBox;
	private boolean picking;
	// by index bc undo makes new line objects
	private int editedIndex = -1;
	// value list clicks type here
	private boolean typingRules;
	private boolean listOpen = true;
	private String listSearch = "";
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

	// everything one built panel owns, dropped whole on rebuild
	private static final class Page {
		PanelScroll scroll;
		FlowLayout panel;
		Box box;
		String key;
		int builtWidth;
		final Map<UIComponent, List<FormattedCharSequence>> tips = new IdentityHashMap<>();
		final List<Field> fields = new ArrayList<>();
		final List<TextBoxComponent> homeFields = new ArrayList<>();
		final List<FlowLayout> lineRows = new ArrayList<>();
		final List<UIComponent> lineHandles = new ArrayList<>();
		DiscreteSliderComponent scaleSlider;
		FlowLayout deleteRow;
		ButtonComponent deleteButton;
		boolean deleteArmed;
		LabelComponent preview;
		Line previewLine;
		// unclipped, so clip only runs on change
		String previewRaw;
		AdvancedPage advanced;
	}

	public EditHudScreen(Screen parent, LiveHud hud) {
		super(Component.literal("Edit HUD"));
		this.parent = parent;
		this.hud = hud;
		// they found the editor, saved on close
		if (hud.layout().dropHints()) hud.setLayout(hud.layout());
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
		rowLabel = EditorFit.widest(ROW_LABEL_MIN, widths(ROW_NAMES));
		keyWidth = EditorFit.widest(KEY_MIN, widths(KEY_NAMES));
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
		undoButton = undoButton(false, b -> undo(false));
		tip(toolbarTips, undoButton, "Undo (Ctrl+Z)");
		toolbar.child(undoButton);
		redoButton = undoButton(true, b -> undo(true));
		tip(toolbarTips, redoButton, "Redo (Ctrl+Shift+Z)");
		toolbar.child(redoButton);
		ButtonComponent newBox = button("+ New box", b -> newBox());
		tip(toolbarTips, newBox, "Add an empty box and pick its first line");
		toolbar.child(newBox);
		ButtonComponent preset = button("+ Preset", this::presetMenu);
		tip(toolbarTips, preset, "Add a ready-made box");
		toolbar.child(preset);
		guideButton = button("?", b -> openGuide(!help.isOpen()));
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
			edited();
		}
		page.homeFields.forEach(ScreenParts::rehome);
		page.homeFields.clear();
		syncFields();
		refreshLinePage();
		recordChanges(false);
		undoButton.active(history.canUndo());
		redoButton.active(history.canRedo());
		drawn.clear();
		// before the boxes, so it never covers one more than the text does
		if (selected == null) drawHintBackdrop(graphics);
		Map<Box, HudRenderer.Bounds> placed = placeAll();
		for (Box box : hud.layout().boxes) {
			HudRenderer.Measured size = measured(box);
			HudRenderer.Bounds bounds = placed.get(box);
			drawn.put(box, bounds);
			HudRenderer.draw(graphics, font, box, size, bounds);
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
		for (Box box : hud.layout().boxes) drawLink(graphics, box);
		for (HudSnap.Guide guide : guides) {
			graphics.fill(guide.x(), guide.y(), guide.x() + guide.width(), guide.y() + guide.height(), GUIDE);
			if (guide.label() != null) graphics.text(font, guide.label(), guide.labelX(), guide.labelY(), GUIDE, true);
		}
		if (selected == null) {
			for (int i = 0; i < HINTS.length; i++) {
				graphics.text(font, HINTS[i], (width - font.width(HINTS[i])) / 2, height - HINT_UP + i * 11, 0xFFFFFFFF, true);
			}
		}
		placeToolbar();
		// panel draws over boxes from here
		graphics.nextStratum();
		super.extractRenderState(graphics, mouseX, mouseY, delta);
		fitPanel();
		fitList();
		menu.fit();
		if (page.advanced != null && !menu.isOpen() && page.scroll != null) {
			page.advanced.drawPlaceholder(graphics, page.scroll.y(), panelBottom());
		}
		if (lineDrag >= 0) {
			scrollWhileDragging(mouseY);
			graphics.nextStratum();
			drawLineDrag(graphics);
		}
		drawTip(graphics, mouseX, mouseY);
	}

	// same data + no edit = same rows, so not every frame
	private HudRenderer.Measured measured(Box box) {
		HudData data = hud.data();
		if (data != measuredData) {
			measured.clear();
			measuredData = data;
		}
		return measured.computeIfAbsent(box, b -> HudRenderer.measure(font, b, rows(b)));
	}

	// replaces owo's tooltip, smallest hovered component wins
	private void drawTip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		boolean onGuide = help.isOpen() && help.over(mouseX, mouseY);
		if (dragging != null || lineDrag >= 0) return;
		// card wins over panel tips under it, else it cant be scrolled
		Map<UIComponent, List<FormattedCharSequence>> tips = menu.contains(mouseX, mouseY) ? menu.tips()
				: onGuide ? Map.of()
				: inPanel(mouseX, mouseY) || inList(mouseX, mouseY) ? page.tips
				: toolbar.isInBoundingBox(mouseX, mouseY) ? toolbarTips : Map.of();
		List<FormattedCharSequence> lines = ScreenParts.pick(tips, mouseX, mouseY, c -> true);
		if (lines != null) {
			graphics.setTooltipForNextFrame(font, lines, ScreenParts.FIT, mouseX, mouseY, true);
		} else if (help.isOpen()) {
			help.draw(graphics, toolbar, page.scroll);
		}
	}

	private void drawHintBackdrop(GuiGraphicsExtractor graphics) {
		int widest = 0;
		for (String hint : HINTS) widest = Math.max(widest, font.width(hint));
		int x = (width - widest) / 2 - 4;
		int y = height - HINT_UP - 3;
		// 3 lines 11 apart, text + shadow 9 tall
		int h = 2 * 11 + 9 + 5;
		graphics.fill(x, y, x + widest + 8, y + h, HINT_FILL);
		graphics.outline(x, y, widest + 8, h, PANEL_OUTLINE);
	}

	private int[] widths(String[] texts) {
		int[] widths = new int[texts.length];
		for (int i = 0; i < texts.length; i++) widths[i] = font.width(texts[i]);
		return widths;
	}

	void tip(UIComponent c, String text) {
		tip(page.tips, c, text);
	}

	private void tip(Map<UIComponent, List<FormattedCharSequence>> target, UIComponent c, String text) {
		target.put(c, EditorParts.tipLines(font, text));
	}

	// panel doesnt scroll by itself during a line drag
	private void scrollWhileDragging(int mouseY) {
		List<FlowLayout> lineRows = page.lineRows;
		PanelScroll panelScroll = page.scroll;
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
		for (FlowLayout row : page.lineRows) {
			if (mouseY > row.y() + row.height() / 2.0) lineGap++;
		}
	}

	// editor shows every box, so none takes no room
	private Map<Box, HudRenderer.Bounds> placeAll() {
		return HudPlace.place(hud.layout().boxes, box -> HudRenderer.size(measured(box), box), box -> true, width, height);
	}

	private static HudSnap.Rect rect(Box box, HudRenderer.Bounds b) {
		return new HudSnap.Rect(b.x(), b.y(), b.width(), b.height(), box.anchorX, box.anchorY);
	}

	// clip across the gap of 2 stuck boxes
	private void drawLink(GuiGraphicsExtractor graphics, Box box) {
		Box parent = HudPlace.parent(box, hud.layout().boxes);
		HudRenderer.Bounds b = drawn.get(box);
		HudRenderer.Bounds p = parent == null ? null : drawn.get(parent);
		if (b == null || p == null) return;
		// pushed over each other by the screen edge = no gap to mark
		if (b.overlapArea(p) > 0) return;
		boolean vertical = box.stickSide.vertical();
		HudSnap.Rect r = vertical ? rect(box, b).transposed() : rect(box, b);
		HudSnap.Rect q = vertical ? rect(parent, p).transposed() : rect(parent, p);
		// no overlap across = mark would float between them
		if (Math.max(r.y(), q.y()) >= Math.min(r.y() + r.height(), q.y() + q.height())) return;
		int from = (int) Math.min(r.x() + r.width(), q.x() + q.width());
		int to = (int) Math.max(r.x(), q.x());
		HudSnap.Guide link = new HudSnap.Guide(from, HudSnap.middle(r.y(), r.height(), q.y(), q.height()) - LINK / 2, to - from,
				LINK, null, 0, 0);
		if (vertical) link = link.transposed();
		graphics.fill(link.x(), link.y(), link.x() + link.width(), link.y() + link.height(), GUIDE);
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
		List<FlowLayout> lineRows = page.lineRows;
		if (lineDrag >= lineRows.size()) return;
		FlowLayout from = lineRows.get(lineDrag);
		graphics.fill(from.x(), from.y(), from.x() + from.width(), from.y() + from.height(), DRAGGED_LINE);
		if (lineGap == lineDrag || lineGap == lineDrag + 1) return;
		FlowLayout last = lineRows.getLast();
		// rows 1px apart, bar covers it +1 each side
		int y = lineGap < lineRows.size() ? lineRows.get(lineGap).y() - 1 : last.y() + last.height();
		graphics.fill(from.x(), y - 1, from.x() + from.width(), y + 1, DROP_LINE);
	}

	// input may have changed the layout, json + rows again
	private void edited() {
		dirty = true;
		measured.clear();
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
		try {
			return clicked(click, doubled);
		} finally {
			edited();
		}
	}

	private boolean clicked(MouseButtonEvent click, boolean doubled) {
		// prior typing/scrolling = own undo step
		syncFields();
		recordChanges(true);
		mouseHeld = true;
		swallowDrag = false;
		boolean left = click.button() == 0;
		boolean rebuilt = rebuiltByClick;
		rebuiltByClick = false;
		if (doubled && rebuilt && overPanel(click.x(), click.y())) return true;
		if (menu.isOpen()) {
			// rows + checkboxes act on any button, panel is left click only
			if (menu.contains(click.x(), click.y())) return !left || super.mouseClicked(click, doubled);
			// 1st click outside only closes it
			closeMenu();
			return true;
		}
		if (resetArmed && !resetButton.isInBoundingBox(click.x(), click.y())) armReset(false);
		if (page.deleteArmed && !page.deleteButton.isInBoundingBox(click.x(), click.y())) armDelete(false);
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
		try {
			return dragged(drag, dx, dy);
		} finally {
			edited();
		}
	}

	private boolean dragged(MouseButtonEvent drag, double dx, double dy) {
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
		HudSnap.Stick stick = null;
		Box stickTo = null;
		guides = List.of();
		if (!minecraft.hasControlDown()) {
			// boxes hanging on this one move w/ it
			List<Box> otherBoxes = drawn.keySet().stream()
					.filter(box -> box != dragging && !HudPlace.follows(box, dragging, hud.layout().boxes)).toList();
			List<HudSnap.Rect> others = otherBoxes.stream().map(box -> rect(box, drawn.get(box))).toList();
			HudSnap.Result snap = HudSnap.snap(new HudSnap.Rect(x, y, bounds.width(), bounds.height(), dragging.anchorX,
					dragging.anchorY), others, width, height, HudLayout.MARGIN);
			x = (int) Math.round(snap.x());
			y = (int) Math.round(snap.y());
			anchorX = snap.anchorX();
			anchorY = snap.anchorY();
			guides = snap.guides();
			stick = snap.stick();
			if (stick != null) stickTo = otherBoxes.get(stick.other());
		}
		x = Anchor.clamp(x, bounds.width(), width);
		y = Anchor.clamp(y, bounds.height(), height);
		HudRenderer.Bounds at = new HudRenderer.Bounds(x, y, bounds.width(), bounds.height());
		Box toast = toastBox();
		// toast is no box
		if (stickTo != null && dragging != toast && stickTo != toast) {
			boolean vertical = stick.side().vertical();
			Anchor across = stick.cross() != null ? stick.cross() : vertical ? stickTo.anchorX : stickTo.anchorY;
			HudPlace.stick(dragging, stickTo, stick.side(), across, drawn.get(stickTo), at, width, height);
			return true;
		}
		HudPlace.free(dragging, at, width, height);
		if (anchorX != null) {
			dragging.anchorX = anchorX;
			dragging.offsetX = anchorX.offset(x, bounds.width(), width);
		}
		if (anchorY != null) {
			dragging.anchorY = anchorY;
			dragging.offsetY = anchorY.offset(y, bounds.height(), height);
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		try {
			return scrolled(mouseX, mouseY, horizontal, vertical);
		} finally {
			edited();
		}
	}

	private boolean scrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		// panel would scroll away from it
		if (menu.isOpen() && !menu.contains(mouseX, mouseY)) closeMenu();
		if (vertical != 0 && help.isOpen() && help.over(mouseX, mouseY)) {
			help.scroll(vertical);
			return true;
		}
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
		if (page.scaleSlider != null) page.scaleSlider.setFromDiscreteValue(selected.scale);
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent key) {
		try {
			return pressed(key);
		} finally {
			edited();
		}
	}

	private boolean pressed(KeyEvent key) {
		// esc goes back one step before it closes the editor
		if (key.key() == GLFW.GLFW_KEY_ESCAPE) {
			if (menu.isOpen()) {
				closeMenu();
				return true;
			}
			if (help.isOpen()) {
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
				HudRenderer.Bounds moved = new HudRenderer.Bounds(x, y, bounds.width(), bounds.height());
				Box parent = HudPlace.parent(selected, hud.layout().boxes);
				if (parent == null) {
					selected.offsetX = selected.anchorX.offset(x, bounds.width(), width);
					selected.offsetY = selected.anchorY.offset(y, bounds.height(), height);
				} else if (selected.stickSide.vertical() ? dy == 0 : dx == 0) {
					// along the other box, stays stuck
					HudPlace.stick(selected, parent, selected.stickSide, selected.stickSide.vertical() ? selected.anchorX : selected.anchorY,
							placeAll().get(parent), moved, width, height);
				} else {
					HudPlace.free(selected, moved, width, height);
				}
				return true;
			}
		}
		return super.keyPressed(key);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent release) {
		try {
			return released(release);
		} finally {
			edited();
		}
	}

	private boolean released(MouseButtonEvent release) {
		mouseHeld = false;
		swallowDrag = false;
		if (lineDrag >= 0) {
			int from = lineDrag;
			lineDrag = -1;
			if (page.box != null) page.box.moveLine(from, lineGap);
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
		tip(toolbarTips, resetButton, armed ? "Click again to replace all boxes with the default ones. Ctrl+Z brings yours back."
				: "Replace all boxes with the default ones");
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
		menu.open(anchor, m -> {
			m.child(menuTitle("Add a ready-made box"));
			for (HudLayout.Preset preset : HudLayout.presets()) {
				FlowLayout entry = menuEntry("§f" + preset.name(), () -> addPreset(preset));
				m.tip(entry, preset.description());
				m.child(entry);
			}
		});
		rebuiltByClick = true;
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
		HudRenderer.Bounds bounds = HudRenderer.bounds(font, box, rows(box), width, height).grown(HudLayout.MARGIN);
		return drawn.values().stream().noneMatch(b -> bounds.overlapArea(b) > 0);
	}

	private String newBoxId() {
		for (int n = 1;; n++) {
			String id = "box" + n;
			if (hud.layout().boxes.stream().noneMatch(b -> id.equals(b.id))) return id;
		}
	}

	private void armDelete(boolean armed) {
		Page page = this.page;
		page.deleteArmed = armed;
		if (page.deleteRow == null) return;
		if (page.deleteButton != null) {
			dropFocusIn(page.deleteButton);
			page.tips.remove(page.deleteButton);
			page.deleteRow.removeChild(page.deleteButton);
		}
		Box box = page.box;
		page.deleteButton = iconTextButton(font, REMOVE, armed ? "§cReally delete?" : "§cDelete box", null, b -> {
			if (!page.deleteArmed) {
				armDelete(true);
				return;
			}
			page.deleteArmed = false;
			// boxes stuck to it stay where they are
			Map<Box, HudRenderer.Bounds> placed = placeAll();
			for (Box other : hud.layout().boxes) {
				if (HudPlace.parent(other, hud.layout().boxes) == box) HudPlace.free(other, placed.get(other), width, height);
			}
			hud.layout().boxes.remove(box);
			selected = null;
			rebuildPanel();
		});
		tip(page.deleteButton, armed ? "Click again to delete this box. Ctrl+Z brings it back."
				: "Delete this box. Ctrl+Z brings it back.");
		page.deleteRow.child(page.deleteButton);
	}

	// polled bc caxton cancels onValueChange. polled = last seen value, a change counts as an edit
	private static final class Field {
		private final Supplier<String> value;
		private final Consumer<String> apply;
		private String polled;

		Field(Supplier<String> value, Consumer<String> apply) {
			this.value = value;
			this.apply = apply;
		}
	}

	void field(Supplier<String> value, Consumer<String> apply) {
		page.fields.add(new Field(value, apply));
	}

	private void syncFields() {
		for (Field field : page.fields) {
			String value = field.value.get();
			if (!value.equals(field.polled)) {
				field.polled = value;
				edited();
			}
			field.apply.accept(value);
		}
	}

	// waits out drags, mouse down, title typing. now = record right away
	private void recordChanges(boolean now) {
		// nothing touched since the layout matched the history
		if (!dirty) return;
		String json = hud.layout().toJson();
		if (json.equals(history.current())) {
			unsettled = null;
			dirty = false;
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
		dirty = false;
		history.record(json);
		hud.save();
	}

	// owo mixins make EditBox + MultiLineEditBox greedy, javac cant see the 2nd
	private boolean typing() {
		return root.focusHandler().focused() instanceof GreedyInputUIComponent;
	}

	// owo keeps a removed component focused, enter/space would still press it
	void dropFocusIn(UIComponent removed) {
		for (UIComponent c = root.focusHandler().focused(); c != null; c = c.parent()) {
			if (c == removed) {
				root.focusHandler().focus(null, null);
				return;
			}
		}
	}

	void focus(UIComponent c) {
		root.focusHandler().focus(c, UIComponent.FocusSource.MOUSE_CLICK);
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
		edited();
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
		HudRenderer.Bounds placed = placeAll().get(box);
		return placed != null ? placed : HudRenderer.bounds(font, box, rows(box), width, height);
	}

	private void buildToastPage(FlowLayout panel) {
		panel.child(label("§fShaft toast"));
		LabelComponent what = label("§7Shows for a few seconds when you leave a shaft. Drag it to move it, scroll on it to resize it.");
		what.horizontalSizing(Sizing.fixed(inner()));
		panel.child(what);
		LabelComponent where = label("§7Style, time and Top right or Movable are in Settings, Journal.");
		where.horizontalSizing(Sizing.fixed(inner()));
		where.margins(Insets.top(4));
		panel.child(where);
	}

	private List<HudRow> rows(Box box) {
		List<HudRow> rows = HudBoxText.rows(box, hud.data(), true);
		return rows.isEmpty() ? List.of(HudRow.text("§8(empty)")) : rows;
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
		PanelScroll panelScroll = page.scroll;
		if (panelScroll != null && area.overlapArea(
				new HudRenderer.Bounds(panelScroll.x(), panelScroll.y(), panelScroll.width(), panelScroll.height())) > 0) {
			cost += 100_000_000;
		}
		FlowLayout listPanel = valueList() == null ? null : valueList().panel();
		if (listPanel != null && area.overlapArea(new HudRenderer.Bounds(listPanel.x(), listPanel.y(), listPanel.width(), listPanel.height())) > 0) {
			cost += 100_000_000;
		}
		return cost;
	}

	private boolean overPanel(double x, double y) {
		return toolbar.isInBoundingBox(x, y) || inPanel(x, y) || inList(x, y) || menu.contains(x, y);
	}

	// empty part under the content lets boxes show + get clicked
	private boolean inPanel(double x, double y) {
		return page.scroll != null && page.scroll.isInBoundingBox(x, y) && y < panelBottom();
	}

	private int panelBottom() {
		return Math.min(page.scroll.y() + page.scroll.height(), page.panel.y() + page.panel.height());
	}

	// sizes known after layout
	private void fitPanel() {
		PanelScroll panelScroll = page.scroll;
		if (panelScroll == null || panelScroll.width() == 0) return;
		// gui scale changed w/ the panel open
		if (page.builtWidth != panelWidth()) {
			rebuildPanel();
			return;
		}
		// no room beside it = leave the top for the toolbar
		int beside = valueList() == null ? 0 : listWidth() + 2;
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
			if (scrollToRow < page.lineRows.size()) showRow(page.lineRows.get(scrollToRow));
			scrollToRow = -1;
		}
	}

	private void showRow(UIComponent row) {
		PanelScroll panelScroll = page.scroll;
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

	private int panelWidth() {
		return Math.clamp(width * 30 / 100, PANEL_MIN, PANEL_MAX);
	}

	// panel minus its padding
	int inner() {
		return panelWidth() - 10;
	}

	FlowLayout root() {
		return root;
	}

	HudData data() {
		return hud.data();
	}

	int panelHeight() {
		return panelHeight;
	}

	String clip(String text, int max) {
		return ScreenParts.clip(font, text, max);
	}

	void swallowDrag() {
		swallowDrag = true;
	}

	boolean listOpen() {
		return listOpen;
	}

	void listOpen(boolean open) {
		listOpen = open;
	}

	String listSearch() {
		return listSearch;
	}

	void listSearch(String search) {
		listSearch = search;
	}

	boolean typingRules() {
		return typingRules;
	}

	void typingRules(boolean rules) {
		typingRules = rules;
	}

	AdvancedPage advancedPage() {
		return page.advanced;
	}

	private ValueList valueList() {
		return page.advanced == null ? null : page.advanced.list();
	}

	private void removePanel() {
		syncFields();
		if (page.scroll != null) {
			dropFocusIn(page.scroll);
			scrollMemory.put(page.key, page.scroll.offset());
			root.removeChild(page.scroll);
		}
		if (valueList() != null) valueList().remove();
		closeMenu();
		page = new Page();
	}

	void rebuildPanel() {
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
		page.box = box;
		page.key = box.id + "/" + (picking ? "picker" : editedIndex >= 0 ? "line" + editedIndex : "box");
		page.builtWidth = panelWidth();
		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(page.builtWidth), Sizing.content());
		panel.padding(Insets.of(5));
		panel.gap(1);
		page.panel = panel;
		PanelScroll panelScroll = new PanelScroll(panelHeight, panel);
		page.scroll = panelScroll;
		panelScroll.surface((graphics, c) -> {
			int h = panelBottom() - c.y();
			graphics.fill(c.x(), c.y(), c.x() + c.width(), c.y() + h, PANEL_FILL);
			graphics.drawRectOutline(c.x(), c.y(), c.width(), h, PANEL_OUTLINE);
		});
		panelScroll.scrollbar(ScrollContainer.Scrollbar.flat(Color.ofArgb(0x80FFFFFF)));
		panelScroll.scrollbarThiccness(2);
		panelScroll.offset(scrollMemory.getOrDefault(page.key, 0.0));
		// measured now, after undo the box wasnt drawn yet
		HudRenderer.Bounds bounds = boundsOf(box);
		boolean boxOnRight = bounds.x() + bounds.width() / 2 > width / 2;
		panelScroll.positioning(Positioning.relative(boxOnRight ? 0 : 100, 100));
		if (box == hud.layout().toast) buildToastPage(panel);
		else if (picking) addLinePage.build(panel, box);
		else if (editedIndex >= 0) buildLinePage(panel, box, editedIndex);
		else buildBoxPage(panel, box);
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

	void backToLines() {
		scrollToRow = editedIndex;
		editedIndex = -1;
		picking = false;
		rebuildPanel();
	}

	private void buildBoxPage(FlowLayout panel, Box box) {
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
		page.scaleSlider = scale;
		panel.child(row("Size", scale));

		Grid background = new Grid(Background.values().length, rowLabel);
		for (Background option : Background.values()) {
			background.add(choice(name(option), box.background == option, () -> box.background = option));
		}
		panel.child(row("Background", background));

		Grid align = new Grid(Align.values().length, rowLabel);
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

		page.deleteRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		page.deleteRow.margins(Insets.top(5));
		panel.child(page.deleteRow);
		armDelete(false);
	}

	// right of the title field
	private void titleColor(Box box, FlowLayout row) {
		boolean shaft = HudLayout.SHAFT_COLOR.equals(box.titleColor);
		String shown = HudBoxText.titleColor(box, hud.data(), true);
		ButtonComponent color = colorButton(shown, null, null, shaft,
				code -> box.titleColor = "e".equals(code) ? null : code);
		tip(color, "Title colour of this box: " + (shaft ? "the shaft's colour" : COLOR_NAMES[HudLayout.colorIndex(shown)]));
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
		tip(color, name + " colour of this box: " + COLOR_NAMES[HudLayout.colorIndex(current)]
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

	boolean addLine(Box box, Line line) {
		box.lines.add(line);
		picking = false;
		openLine(box.lines.size() - 1);
		return true;
	}

	private FlowLayout lineRow(Box box, int index) {
		Line line = box.lines.get(index);
		HoverRow row = new HoverRow();
		row.verticalSizing(Sizing.fixed(CHOICE));
		row.gap(4);
		FlowLayout grip = UIContainers.horizontalFlow(Sizing.fixed(7), Sizing.fixed(CHOICE - 2));
		grip.surface(ScreenParts.GRIP);
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
		HudValue.Period period = line.advancedMode() ? null : line.period();
		boolean ownColor = !line.advancedMode() && (line.labelColor != null || line.valueColor != null);
		// cramped at 204 px, tooltip only there
		String tag = period == null || panelWidth() < TAG_MIN_PANEL ? null : "§8" + period.name().toLowerCase(Locale.ROOT);
		// grip, checkbox, marker, gaps + slack so it never wraps
		int room = inner() - 40 - (tag == null ? 0 : font.width(tag) + 4 + (ownColor ? 12 : 0));
		LabelComponent look = label(clip(text, room));
		look.horizontalSizing(Sizing.expand());
		tip(look, "Click to change the label, period, format and colours"
				+ (period != null && tag == null ? "\n§7Period: " + period.name() : ""));
		row.child(look);
		if (tag != null) {
			LabelComponent periodTag = label(tag);
			tip(periodTag, period.name() + ": " + period.description() + ".");
			row.child(periodTag);
		}
		if (ownColor) {
			FlowLayout own = UIContainers.horizontalFlow(Sizing.fixed(8), Sizing.fixed(8));
			String label = line.labelColor;
			String value = line.valueColor;
			own.surface((graphics, c) -> {
				int x = c.x();
				int y = c.y();
				graphics.fill(x, y, x + 8, y + 8, 0xFF000000);
				// label left, value right, one colour fills it
				int left = HudLayout.COLOR_RGB[HudLayout.colorIndex(label != null ? label : value)];
				int right = HudLayout.COLOR_RGB[HudLayout.colorIndex(value != null ? value : label)];
				graphics.fill(x + 1, y + 1, x + 4, y + 7, 0xFF000000 | left);
				graphics.fill(x + 4, y + 1, x + 7, y + 7, 0xFF000000 | right);
			});
			List<String> parts = new ArrayList<>();
			if (label != null) parts.add("labels " + COLOR_NAMES[HudLayout.colorIndex(label)]);
			if (value != null) parts.add("values " + COLOR_NAMES[HudLayout.colorIndex(value)]);
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
		page.lineRows.add(row);
		page.lineHandles.add(grip);
		return row;
	}

	private void buildLinePage(FlowLayout panel, Box box, int index) {
		Line line = box.lines.get(index);
		HudValue value = line.hudValue();
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		header.gap(3);
		header.verticalAlignment(VerticalAlignment.CENTER);
		ButtonComponent back = button("‹ Back", b -> backToLines());
		tip(back, "Back to the box (Esc)");
		header.child(back);
		ButtonComponent remove = iconTextButton(font, REMOVE, "§cRemove", null, b -> {
			box.lines.remove(line);
			editedIndex = -1;
			scrollToRow = Math.min(index, box.lines.size() - 1);
			rebuildPanel();
		});
		tip(remove, "Remove this line. Ctrl+Z brings it back.");
		header.child(spacer());
		header.child(remove);
		String lineName = value.lineName(line.range(), line.target());
		panel.child(crumbs(box, lineName));
		panel.child(header);

		FlowLayout look = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		look.surface(PREVIEW);
		look.padding(Insets.of(3, 3, 5, 3));
		look.margins(Insets.vertical(3));
		page.preview = label("");
		page.previewLine = line;
		look.child(page.preview);
		if (!line.shown) {
			look.child(spacer());
			LabelComponent hidden = label("§7" + HIDDEN_NOTE);
			tip(hidden, "This line is hidden. Tick it in the box's line list to show it.");
			look.child(hidden);
		}
		tip(look, "§f" + lineName + "\n§7" + value.description());
		panel.child(look);
		if (value.multiRow()) {
			buildDrops(panel, line);
			return;
		}

		boolean advanced = line.advancedMode();
		Grid modes = new Grid(2, keyWidth);
		tip(modes.add(choice("Easy", !advanced, () -> {
			if (!line.advancedMode()) return;
			line.settle(box);
			line.advanced = false;
		})), "Pick a label, period, format and colour");
		tip(modes.add(choice("Advanced", advanced, () -> {
			if (line.advancedMode()) return;
			line.settle(box);
			line.advanced = true;
		})), "Type the whole line yourself: colours, {values}, maths and colour rules. Starts from this line plus its "
				+ "ready-made rules.");
		panel.child(keyRow("Mode", modes));
		if (advanced) {
			page.advanced = new AdvancedPage(this, box, line);
			page.advanced.build(panel);
		} else {
			buildEasy(panel, box, line);
		}
	}

	private void buildEasy(FlowLayout panel, Box box, Line line) {
		HudValue value = line.hudValue();
		// both colour buttons say the same, the label row is the tight one
		boxWord = boxWord();
		if (line.text != null || line.colorRules != null) {
			LabelComponent kept = label("§8Your Advanced text and rules are kept. Reset there starts again from this.");
			kept.maxWidth(inner());
			panel.child(kept);
		}
		panel.child(heading("Label"));
		FlowLayout labelRow = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		labelRow.gap(3);
		labelRow.verticalAlignment(VerticalAlignment.CENTER);
		String auto = value.defaultPrefix(line.range(), line.target());
		ButtonComponent reset = iconTextButton(font, pixels(RESET_ICON), "", "Automatic label", b -> {
			syncFields();
			line.prefix = auto;
			rebuildPanel();
		});
		tip(reset, "Use the automatic label: " + auto.strip());
		// field is polled, so the button comes + goes as u type
		TextBoxComponent prefix = textField(line.prefix, 64, typed -> {
			line.prefix = typed;
			boolean typedOwn = !value.isDefaultPrefix(typed);
			if (typedOwn && reset.parent() == null) labelRow.child(1, reset);
			else if (!typedOwn && reset.parent() != null) labelRow.removeChild(reset);
		});
		tip(prefix, "The text before the value. Spaces count.");
		labelRow.child(prefix);
		if (!value.isDefaultPrefix(line.prefix)) labelRow.child(reset);
		ButtonComponent labelColor = colorButton(line.labelColor, box.labelColor, code -> line.labelColor = code);
		tip(labelColor, line.labelColor == null ? sameAsBoxTip(box.labelColor) : "Label colour. \"Box\" follows the box's label colour.");
		labelRow.child(labelColor);
		panel.child(labelRow);
		if (line.range() == HudValue.Range.SHAFT) {
			LabelComponent note = label("§8\"This shaft\" in the label says \"Last shaft\" while you're outside a shaft.");
			note.maxWidth(inner());
			note.margins(Insets.top(1));
			panel.child(note);
		}

		panel.child(heading("Value"));
		List<String> targets = value.targets();
		if (!targets.isEmpty()) {
			HudValue.Target kind = value.target();
			int widest = targets.stream().mapToInt(t -> font.width(value.targetName(t))).max().orElse(0);
			// as many a row as the longest name fits, f.e. 2 at scale 4
			int columns = 1;
			for (int c = kind == HudValue.Target.SHAFT_TYPE ? 3 : 2; c > 1 && columns == 1; c--) {
				if (widest + 8 <= (inner() - keyWidth - 2 - 2 * (c - 1)) / c) columns = c;
			}
			Grid options = new Grid(columns, keyWidth);
			for (String t : targets) {
				String name = value.targetName(t);
				tip(options.add(choice(name, line.target().equals(t), () -> line.setTarget(t))), switch (kind) {
					case SHAFT_TYPE -> "Shafts since your last " + name + " shaft";
					case CORPSE_TYPE -> name + " corpses";
					default -> "Your " + name + " personal best";
				});
			}
			panel.child(keyRow(switch (kind) {
				case SHAFT_TYPE -> "Shaft";
				case CORPSE_TYPE -> "Corpse";
				default -> "PB";
			}, options));
			if (kind == HudValue.Target.PERSONAL_BEST && !value.hasTarget(line.target())) {
				LabelComponent gone = label("§8This personal best isn't on your Stats tab any more, so it shows -.");
				gone.maxWidth(inner());
				panel.child(gone);
			}
		}
		if (!value.periods().isEmpty()) panel.child(periodRow(line));
		List<String> formats = HudFormat.options(value.kind());
		if (formats.size() > 1 && value.hasFormatChoice()) {
			Grid options = new Grid(formats.size(), keyWidth);
			for (int i = 0; i < formats.size(); i++) {
				int format = i;
				tip(options.add(choice(formats.get(i), line.format == i, () -> line.format = format)),
						"Shows as " + HudFormat.example(value.kind(), i, HudBoxText.suffix(value, line.range(), line.prefix)));
			}
			panel.child(keyRow("Format", options));
		}
		ButtonComponent valueColor = colorButton(line.valueColor, box.valueColor, value.defaultColor(),
				code -> line.valueColor = code);
		tip(valueColor, line.valueColor == null ? sameAsBoxTip(box.valueColor) : "Value colour. \"Box\" follows the box's value colour.");
		panel.child(keyRow("Colour", valueColor));
		if (value.graph(line.range()) != HudValue.Graph.NONE) panel.child(showRow(line));
		if (value.followsShaft(line.range())) panel.child(outsideRow(line, false));
	}

	// no mode, label, format or colour
	private void buildDrops(FlowLayout panel, Line line) {
		panel.child(periodRow(line));
		if (line.hudValue().followsShaft(line.range())) panel.child(outsideRow(line, false));
		HudLayout.DropsView view = line.dropsView();
		Grid show = new Grid(3, keyWidth);
		tip(show.add(choice("Items", view == HudLayout.DropsView.ITEMS, () -> line.dropsView(HudLayout.DropsView.ITEMS))), "One row per item");
		tip(show.add(choice("Sources", view == HudLayout.DropsView.SOURCES, () -> line.dropsView(HudLayout.DropsView.SOURCES))),
				"One row per source: mined gems, corpse loot, outside shafts and so on");
		tip(show.add(choice("Both", view == HudLayout.DropsView.BOTH, () -> line.dropsView(HudLayout.DropsView.BOTH))),
				"Sources with their biggest items under them");
		panel.child(keyRow("Show", show));
		Grid rows = new Grid(4, keyWidth);
		String rowsTip = "How many items before the rest folds into one row";
		for (int n : new int[] {3, 5, 8, 0}) {
			tip(rows.add(choice(n == 0 ? "All" : String.valueOf(n), line.rowsOrDefault() == n, () -> line.rows = n)), rowsTip);
		}
		panel.child(keyRow("Rows", rows));
		// "Amounts" wraps in the key column
		panel.child(showHide("Amount", "The amount after each name", line.hideAmounts, hide -> line.hideAmounts = hide));
		panel.child(showHide("Spent", "Keys and campfires, with their cost in red", line.hideSpent, hide -> line.hideSpent = hide));
	}

	// null = shown, never writes false
	private FlowLayout showHide(String name, String tip, Boolean hidden, Consumer<Boolean> set) {
		boolean hide = Boolean.TRUE.equals(hidden);
		Grid options = new Grid(2, keyWidth);
		tip(options.add(choice("Show", !hide, () -> set.accept(null))), tip);
		tip(options.add(choice("Hide", hide, () -> set.accept(true))), tip);
		return keyRow(name, options);
	}

	// "Show as" doesnt fit the key column
	FlowLayout showRow(Line line) {
		ShowAs now = line.showAs();
		HudValue.Graph kind = line.hudValue().graph(line.range());
		String graph = switch (kind) {
			case CHART -> "your last 12 shafts' spawn times, dashes = this average";
			case LINE -> "a line of the last minute, every 10 s, dashes = your usual for this shaft type";
			default -> "a bar";
		};
		Grid options = new Grid(3, keyWidth);
		tip(options.add(choice("Text", now == ShowAs.TEXT, () -> line.showAs(ShowAs.TEXT))), "The number only");
		tip(options.add(choice("Both", now == ShowAs.BOTH, () -> line.showAs(ShowAs.BOTH))), "The number with " + graph + " under it");
		String only = switch (kind) {
			case CHART -> "Chart";
			case LINE -> "Line";
			default -> "Bar";
		};
		String onlyTip = switch (kind) {
			case CHART -> "Your last 12 shafts' spawn times without the number";
			case LINE -> "The line without the number";
			default -> "A bar without the number";
		};
		tip(options.add(choice(only, now == ShowAs.GRAPH, () -> line.showAs(ShowAs.GRAPH))), onlyTip);
		return keyRow("Show", options);
	}

	private FlowLayout periodRow(Line line) {
		Grid options = new Grid(2, keyWidth);
		HudValue.Period now = line.period();
		for (HudValue.Period period : line.hudValue().periods()) {
			tip(options.add(choice(period.name(), period.equals(now), () -> line.setPeriod(period))), period.description() + ".");
		}
		return keyRow("Period", options);
	}

	FlowLayout outsideRow(Line line, boolean advanced) {
		String which = advanced ? "shaft values in this line show " : "show ";
		Grid outside = new Grid(2, keyWidth);
		tip(outside.add(choice("Last shaft", !line.dash(), () -> line.dashOutside = null)),
				"Outside a shaft, " + which + "the last shaft's number");
		tip(outside.add(choice("-", line.dash(), () -> line.dashOutside = true)), "Outside a shaft, " + which + "-");
		return keyRow("Outside", outside);
	}

	int listWidth() {
		return Math.clamp(width * 20 / 100, LIST_MIN, LIST_MAX);
	}

	// click keeps the help open beside the panel while typing
	private void openGuide(boolean open) {
		help.open(open);
		guideButton.setMessage(Component.literal(open ? "§e?" : "?"));
	}

	// live, the text fields are polled
	private void refreshLinePage() {
		if (page.preview != null) {
			Line previewLine = page.previewLine;
			String text = HudBoxText.line(page.box, previewLine, hud.data(), true);
			if (!previewLine.shown) text = "§8" + strip(text);
			if (!text.equals(page.previewRaw)) {
				page.previewRaw = text;
				// padding, + note and gap if hidden
				int room = inner() - 8 - (previewLine.shown ? 0 : font.width(HIDDEN_NOTE) + 4);
				page.preview.text(Component.literal(clip(text, room)));
			}
		}
		if (page.advanced != null) page.advanced.refresh(root.focusHandler().focused());
	}

	// beside the panel, same height. sizes known after layout
	private void fitList() {
		if (valueList() == null || page.scroll == null || page.scroll.width() == 0) return;
		valueList().fit(page.scroll);
	}

	private boolean inList(double x, double y) {
		return valueList() != null && valueList().contains(x, y);
	}

	void closeMenu() {
		menu.close();
	}

	TextBoxComponent textField(String text, int maxLength, Consumer<String> apply) {
		TextBoxComponent field = UIComponents.textBox(Sizing.expand());
		field.verticalSizing(Sizing.fixed(BUTTON));
		// setValue cuts to the max, a longer hand edit in hud.json would get saved cut
		field.setMaxLength(Math.max(maxLength, text.length()));
		field.text(text);
		String[] last = {text};
		field(field::getValue, typed -> {
			if (typed.equals(last[0])) return;
			last[0] = typed;
			apply.accept(typed);
		});
		// caxton: text set at 0 width can scroll 1st letter out, rehome next frame
		page.homeFields.add(field);
		return field;
	}

	private int lineHandleAt(double x, double y) {
		// rows scrolled out of view keep their bounds, under the toolbar on narrow screens
		if (!inPanel(x, y)) return -1;
		List<UIComponent> lineHandles = page.lineHandles;
		for (int i = 0; i < lineHandles.size(); i++) {
			if (lineHandles.get(i).isInBoundingBox(x, y)) return i;
		}
		return -1;
	}

	// boxColor null = no "Box" choice. shows the colour it ends up as
	private ButtonComponent colorButton(String current, String boxColor, Consumer<String> setColor) {
		return colorButton(current, boxColor, null, setColor);
	}

	private ButtonComponent colorButton(String current, String boxColor, String valueDefault, Consumer<String> setColor) {
		return colorButton(current, boxColor, valueDefault, null, setColor);
	}

	// valueDefault = the values own colour, offered back in the menu. shaft null = no Shaft colour choice, else picked or not
	private ButtonComponent colorButton(String current, String boxColor, String valueDefault, Boolean shaft,
			Consumer<String> setColor) {
		String text = current == null ? boxText(boxWord) : "§7▾";
		int shown = HudLayout.colorIndex(current != null ? current : boxColor);
		Consumer<String> pick = code -> {
			closeMenu();
			setColor.accept(code);
			rebuildPanel();
		};
		ButtonComponent button = drawnButton("Colour: " + (Boolean.TRUE.equals(shaft) ? "Shaft colour" : COLOR_NAMES[shown]), b -> {
			menu.colors(b, Boolean.TRUE.equals(shaft) ? HudLayout.SHAFT_COLOR : current, boxColor, valueDefault, shaft != null, pick);
			rebuiltByClick = true;
		});
		button.verticalSizing(Sizing.fixed(CHOICE));
		button.horizontalSizing(Sizing.fixed(colorButtonWidth(text)));
		button.renderer((graphics, b, delta) -> {
			PackButton.RENDERER.draw(graphics, b, delta);
			int x = b.getX() + 4;
			int y = b.getY() + (b.getHeight() - 8) / 2;
			graphics.fill(x - 1, y - 1, x + 9, y + 9, 0xFF000000);
			graphics.fill(x, y, x + 8, y + 8, HudLayout.argb(current != null ? current : boxColor));
			graphics.text(font, text, x + 11, y, 0xFFFFFFFF, true);
		});
		return button;
	}

	private static String boxText(String word) {
		return "§f" + word + " §7▾";
	}

	// swatch + gaps + padding, as colorButton sizes it
	private int colorButtonWidth(String text) {
		return 4 + 8 + 3 + font.width(text) + 4;
	}

	// "Same as box" only if the label field keeps the room "Box" leaves it in the smallest panel
	private String boxWord() {
		return EditorFit.sameAsBoxFits(inner(), PANEL_MIN - 10, colorButtonWidth(boxText("Same as box")),
				colorButtonWidth(boxText("Box"))) ? "Same as box" : "Box";
	}

	private static String sameAsBoxTip(String boxColor) {
		return "Colour: same as the box (" + COLOR_NAMES[HudLayout.colorIndex(boxColor)] + ")";
	}

	private FlowLayout keyRow(String name, UIComponent child) {
		FlowLayout row = row(name, child);
		row.children().getFirst().horizontalSizing(Sizing.fixed(keyWidth));
		row.margins(Insets.vertical(1));
		return row;
	}

	// HUD › box › page. here null = the box page
	FlowLayout crumbs(Box box, String here) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.margins(Insets.bottom(2));
		String gap = "§8 › ";
		String name = strip(HudBoxText.tags(box.title, hud.data(), true)).strip();
		if (name.isEmpty()) name = "Box";
		// crumb padding 2px each
		int room = inner() - font.width("HUD") - font.width(gap) - 4;
		FlowLayout home = crumb("HUD", () -> {
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
		FlowLayout boxCrumb = crumb(clip(name, room - font.width(last)), this::backToLines);
		tip(boxCrumb, "Back to the box (Esc)");
		row.child(boxCrumb);
		row.child(label(gap));
		row.child(label(last));
		return row;
	}

	private FlowLayout row(String name, UIComponent... children) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(2);
		row.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent label = label("§7" + name);
		label.horizontalSizing(Sizing.fixed(rowLabel));
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

	private FlowLayout place(String name, boolean on, Consumer<Boolean> set, String tooltip) {
		FlowLayout check = check(name, on, set);
		tip(check, tooltip);
		return check;
	}
}
