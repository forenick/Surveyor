package dev.skysurveyor.hud;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import dev.skysurveyor.hud.HudValue.Range;

// client thread only, except toJson() copies
public final class HudLayout {
	public static final int VERSION = 3;
	// nicks pick (2026-11-11)
	public static final int MARGIN = 4;
	public static final float MIN_SCALE = 0.5f;
	public static final float MAX_SCALE = 2;
	public static final String COLOR_CODES = "0123456789abcdef";
	// rgb of each code above, same order
	public static final int[] COLOR_RGB = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
			0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
	public static final String TOAST_ID = "toast";

	// bad or no code = white
	public static int colorIndex(String code) {
		int i = code == null || code.isEmpty() ? -1 : COLOR_CODES.indexOf(Character.toLowerCase(code.charAt(0)));
		return i < 0 ? COLOR_CODES.indexOf('f') : i;
	}

	public static int argb(String code) {
		return 0xFF000000 | COLOR_RGB[colorIndex(code)];
	}
	// box title in the shafts (or last ones) colour, yellow w/o one
	public static final String SHAFT_COLOR = "shaft";
	// drops line, mockup hud-drops-v1
	public static final int DEFAULT_ROWS = 5;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	// display choice, not a game number. fixtures have 6 boxes, 6 lines, 66 chars, the editor allows 64 to 512 chars
	static final int MAX_BOXES = 100;
	static final int MAX_LINES = 100;
	static final int MAX_RULES = 100;
	static final int MAX_TEXT = 1000;

	enum Visibility {
		ALWAYS, MINES, IN_SHAFT, OUTSIDE_SHAFT
	}

	public enum Anchor {
		START, MIDDLE, END;

		// edge: 0 start, 0.5 middle, 1 end
		public static Anchor ofEdge(double edge) {
			return edge == 0 ? START : edge == 1 ? END : MIDDLE;
		}

		public static Anchor forPosition(int start, int size, int screen) {
			double middle = start + size / 2.0;
			return middle < screen / 3.0 ? START : middle > screen * 2 / 3.0 ? END : MIDDLE;
		}

		public int start(int offset, int size, int screen) {
			int start = switch (this) {
				case START -> MARGIN + offset;
				case MIDDLE -> centred(size, screen) + offset;
				case END -> screen - MARGIN - size - offset;
			};
			return clamp(start, size, screen);
		}

		public int offset(int start, int size, int screen) {
			return switch (this) {
				case START -> start - MARGIN;
				case MIDDLE -> start - centred(size, screen);
				case END -> screen - MARGIN - size - start;
			};
		}

		// too big for the screen = starts at the margin
		public static int clamp(int start, int size, int screen) {
			return Math.max(MARGIN, Math.min(start, screen - MARGIN - size));
		}

		private static int centred(int size, int screen) {
			return (int) Math.round((screen - size) / 2.0);
		}
	}

	public enum Background {
		NONE, DARK, FRAME
	}

	public enum Align {
		LEFT, CENTER, RIGHT
	}

	public static final class Line {
		public String value;
		public String prefix;
		// null = box colour
		public String labelColor;
		// null = box colour
		public String valueColor;
		public boolean shown = true;
		public int format;
		public Range range;
		// mode, Easy = false. text + colorRules kept in Easy
		public boolean advanced;
		public String text;
		// pre 0.23, read as typed rules till the line is edited
		public boolean readyRule;
		public List<ColorRule> rules;
		// null = false, shaft values show the last shaft outside one
		public Boolean dashOutside;
		// typed rules, 1 a line. null = never set (old rules or the preset)
		public List<String> colorRules;
		// shaft or corpse type tag, f.e. "jasper". null = the values default
		public String target;
		// null = text, "both" or "graph". unknown kept + drawn as text
		public String showAs;
		// drops line. null = DEFAULT_ROWS, 0 = all
		public Integer rows;
		// drops line, null = shown
		public Boolean hideAmounts;
		// row bars gone since 0.25.0-alpha.12, kept so old files load unchanged
		public Boolean hideBars;
		public Boolean hideSpent;
		// drops line. null = items, "sources" or "both". unknown kept + drawn as items
		public String view;

		public Line() {
		}

		public Line(HudValue value) {
			this(value, null, true, 0);
		}

		Line(HudValue value, String prefix, boolean shown, int format) {
			this.value = value.id();
			this.range = value.defaultRange();
			this.prefix = prefix == null ? value.defaultPrefix(range) : prefix;
			this.shown = shown;
			this.format = format;
			// not in Line(), gson uses that one
			this.valueColor = value.defaultColor();
			if (value.pinsTarget()) this.target = value.defaultTarget();
		}

		public static Line of(HudValue.Period period) {
			Line line = new Line(period.value());
			if (period.range() != null) line.setRange(period.range());
			return line;
		}

		// null = the value has no periods
		public HudValue.Period period() {
			HudValue value = hudValue();
			return value == null ? null : value.period(range());
		}

		// period row. across the shafts family it changes the value, saved ids stay
		public void setPeriod(HudValue.Period period) {
			HudValue old = hudValue();
			if (old == null) return;
			HudValue now = period.value();
			if (now == old) {
				if (period.range() != null) setRange(period.range());
				return;
			}
			if (old.isDefaultPrefix(prefix)) prefix = now.defaultPrefix(period.range(), target);
			// formats are per kind, an index from another is wrong
			if (now.kind() != old.kind()) format = 0;
			if (Objects.equals(valueColor, old.defaultColor(target()))) valueColor = now.defaultColor(target);
			value = now.id();
			range = period.range();
		}

		// null in old files = the default range, kept null so the file stays the same
		public Range range() {
			HudValue value = hudValue();
			if (value == null) return range;
			return value.hasRange(range) ? range : value.defaultRange();
		}

		public String target() {
			HudValue value = hudValue();
			return value == null ? target : value.targetOrDefault(target);
		}

		// label + colour nobody picked follow the target
		public void setTarget(String target) {
			HudValue value = hudValue();
			if (value == null) return;
			if (value.isDefaultPrefix(prefix)) prefix = value.defaultPrefix(range(), target);
			if (Objects.equals(valueColor, value.defaultColor(target()))) valueColor = value.defaultColor(target);
			this.target = !value.pinsTarget() && target.equals(value.defaultTarget()) ? null : target;
		}

		// drops line, 0 = all
		public int rowsOrDefault() {
			return rows == null ? DEFAULT_ROWS : Math.max(0, rows);
		}

		public DropsView dropsView() {
			for (DropsView v : DropsView.values()) {
				if (v.saved != null && v.saved.equals(view)) return v;
			}
			return DropsView.ITEMS;
		}

		public void dropsView(DropsView v) {
			view = v.saved;
		}

		public boolean dash() {
			return Boolean.TRUE.equals(dashOutside);
		}

		// a label nobody typed follows the range
		public void setRange(Range range) {
			HudValue value = hudValue();
			if (value != null && value.isDefaultPrefix(prefix)) {
				prefix = value.defaultPrefix(range, target());
			}
			this.range = range;
		}

		public HudValue hudValue() {
			return HudValue.byId(value);
		}

		// a value w/o a graph (or not in this period) shows text, showAs kept
		public ShowAs showAs() {
			HudValue value = hudValue();
			if (value == null || value.graph(range()) == HudValue.Graph.NONE) return ShowAs.TEXT;
			for (ShowAs as : ShowAs.values()) {
				if (as.saved != null && as.saved.equals(showAs)) return as;
			}
			return ShowAs.TEXT;
		}

		public void showAs(ShowAs as) {
			showAs = as.saved;
		}

		public boolean advancedMode() {
			return advanced || oldRules();
		}

		// rules from before 0.23 on an Easy line: shows as Advanced, looks the same
		private boolean oldRules() {
			return !advanced && colorRules == null && (rules != null || readyRule);
		}

		public String advancedText(Box box) {
			return oldRules() || text == null ? HudBoxText.basicText(box, this) : text;
		}

		public List<String> advancedRules() {
			if (colorRules != null) return colorRules;
			if (rules == null && !readyRule) return List.of();
			return HudRules.fromOld(hudValue(), HudBoxText.ruleRange(this), rules, readyRule);
		}

		// 1st edit on the line page writes the typed form, just opening it changes nothing
		public void settle(Box box) {
			boolean old = oldRules();
			String start = advancedText(box);
			// preset only when turning Advanced on, old advanced lines had none
			List<String> typed = colorRules != null || rules != null || readyRule ? advancedRules()
					: advancedMode() ? List.of() : HudRules.preset(hudValue());
			text = start;
			colorRules = new ArrayList<>(typed);
			rules = null;
			readyRule = false;
			if (old) advanced = true;
		}

		// Reset: the Easy look + the preset rules
		public void resetAdvanced(Box box) {
			text = HudBoxText.basicText(box, this);
			colorRules = new ArrayList<>(HudRules.preset(hudValue()));
			rules = null;
			readyRule = false;
		}
	}

	public enum ShowAs {
		TEXT(null),
		BOTH("both"),
		GRAPH("graph");

		// saved in hud.json, never rename
		private final String saved;

		ShowAs(String saved) {
			this.saved = saved;
		}
	}

	public enum DropsView {
		ITEMS(null),
		SOURCES("sources"),
		BOTH("both");

		// saved in hud.json, never rename
		private final String saved;

		DropsView(String saved) {
			this.saved = saved;
		}
	}

	public static final class ColorRule {
		public enum When {
			OVER, UNDER
		}

		public When when = When.OVER;
		// seconds for times, not ms
		public double value;
		// a value tag like {average_spawn}, beats value. null = value
		public String than;
		public String color = "c";
	}

	public static final class Box {
		public String id;
		public String title = "";
		public boolean showTitle;
		public boolean showInShaft = true;
		public boolean showInMines = true;
		public boolean showElsewhere;
		public Background background = Background.DARK;
		public float scale = 1;
		public Align align = Align.LEFT;
		public String labelColor = "7";
		public String valueColor = "f";
		public boolean labelBold;
		public boolean valueBold;
		public Anchor anchorX = Anchor.START;
		public Anchor anchorY = Anchor.START;
		// inwards for START/END, right/down for MIDDLE
		public int offsetX;
		public int offsetY;
		public List<Line> lines = new ArrayList<>();
		// v1/v2 only, read to convert, never written
		Float x;
		Float y;
		Visibility visibility;
		// null = yellow, nulls stay out of the file. or SHAFT_COLOR
		public String titleColor;
		// null = bold
		public Boolean titleBold;
		// "(/sv hud)" under the box till the editor opens, new players only. null = none
		public Boolean hint;

		public String titleColorOrDefault() {
			return titleColor != null ? titleColor : "e";
		}

		public boolean hint() {
			return hint != null && hint;
		}

		public boolean titleBoldOrDefault() {
			return titleBold == null || titleBold;
		}

		// shaft wins over mines
		public boolean visible(boolean inMines, boolean inShaft) {
			return inShaft ? showInShaft : inMines ? showInMines : showElsewhere;
		}

		// gap 0 = above first, lines.size() = below last
		public void moveLine(int from, int gap) {
			if (from < 0 || from >= lines.size() || gap < 0 || gap > lines.size()) return;
			Line line = lines.remove(from);
			lines.add(gap > from ? gap - 1 : gap, line);
		}
	}

	public int version = VERSION;
	public List<Box> boxes = new ArrayList<>();
	// movable shaft toast spot, null = never moved (defaultToast). only position + scale count
	public Box toast;
	// parts the caps cut on load, never saved
	transient int cutParts;

	// left column, right side is hypixels scoreboard (nick, 2026-11-18)
	public static HudLayout defaults() {
		HudLayout layout = new HudLayout();
		layout.boxes.add(sessionBox());
		layout.boxes.add(spawnBox());
		layout.boxes.add(shaftBox());
		layout.boxes.add(moneyBox());
		return layout;
	}

	// a new players HUD, so they can find where the boxes come from
	public static HudLayout firstStart() {
		HudLayout layout = defaults();
		for (Box box : layout.boxes) box.hint = true;
		return layout;
	}

	// editor opened, hints gone for good. false = there were none
	public boolean dropHints() {
		boolean had = false;
		for (Box box : boxes) {
			had |= box.hint();
			box.hint = null;
		}
		return had;
	}

	public record Preset(String name, String description, Supplier<Box> box) {
	}

	// display choice: top middle, under the boss bar
	public static Box defaultToast() {
		Box box = new Box();
		box.id = TOAST_ID;
		box.anchorX = Anchor.MIDDLE;
		box.anchorY = Anchor.START;
		box.offsetY = 26;
		return box;
	}

	public Box toastOrDefault() {
		return toast != null ? toast : defaultToast();
	}

	// nicks picks (2026-11-12), lines matter more than looks
	public static List<Preset> presets() {
		return List.of(
				new Preset("Spawn", "Spawn timer and average spawn", HudLayout::spawnBox),
				new Preset("Session", "Shafts, shafts per hour and powder", HudLayout::sessionBox),
				new Preset("Current shaft", "Time inside, corpses, campfires and coins", HudLayout::shaftBox),
				new Preset("All-time", "Shafts, powder and corpse Fine, all-time", HudLayout::allTimeBox),
				new Preset("Session profit", "Coins this session, per hour, per shaft, session length and drops", HudLayout::moneyBox),
				new Preset("Performance", "FPS, ping and TPS", HudLayout::performanceBox));
	}

	private static Box sessionBox() {
		Box session = box("session", "Session", true, true, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.START, 0);
		session.lines.add(new Line(HudValue.SESSION_SHAFTS, null, true, 0));
		session.lines.add(new Line(HudValue.SHAFTS_PER_HOUR, null, true, 0));
		session.lines.add(new Line(HudValue.AVERAGE_SPAWN, null, false, 1));
		session.lines.add(new Line(HudValue.SESSION_POWDER, null, true, 0));
		return session;
	}

	private static Box spawnBox() {
		// same spot as the shaft box, never both at once. offsets leave room for the first start hint
		Box spawn = box("spawn", "Spawn", false, true, Background.NONE, Align.LEFT, Anchor.START, 0, Anchor.START, 54);
		spawn.showInShaft = false;
		spawn.valueBold = true;
		spawn.lines.add(new Line(HudValue.SPAWN_TIMER, null, true, 0));
		spawn.lines.add(new Line(HudValue.AVERAGE_SPAWN, "Avg: ", true, 0));
		return spawn;
	}

	private static Box shaftBox() {
		Box shaft = box("shaft", "{shaft_type} Shaft", true, false, Background.FRAME, Align.LEFT, Anchor.START, 0, Anchor.START, 54);
		shaft.titleColor = SHAFT_COLOR;
		shaft.lines.add(new Line(HudValue.SHAFT_TIME, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CORPSES, null, true, 0));
		shaft.lines.add(new Line(HudValue.SHAFT_CAMPFIRES, null, true, 0));
		// title says the shaft already. no cold, the scoreboard has it (nick, 2026-11-15)
		shaft.lines.add(rangeLine(HudValue.COINS, "Coins: ", Range.SHAFT));
		return shaft;
	}

	private static Box allTimeBox() {
		Box box = box("all_time", "All-time", true, true, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.MIDDLE, 0);
		for (HudValue value : List.of(HudValue.SESSION_SHAFTS, HudValue.SESSION_POWDER, HudValue.SESSION_CORPSE_FINE)) {
			// box title says all-time already
			box.lines.add(rangeLine(value, value.defaultPrefix(), Range.ALL_TIME));
		}
		return box;
	}

	private static Box moneyBox() {
		Box box = box("money", "Session profit", true, true, Background.DARK, Align.LEFT, Anchor.START, 0, Anchor.START, 120);
		// this shaft lives in the shaft box (nick, 2026-11-15)
		box.lines.add(rangeLine(HudValue.COINS, "Total: ", Range.SESSION));
		box.lines.add(rangeLine(HudValue.COINS, "Per hour: ", Range.PER_HOUR));
		box.lines.add(rangeLine(HudValue.COINS_PER_SHAFT, "Per shaft: ", Range.SESSION));
		box.lines.add(new Line(HudValue.SESSION_TIME, "Time: ", true, 0));
		// nick, 2026-11-16
		box.lines.add(rangeLine(HudValue.DROPS, "", Range.SESSION));
		return box;
	}

	private static Line rangeLine(HudValue value, String prefix, Range range) {
		Line line = new Line(value, prefix, true, 0);
		line.range = range;
		return line;
	}

	private static Box performanceBox() {
		Box box = box("performance", "Performance", false, true, Background.DARK, Align.LEFT, Anchor.END, 0, Anchor.END, 0);
		box.showElsewhere = true;
		box.lines.add(new Line(HudValue.FPS));
		box.lines.add(new Line(HudValue.PING));
		box.lines.add(new Line(HudValue.TPS));
		return box;
	}

	private static Box box(String id, String title, boolean showTitle, boolean inMines, Background background,
			Align align, Anchor anchorX, int offsetX, Anchor anchorY, int offsetY) {
		Box box = new Box();
		box.id = id;
		box.title = title;
		box.showTitle = showTitle;
		box.showInMines = inMines;
		box.background = background;
		box.align = align;
		box.anchorX = anchorX;
		box.offsetX = offsetX;
		box.anchorY = anchorY;
		box.offsetY = offsetY;
		return box;
	}

	public String toJson() {
		return GSON.toJson(this);
	}

	public static HudLayout fromJson(String json) {
		HudLayout layout = GSON.fromJson(json, HudLayout.class);
		if (layout == null || layout.boxes == null) throw new JsonParseException("No boxes in the HUD file");
		layout.boxes.removeIf(box -> box == null);
		layout.cutParts += cut(layout.boxes, MAX_BOXES);
		Set<String> ids = new HashSet<>();
		ids.add(TOAST_ID);
		if (layout.toast != null) {
			layout.cutParts += repair(layout.toast);
			layout.toast.id = TOAST_ID;
		}
		for (Box box : layout.boxes) {
			layout.cutParts += repair(box);
			// editor finds boxes by id after undo
			if (box.id == null || box.id.isBlank() || !ids.add(box.id)) {
				int n = 1;
				while (!ids.add("box" + n)) n++;
				box.id = "box" + n;
			}
		}
		layout.version = VERSION;
		return layout;
	}

	// = parts the caps cut
	private static int repair(Box box) {
		int cut = 0;
		if (box.title == null) box.title = "";
		if (box.title.length() > MAX_TEXT) {
			box.title = cut(box.title);
			cut++;
		}
		if (box.visibility != null) convertVersion2(box);
		if (box.background == null) box.background = Background.DARK;
		if (box.align == null) box.align = Align.LEFT;
		if (!isColor(box.labelColor)) box.labelColor = "7";
		if (!isColor(box.valueColor)) box.valueColor = "f";
		if (box.titleColor != null && !isColor(box.titleColor) && !SHAFT_COLOR.equals(box.titleColor)) box.titleColor = null;
		box.scale = clamp(box.scale == 0 ? 1 : box.scale, MIN_SCALE, MAX_SCALE);
		if (box.anchorX == null) box.anchorX = Anchor.START;
		if (box.anchorY == null) box.anchorY = Anchor.START;
		if (box.x != null || box.y != null) convertVersion1(box);
		if (box.lines == null) box.lines = new ArrayList<>();
		box.lines.removeIf(line -> line == null || line.hudValue() == null);
		cut += cut(box.lines, MAX_LINES);
		for (Line line : box.lines) {
			HudValue value = line.hudValue();
			if (line.prefix != null && line.prefix.length() > MAX_TEXT) {
				line.prefix = cut(line.prefix);
				cut++;
			}
			if (line.text != null && line.text.length() > MAX_TEXT) {
				line.text = cut(line.text);
				cut++;
			}
			if (!isColor(line.labelColor)) line.labelColor = null;
			if (!isColor(line.valueColor)) line.valueColor = null;
			int formats = HudFormat.options(value.kind()).size();
			if (line.format < 0 || line.format >= formats) line.format = 0;
			// pre-range files have none, were all session values
			if (line.range != null && !value.hasRange(line.range)) line.range = value.defaultRange();
			// pbs come from settings, maybe not loaded yet
			if (line.target != null && !value.acceptsTarget(line.target)) line.target = null;
			if (line.prefix == null) line.prefix = value.defaultPrefix(line.range(), line.target());
			if (HudRules.preset(value).isEmpty()) line.readyRule = false;
			if (line.rules != null) {
				line.rules.removeIf(rule -> rule == null);
				cut += cut(line.rules, MAX_RULES);
				for (ColorRule rule : line.rules) {
					if (rule.when == null) rule.when = ColorRule.When.OVER;
					if (!isColor(rule.color)) rule.color = "c";
					if (!Double.isFinite(rule.value)) rule.value = 0;
					if (rule.than != null && !HudBoxText.isLimitTag(rule.than, value)) rule.than = null;
				}
				if (line.rules.isEmpty() || !value.hasNumber()) line.rules = null;
			}
			if (line.advanced && line.text == null) line.advanced = false;
			if (line.colorRules != null) {
				line.colorRules.removeIf(rule -> rule == null);
				cut += cut(line.colorRules, MAX_RULES);
				for (int i = 0; i < line.colorRules.size(); i++) {
					if (line.colorRules.get(i).length() > MAX_TEXT) {
						line.colorRules.set(i, cut(line.colorRules.get(i)));
						cut++;
					}
				}
			}
		}
		return cut;
	}

	private static int cut(List<?> list, int max) {
		if (list.size() <= max) return 0;
		int over = list.size() - max;
		list.subList(max, list.size()).clear();
		return over;
	}

	// never half a surrogate pair
	private static String cut(String text) {
		int end = Character.isHighSurrogate(text.charAt(MAX_TEXT - 1)) ? MAX_TEXT - 1 : MAX_TEXT;
		return text.substring(0, end);
	}

	public int cutParts() {
		return cutParts;
	}

	private static void convertVersion2(Box box) {
		Visibility old = box.visibility;
		box.showInShaft = old != Visibility.OUTSIDE_SHAFT;
		box.showInMines = old != Visibility.IN_SHAFT;
		box.showElsewhere = old == Visibility.ALWAYS;
		box.visibility = null;
	}

	// 480×270 = 1080p auto gui scale. non-edge boxes can land a few px off
	private static void convertVersion1(Box box) {
		float fx = clamp(box.x == null ? 0 : box.x, 0, 1);
		float fy = clamp(box.y == null ? 0 : box.y, 0, 1);
		box.anchorX = anchorOf(fx);
		box.offsetX = offsetOf(box.anchorX, fx, 480);
		box.anchorY = anchorOf(fy);
		box.offsetY = offsetOf(box.anchorY, fy, 270);
		box.x = null;
		box.y = null;
	}

	private static Anchor anchorOf(float fraction) {
		return fraction < 1 / 3f ? Anchor.START : fraction > 2 / 3f ? Anchor.END : Anchor.MIDDLE;
	}

	private static int offsetOf(Anchor anchor, float fraction, int screen) {
		int usable = screen - 2 * MARGIN;
		return Math.round(switch (anchor) {
			case START -> fraction * usable;
			case MIDDLE -> (fraction - 0.5f) * usable;
			case END -> (1 - fraction) * usable;
		});
	}

	private static boolean isColor(String code) {
		return code != null && code.length() == 1 && COLOR_CODES.indexOf(code.charAt(0)) >= 0;
	}

	private static float clamp(float value, float min, float max) {
		return Float.isNaN(value) ? min : Math.max(min, Math.min(max, value));
	}
}
