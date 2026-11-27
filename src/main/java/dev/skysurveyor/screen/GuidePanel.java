package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.FocusHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.calc.Fiesta;
import dev.skysurveyor.calc.FiestaText;
import dev.skysurveyor.calc.GameData;
import dev.skysurveyor.calc.GuideHelp;
import dev.skysurveyor.calc.GuideProgress;
import dev.skysurveyor.calc.GuideText;
import dev.skysurveyor.calc.Layouts;
import dev.skysurveyor.calc.Learner;
import dev.skysurveyor.calc.LiveGuide;
import dev.skysurveyor.calc.NumberId;
import dev.skysurveyor.calc.PastShafts;
import dev.skysurveyor.calc.ShaftModel;
import dev.skysurveyor.calc.Verdict;
import dev.skysurveyor.calc.YourNumbers;
import dev.skysurveyor.calc.YourNumbers.Num;
import dev.skysurveyor.calc.YourNumbers.Source;
import dev.skysurveyor.data.CorpseType;
import dev.skysurveyor.data.ShaftNames;
import dev.skysurveyor.effects.Consumable;
import dev.skysurveyor.money.CoinBreakdown;
import dev.skysurveyor.money.Coins;
import dev.skysurveyor.settings.CalculatorSettings.KeyLoot;
import dev.skysurveyor.util.Format;

import static dev.skysurveyor.screen.ScreenParts.PANEL;
import static dev.skysurveyor.screen.ScreenParts.applyTooltip;
import static dev.skysurveyor.screen.ScreenParts.label;

// Docs/calculator-design.md, mockups/calculator-v3.html
final class GuidePanel {
	// display choices, not game numbers
	private static final int GAP = 5;
	private static final int TILES = 4;
	// all 4 dont fit
	private static final int TILES_NARROW = 2;
	private static final int TILE_MIN = 100;
	private static final int ROW = 13;
	private static final int BOX = 46;
	private static final int ICON = 5;
	// narrower = segments stack
	private static final int SEGMENT_MIN = 230;
	private static final int TIP_WIDTH = 220;
	// box around on / off chips, so they dont read as range options
	private static final int TOGGLE_EDGE = 0x40FFFFFF;
	private static final int HELP_MIN = 200;
	private static final int HELP_GAP = 12;
	private static final String HELP_CHIP = "? " + GuideHelp.BUTTON;
	// same icon as settings' reset all
	private static final Identifier RESET_ALL_ICON = Identifier.fromNamespaceAndPath("skysurveyor", "textures/gui/reset_red_icon.png");

	private final JournalScreen screen;
	private final List<Field> fields = new ArrayList<>();
	// fields not rehomed since the build
	private boolean needsRehome;
	// guide version on show
	private int shown = -1;
	// closed till asked for, kept while the game runs. display choice (nick 11-26)
	private static boolean layoutsOpen;
	private static boolean pastOpen;
	private static boolean helpOpen;
	// folded to the bar till asked for (review 11-27)
	private static boolean numbersOpen;
	// reset all asks twice, like settings. any other rebuild lets go
	private boolean resetArmed;
	private boolean keepArmed;
	// off by more = yellow. display choice
	private static final double PAST_OFF = 0.10;
	// key of the box to focus after the rebuild
	private String refocus;

	private static final class Field {
		final java.util.function.Function<String, Double> parse;
		// config.json key, per fiesta state for 3
		final String key;
		final TextBoxComponent box;
		final String shown;
		boolean wasFocused;

		Field(java.util.function.Function<String, Double> parse, String key, TextBoxComponent box, String shown) {
			this.parse = parse;
			this.key = key;
			this.box = box;
			this.shown = shown;
		}
	}

	GuidePanel(JournalScreen screen) {
		this.screen = screen;
	}

	YourNumbers numbers() {
		return Surveyor.GUIDE.numbers();
	}

	// 0 = the range
	private static int minedShafts() {
		return Surveyor.SETTINGS.get().calculator().minedShafts().count();
	}

	// null = no prices yet
	ShaftModel.Guide guide() {
		return Surveyor.GUIDE.guide();
	}

	// a newer guide landed
	boolean stale() {
		return Surveyor.GUIDE.version() != shown;
	}

	void build(FlowLayout content, int width) {
		fields.clear();
		needsRehome = true;
		shown = Surveyor.GUIDE.version();
		if (!keepArmed) resetArmed = false;
		keepArmed = false;
		YourNumbers yours = numbers();
		FlowLayout column = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		column.gap(GAP);
		int inner = width - 2 * JournalScreen.PADDING - KeptScroll.GUTTER - 2;
		column.child(rangeRow(inner));
		GuideProgress progress = Surveyor.GUIDE.cachedProgress();
		if (progress.shows()) column.child(progressRow(progress, inner));
		if (helpOpen) column.child(helpPanel(inner));
		if (yours == null) {
			// 1st build still running, stale() redraws
			String text = Surveyor.GUIDE.failed() ? "§cCouldn't work out your numbers, the log says why" : "§7Working out your numbers";
			column.child(JournalScreen.panelWith(label(text), Sizing.fill(100), Sizing.content()));
			content.child(column);
			return;
		}
		ShaftModel.Guide worth = guide();
		column.child(tiles(yours, worth, inner));
		column.child(worth == null ? noPrices() : table(yours, worth, inner));
		column.child(numbersBar(yours, inner));
		if (numbersOpen) column.child(segments(yours, inner));
		if (layoutsOpen && !yours.layouts().isEmpty()) {
			column.child(layoutPanel(yours, inner, false));
			column.child(layoutPanel(yours, inner, true));
		}
		if (pastOpen) column.child(pastPanel(inner));
		KeptScroll scroll = screen.scroll(Sizing.fill(100), Sizing.fill(100), column);
		scroll.tag("guide");
		column.padding(Insets.right(KeptScroll.GUTTER));
		content.child(scroll);
	}

	// every range on show, so u see what u can pick. same range as entry title + hud
	private FlowLayout rangeRow(int inner) {
		Font font = font();
		int mined = minedShafts();
		List<String> wordTip = List.of("§f" + RangeBar.WORD, "§7Which sessions your spawn numbers come from", "§7Recent sessions follow how you play now,",
				"§7more sessions give steadier numbers", mined > 0 ? "§8Mining numbers use your newest " + mined + " mined shafts" : "§8Mining numbers use it too",
				"§8Change that in Settings, Journal", "§8Corpse odds always use every shaft");
		FlowLayout help = toggles(List.of(chip(HELP_CHIP, helpOpen,
				List.of("§f" + GuideHelp.BUTTON, "§7" + (helpOpen ? "Hide" : "Show") + " what this page tells you and", "§7where its numbers come from"), () -> {
					helpOpen = !helpOpen;
					screen.rebuild(true);
				})));
		return RangeBar.build(font, inner, LiveGuide.range(), wordTip, range -> {
			LiveGuide.range(range);
			screen.rebuild(true);
		}, help, chipWidth(font, HELP_CHIP) + 2);
	}

	// gone once nothing is missing
	private static FlowLayout progressRow(GuideProgress progress, int inner) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.surface(PANEL);
		row.padding(Insets.of(4, 4, 6, 6));
		row.gap(4);
		UIComponent icon = icon(Source.START);
		// level w/ the 1st text line
		icon.margins(Insets.top(2));
		row.child(icon);
		row.child(helpText(progress.line(), inner - 12 - ICON - 4));
		tip(row, progress.hover());
		return row;
	}

	private static int chipWidth(Font font, String text) {
		return RangeBar.chipWidth(font, text);
	}

	private static FlowLayout chip(String text, boolean on, List<String> tip, Runnable click) {
		return RangeBar.chip(text, on, tip, click);
	}

	// on / off chips in one box = one group
	private static FlowLayout toggles(List<FlowLayout> chips) {
		FlowLayout group = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		group.surface(Surface.outline(TOGGLE_EDGE));
		group.padding(Insets.of(1));
		for (FlowLayout chip : chips) {
			// line between, else 2 chips read as one
			if (!group.children().isEmpty()) {
				FlowLayout line = UIContainers.horizontalFlow(Sizing.fixed(1), Sizing.fixed(RangeBar.CHIP_HEIGHT));
				line.surface(Surface.flat(TOGGLE_EDGE));
				group.child(line);
			}
			group.child(chip);
		}
		return group;
	}

	// GuideHelp's blocks, read across. columns by width
	private FlowLayout helpPanel(int inner) {
		FlowLayout panel = panel(Sizing.fill(100), GuideHelp.TITLE, null);
		int space = inner - 12;
		int columns = Math.max(1, Math.min(3, (space + HELP_GAP) / (HELP_MIN + HELP_GAP)));
		int columnWidth = (space - (columns - 1) * HELP_GAP) / columns;
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(HELP_GAP);
		row.margins(Insets.top(3));
		List<FlowLayout> flows = new ArrayList<>();
		for (int i = 0; i < columns; i++) {
			FlowLayout flow = UIContainers.verticalFlow(Sizing.fixed(columnWidth), Sizing.content());
			flow.gap(8);
			flows.add(flow);
			row.child(flow);
		}
		List<GuideHelp.Block> blocks = GuideHelp.blocks(minedShafts());
		for (int i = 0; i < blocks.size(); i++) {
			GuideHelp.Block block = blocks.get(i);
			FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
			box.gap(3);
			box.child(label("§f" + block.title()));
			for (GuideHelp.Line line : block.lines()) {
				if (line.icon() == null) {
					box.child(helpText("§7" + line.text(), columnWidth));
					continue;
				}
				Look look = LOOKS.get(line.icon());
				FlowLayout iconLine = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
				iconLine.gap(4);
				UIComponent icon = icon(line.icon());
				// level w/ the 1st text line
				icon.margins(Insets.top(2));
				iconLine.child(icon);
				iconLine.child(helpText(look.colour() + look.name() + "§7: " + line.text(), columnWidth - ICON - 4));
				box.child(iconLine);
			}
			flows.get(i % columns).child(box);
		}
		panel.child(row);
		return panel;
	}

	// fixed width = owo wraps it
	private static LabelComponent helpText(String text, int width) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(width));
		return label;
	}

	// name = hover title
	private record Col(String key, String title, int width, String name, String tip) {
		Col(String key, String title, int width, String tip) {
			this(key, title, width, title, tip);
		}
	}

	// dropped in this order
	private static final List<String> DROP = List.of("campfire", "umber", "tungsten");
	private static final List<String> LEFT = List.of("name", "from", "campfire");
	// corpse odds, Learner takes these over each shaft
	private static final List<NumberId> ALL_TIME = List.of(NumberId.EXTRA_CORPSE, NumberId.LAPIS_SHARE, NumberId.EXTRA_ROLL);
	private static final int COL_GAP = 4;
	private static final int FROM_GAP = 6;
	private static final int YES = 0x601D7A2A;
	private static final int CLOSE = 0x606A5D13;
	private static final int NO = 0x605E1C1C;
	private static final int SWATCH = 7;
	private static final String CELL_WORDS = "What mining a shaft makes over leaving it and spawning the next one";

	private FlowLayout table(YourNumbers yours, ShaftModel.Guide worth, int inner) {
		Font font = font();
		ShaftNames names = ShaftNames.defaults();
		int nameWidth = 0;
		for (ShaftModel.Row row : worth.rows()) nameWidth = Math.max(nameWidth, font.width(row.gem().name()));
		// "+556.7k" beats "+12.1M", lost its + on scale 3 lol
		int cell = font.width("+12.1M");
		int keyCell = font.width("-12.1M");
		for (ShaftModel.Row row : worth.rows()) {
			if (row.perFine() == null) continue;
			for (ShaftModel.Shaft shaft : row.lapis()) cell = Math.max(cell, font.width(Coins.signed(shaft.gain())));
			for (CorpseType type : CorpseType.KEY_LOOT) {
				Double gain = worth.keyGain(row, 0, type.label());
				if (gain != null) keyCell = Math.max(keyCell, font.width(Coins.signed(gain)));
			}
		}
		cell += 6;
		List<Col> cols = new ArrayList<>();
		cols.add(new Col("name", "Shaft", nameWidth + 4, "Hover a gem for its ticks, price\nand coins per hour inside"));
		for (int k = 0; k <= ShaftModel.MAX_LAPIS; k++) {
			cols.add(new Col("lapis" + k, String.valueOf(k), cell, GuideText.lapis(k), "Mining it over leaving, with " + k + " Lapis looted\n"
					+ "§aGreen §7mine, §eyellow §7a small gain, §cred §7skip\n§8Yellow from 0 up to " + Coins.format(worth.band()) + "\n§8"
					+ Format.percent(worth.lapisChance()[k]) + " of shafts have " + k + " Lapis"));
		}
		// widest answer, not only the title
		int answer = Math.max(font.width(GuideText.lapis(ShaftModel.MAX_LAPIS)), font.width("no price"));
		cols.add(new Col("from", "Mine from", Math.max(font.width("Mine from"), answer) + 4 + FROM_GAP,
				"Mine it from this many Lapis looted:\nthe first green cell\n§cSkip §7never green"));
		cols.add(new Col("campfire", "Campfire", Math.max(font.width("Campfire"), answer) + 4,
				"Place one from this many Lapis looted:\nthe first green count\n§7Never never green"));
		for (CorpseType key : CorpseType.KEY_LOOT) {
			String type = key.label();
			cols.add(new Col(type.toLowerCase(Locale.ROOT), type + " key", Math.max(font.width(type + " key"), keyCell) + 6,
					"Looting it first: its loot and the Pristine\nit adds, minus the key\n§8Yellow from 0 up to " + Coins.format(worth.keyBand())));
		}
		int available = inner - 12;
		for (String drop : DROP) {
			if (width(cols) <= available) break;
			cols.removeIf(c -> c.key().equals(drop));
		}
		int lapisWidth = cell + Math.max(0, available - width(cols)) / (ShaftModel.MAX_LAPIS + 1);

		FlowLayout panel = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		panel.surface(PANEL);
		panel.padding(Insets.of(5, 5, 6, 6));
		panel.gap(1);
		panel.child(cellLegend(worth, available));
		panel.child(lapisHeading(cols, lapisWidth));
		FlowLayout header = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW));
		header.gap(COL_GAP);
		header.verticalAlignment(VerticalAlignment.CENTER);
		for (Col col : cols) {
			LabelComponent title = label("§7" + col.title());
			FlowLayout box = cellBox(col, lapisWidth, !LEFT.contains(col.key()));
			box.child(title);
			List<String> tipLines = new ArrayList<>(List.of("§f" + col.name()));
			for (String line : col.tip().split("\n")) tipLines.add(line.startsWith("§") ? line : "§7" + line);
			tip(box, tipLines);
			header.child(box);
		}
		panel.child(header);
		panel.child(JournalScreen.divider());
		int index = 0;
		for (ShaftModel.Row row : worth.rows()) {
			FlowLayout line = new HoverFlow(Sizing.fill(100), Sizing.fixed(ROW), HoverFlow.ROW);
			line.gap(COL_GAP);
			line.verticalAlignment(VerticalAlignment.CENTER);
			if (index++ % 2 == 1) line.surface(Surface.flat(0x08FFFFFF));
			String code = row.gem().code();
			for (Col col : cols) {
				FlowLayout box = cellBox(col, lapisWidth, !LEFT.contains(col.key()));
				switch (col.key()) {
					case "name" -> {
						// no mined shaft of it: ticks + efficiency are worked out, not his
						boolean measured = Surveyor.GUIDE.cachedMeasured().contains(code);
						box.child(label("§" + colour(names, code) + row.gem().name() + (measured ? "" : " §8?")));
						List<String> lines = gemTip(row, yours, names, worth);
						if (!measured) {
							lines.add("");
							lines.add("§eNot measured");
							lines.add("§7No mined " + row.gem().name() + " shaft yet, so its row");
							lines.add("§7is worked out from your other shafts");
						}
						tip(box, lines);
					}
					case "from" -> box.child(label(row.perFine() == null ? "§8no price" : GuideText.mineFrom(row)));
					case "campfire" -> {
						if (row.perFine() == null) {
							box.child(label("§8-"));
						} else if (worth.campfireCost() == null) {
							box.child(label("§8no price"));
						} else {
							box.child(label(GuideText.campfire(row, worth.band())));
							tip(box, campfireTip(row, worth));
						}
					}
					case "tungsten", "umber" -> {
						String type = CorpseType.valueOf(col.key().toUpperCase(Locale.ROOT)).label();
						// never mined = nothing for pristine
						Double gain = row.mineFrom() == null ? worth.keyGainSkipped(type) : worth.keyGain(row, 0, type);
						if (gain == null) {
							box.child(label("§8-"));
						} else {
							box.surface(Surface.flat(state(gain, worth.keyBand())));
							box.child(label(GuideText.colour(gain, worth.keyBand()) + Coins.signed(gain)));
							tip(box, keyTip(row, worth, type));
						}
					}
					default -> {
						int k = col.key().charAt(5) - '0';
						ShaftModel.Shaft shaft = row.lapis().get(k);
						if (row.perFine() == null) {
							box.child(label("§8-"));
						} else {
							box.surface(Surface.flat(state(shaft.gain(), worth.band())));
							box.child(label(GuideText.colour(shaft.gain(), worth.band()) + Coins.signed(shaft.gain())));
							tip(box, cellTip(row, k, worth));
						}
					}
				}
				line.child(box);
			}
			panel.child(line);
		}
		return panel;
	}

	// what a cell is + the colours, always on (review 11-27)
	private static FlowLayout cellLegend(ShaftModel.Guide worth, int available) {
		Font font = font();
		FlowLayout swatches = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		swatches.gap(10);
		swatches.verticalAlignment(VerticalAlignment.CENTER);
		String band = Coins.format(worth.band());
		String keyBand = Coins.format(worth.keyBand());
		swatches.child(swatch(YES, "§aMine", List.of("§aMine", "§7Makes " + band + " or more over leaving", "§8Key columns from " + keyBand)));
		swatches.child(swatch(CLOSE, "§eYour call", List.of("§eYour call", "§7Makes 0 up to " + band + " over leaving", "§8Key columns up to " + keyBand,
				"§8Change it under Your numbers")));
		swatches.child(swatch(NO, "§cSkip", List.of("§cSkip", "§7Makes less than leaving")));
		int swatchesWidth = 20 + 3 * (SWATCH + 3) + font.width("Mine") + font.width("Your call") + font.width("Skip");
		LabelComponent words = label("§7" + CELL_WORDS);
		boolean oneRow = font.width(CELL_WORDS) + 12 + swatchesWidth <= available;
		FlowLayout legend = oneRow ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content()) : UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		legend.gap(oneRow ? 12 : 3);
		legend.verticalAlignment(VerticalAlignment.CENTER);
		legend.padding(Insets.of(0, 4, 2, 2));
		legend.child(oneRow ? words : helpText("§7" + CELL_WORDS, available - 4));
		if (oneRow) legend.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
		legend.child(swatches);
		return legend;
	}

	private static FlowLayout swatch(int colour, String text, List<String> tip) {
		FlowLayout item = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		item.gap(3);
		item.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout square = UIContainers.horizontalFlow(Sizing.fixed(SWATCH), Sizing.fixed(SWATCH));
		square.surface(Surface.flat(colour));
		item.child(square);
		item.child(label(text));
		tip(item, tip);
		return item;
	}

	// "Lapis corpses" over its 5 columns
	private static FlowLayout lapisHeading(List<Col> cols, int lapisWidth) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW));
		row.gap(COL_GAP);
		for (Col col : cols) {
			if (!col.key().startsWith("lapis")) {
				row.child(UIContainers.horizontalFlow(Sizing.fixed(col.width()), Sizing.fixed(ROW)));
				continue;
			}
			if (!col.key().equals("lapis0")) continue;
			int lapisCols = ShaftModel.MAX_LAPIS + 1;
			FlowLayout group = UIContainers.verticalFlow(Sizing.fixed(lapisCols * lapisWidth + (lapisCols - 1) * COL_GAP), Sizing.fixed(ROW));
			group.horizontalAlignment(HorizontalAlignment.CENTER);
			group.verticalAlignment(VerticalAlignment.BOTTOM);
			group.gap(1);
			group.child(label("§fLapis corpses"));
			FlowLayout line = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(1));
			line.surface(Surface.flat(TOGGLE_EDGE));
			group.child(line);
			tip(group, List.of("§fLapis corpses", "§7How many Lapis corpses you loot in the shaft", "§8Hover a column for how often you get that many"));
			row.child(group);
		}
		return row;
	}

	private static int width(List<Col> cols) {
		return cols.stream().mapToInt(Col::width).sum() + COL_GAP * (cols.size() - 1);
	}

	private static FlowLayout cellBox(Col col, int lapisWidth, boolean right) {
		int width = col.key().startsWith("lapis") ? lapisWidth : col.width();
		FlowLayout box = UIContainers.horizontalFlow(Sizing.fixed(width), Sizing.fixed(ROW));
		box.verticalAlignment(VerticalAlignment.CENTER);
		box.horizontalAlignment(right ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
		// else "+4.7M Always" reads as one cell
		box.padding(col.key().equals("from") ? Insets.left(FROM_GAP) : Insets.horizontal(right ? 2 : 0));
		return box;
	}

	private static int state(double gain, double band) {
		return switch (Verdict.State.of(gain, band)) {
			case MINE -> YES;
			case CLOSE -> CLOSE;
			case SKIP -> NO;
		};
	}

	// null = u freeze b4 the route ends
	private static String routeEnd(ShaftModel.Guide worth, String layout) {
		ShaftModel.Row row = worth.layouts().get(layout);
		if (row == null) return null;
		double inside = row.lapis().getFirst().seconds();
		return inside < worth.freeze() - 1 ? "route done after " + NumberId.FREEZE.format(inside) : null;
	}

	private static List<String> gemTip(ShaftModel.Row row, YourNumbers yours, ShaftNames names, ShaftModel.Guide worth) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + row.gem().name());
		lines.add("§7" + NumberId.TICKS_4000.format(row.ticks()) + " ticks a block, " + NumberId.TICKS_4000.format(row.boostTicks()) + " in boost");
		if (row.perFine() == null) lines.add("§8No price for its gems yet");
		else lines.add("§7Fine at §6" + Coins.format(row.perFine()) + " §7(" + CoinBreakdown.sellName(Surveyor.MONEY.settings()) + ")");
		// was the Inside / h column
		if (row.perFine() != null) {
			lines.add("§7Inside §f" + Coins.format(row.insidePerHour()) + " / h §7with no Lapis looted");
			lines.add("§8Gems and other drops, setup time counted");
		}
		lines.add("§7" + Format.percent(row.chance()) + " of the shafts you spawn");
		List<GameData.Layout> layouts = GameData.defaults().layoutsOf(row.gem().code()).stream().filter(l -> yours.layouts().containsKey(l.id())).toList();
		if (layouts.isEmpty()) return lines;
		lines.add("");
		lines.add("§7Efficiency per layout");
		for (GameData.Layout layout : layouts) {
			YourNumbers.LayoutNum num = yours.layouts().get(layout.id());
			lines.add("§7" + Layouts.name(names, layout) + " §f" + Layouts.format(num.efficiency().value()) + " §8"
					+ sourceName(num.efficiency().source()).toLowerCase(Locale.ROOT) + ", " + Format.percent(num.weight()) + " of them");
			String end = routeEnd(worth, layout.id());
			if (end != null) lines.add("§8  " + end);
		}
		return lines;
	}

	private static List<String> cellTip(ShaftModel.Row row, int k, ShaftModel.Guide worth) {
		ShaftModel.Shaft shaft = row.lapis().get(k);
		List<String> lines = new ArrayList<>();
		lines.add("§f" + row.gem().name() + ", " + GuideText.lapis(k));
		lines.add("§7" + NumberId.FREEZE.format(shaft.seconds())
				+ (shaft.seconds() < worth.freeze() - 1 ? " inside on average, a route ends before you freeze" : " inside until you freeze"));
		lines.add("");
		lines.add("§7Gems §8" + Math.round(shaft.fine()) + " Fine §6+" + Coins.format(shaft.gems()));
		lines.add("§7Other drops §6+" + Coins.format(shaft.other()));
		lines.add("§7Leaving instead §c-" + Coins.format(shaft.away() * worth.leaving()));
		lines.add("§8" + NumberId.FREEZE.format(shaft.away()) + " with the way back, at " + Coins.format(worth.perHour(worth.leaving())) + " / h");
		lines.add("§8That is your loop with the yellow ones mined too");
		lines.add("");
		lines.add("§fGain over leaving " + GuideText.colour(shaft.gain(), worth.band()) + Coins.signed(shaft.gain()));
		if (k < ShaftModel.MAX_LAPIS) {
			double more = row.lapis().get(k + 1).gain() - shaft.gain();
			lines.add("§8One more Lapis " + Coins.signed(more));
		}
		return lines;
	}

	private static List<String> keyTip(ShaftModel.Row row, ShaftModel.Guide worth, String type) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + type + " corpse in " + row.gem().name());
		lines.add("§7Loot §6+" + Coins.format(worth.keyLoot()));
		// no gem price = no pristine
		Double gain = worth.keyGain(row, 0, type);
		if (gain != null) lines.add("§7Pristine it adds §6+" + Coins.format(row.lapis().getFirst().pristine()));
		lines.add("§7Key §c-" + Coins.format(worth.keyCosts().get(type)));
		lines.add("");
		double skipped = worth.keyGainSkipped(type);
		if (gain != null) lines.add("§fGain " + GuideText.colour(gain, worth.keyBand()) + Coins.signed(gain) + " §7if you mine the shaft");
		lines.add("§7Skipping it, no Pristine " + GuideText.colour(skipped, worth.keyBand()) + Coins.signed(skipped));
		lines.add("§8Same at any Lapis count");
		lines.add("§8Worth less the later you loot it");
		return lines;
	}

	private static List<String> campfireTip(ShaftModel.Row row, ShaftModel.Guide worth) {
		List<String> lines = new ArrayList<>();
		lines.add("§fA campfire in " + row.gem().name());
		lines.add("§7Adds §f" + NumberId.CAMPFIRE.format(worth.campfire()) + " §7of mining, costs §c" + Coins.format(worth.campfireCost()));
		lines.add("§7Gain over leaving:");
		for (int k = 0; k <= ShaftModel.MAX_LAPIS; k++) {
			double gain = row.lapis().get(k).campfire();
			lines.add("§7" + GuideText.lapis(k) + " " + GuideText.colour(gain, worth.band()) + Coins.signed(gain));
		}
		return lines;
	}

	private static FlowLayout noPrices() {
		return JournalScreen.panelWith(label("§7Waiting for Bazaar prices"), Sizing.fill(100), Sizing.content());
	}

	List<String> footer() {
		// per layout has boxes too
		return numbersOpen || layoutsOpen ? List.of("§7Click a number to type your own", "§7Right-click it to undo")
				: List.of("§7Hover a cell for how it adds up", "§7Hover a gem for its ticks and price");
	}

	private FlowLayout tiles(YourNumbers yours, ShaftModel.Guide worth, int inner) {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(GAP);
		// freeze, campfire + spawn gem are in your numbers (review 11-27). no prices = fiesta only
		int count = worth == null ? 1 : TILES;
		int perRow = (inner - (count - 1) * GAP) / count >= TILE_MIN ? count : Math.min(count, TILES_NARROW);
		int tileWidth = (inner - (perRow - 1) * GAP) / perRow;
		if (worth != null) {
			// green only is the headline, cells stay vs the best loop (nick 11-26, research/11 s18)
			String leaving = Coins.format(worth.perHour(worth.greenOnly())) + " / h";
			List<String> tip = new ArrayList<>(List.of("§fLeaving is worth " + leaving, "§7What you make per hour if you leave, spawn",
					"§7again and mine only the green shafts", ""));
			tip.add("§7Yellow ones too §f" + Coins.format(worth.perHour(worth.leaving())) + " / h");
			tip.add("§8A cell's gain is against that one");
			tip.add("");
			double cycle = yours.get(NumberId.SPAWN) + yours.get(NumberId.NOT_MINING);
			tip.add("§7A skipped shaft takes §f" + NumberId.SPAWN.format(cycle));
			tip.add("§7A green one §f" + NumberId.FREEZE.format(worth.away()) + " §7more on average, way back counted");
			if (!yours.ownPace()) tip.add("§8Starting values until your mining is tracked");
			row.child(tile(tileWidth, "Leaving is worth", "§6" + leaving, tip));
			row.child(tile(tileWidth, "Worth mining", "§f" + Format.percent(worth.minedShare()) + " §7of shafts",
					List.of("§fWorth mining " + Format.percent(worth.minedShare()), "§7Share of the shafts you spawn that are green:",
							"§7each cell by how often you spawn that gem", "§7and get that many Lapis",
							"§8Yellow ones too " + Format.percent(worth.minedShare() + worth.closeShare()))));
			row.child(tile(tileWidth, "Jasper only", "§f" + Coins.format(worth.perHour(worth.jasperOnly())) + " / h",
					List.of("§fJasper only", "§7Per hour if you mine only Jasper shafts", "§7and skip the rest, at your spawn gem's mix")));
		}
		long now = System.currentTimeMillis();
		Fiesta.Reading fiesta = Surveyor.FIESTA.reading(now);
		Long flask = Surveyor.flaskRunning() ? Surveyor.EFFECTS.left().get(Consumable.FIESTA_FLASK) : null;
		// last takes the leftover px
		int lastWidth = perRow == count ? inner - (count - 1) * (tileWidth + GAP) : tileWidth;
		row.child(tile(lastWidth, "Mining Fiesta", FiestaText.tile(fiesta, flask), FiestaText.tip(fiesta, flask, now)));
		if (perRow == count) return row;
		FlowLayout rows = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		rows.gap(GAP);
		List<UIComponent> tiles = List.copyOf(row.children());
		FlowLayout current = null;
		for (int i = 0; i < tiles.size(); i++) {
			if (i % perRow == 0) {
				current = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
				current.gap(GAP);
				rows.child(current);
			}
			row.removeChild(tiles.get(i));
			current.child(tiles.get(i));
		}
		return rows;
	}

	private static String colour(ShaftNames names, String code) {
		Character color = names.hudColor(code);
		return color == null ? "f" : String.valueOf(color);
	}

	private FlowLayout tile(int width, String name, String value, List<String> tip) {
		FlowLayout tile = UIContainers.verticalFlow(Sizing.fixed(width), Sizing.content());
		tile.surface(PANEL);
		tile.padding(Insets.of(4, 4, 5, 5));
		tile.gap(2);
		tile.child(label("§7" + clip(name, width - 10)));
		tile.child(label(clip(value, width - 10)));
		tip(tile, tip);
		return tile;
	}

	// title + what it can show on the left, reset alone on the right (nick 11-27). closed = counts per source (review 11-27)
	private FlowLayout numbersBar(YourNumbers yours, int inner) {
		Font font = font();
		LabelComponent title = label("§fYour numbers");
		tip(title, List.of("§fYour numbers", "§7What the list is worked out from", numbersOpen ? "§8Click one to type your own" : "§8Show opens them"));
		FlowLayout show = toggles(List.of(chip(numbersOpen ? "Hide" : "Show", numbersOpen,
				List.of("§fYour numbers", "§7" + (numbersOpen ? "Hide" : "Show") + " the numbers the list is worked out from", "§7and type your own"), () -> {
					numbersOpen = !numbersOpen;
					screen.rebuild(true);
				})));
		int showWidth = chipWidth(font, numbersOpen ? "Hide" : "Show") + 2;
		List<FlowLayout> views = new ArrayList<>();
		int viewsWidth = 2;
		if (!yours.layouts().isEmpty()) {
			views.add(chip("Per layout", layoutsOpen,
					List.of("§fPer layout", "§7" + (layoutsOpen ? "Hide" : "Show") + " your Efficiency and route length", "§7in each shaft layout"), () -> {
						layoutsOpen = !layoutsOpen;
						screen.rebuild(true);
					}));
			viewsWidth += chipWidth(font, "Per layout") + 1;
		}
		views.add(chip("Past shafts", pastOpen,
				List.of("§fPast shafts", "§7" + (pastOpen ? "Hide" : "Show") + " what your mined shafts made", "§7against what this list says"), () -> {
					pastOpen = !pastOpen;
					screen.rebuild(true);
				}));
		viewsWidth += chipWidth(font, "Past shafts");
		FlowLayout legend = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		legend.gap(10);
		legend.verticalAlignment(VerticalAlignment.CENTER);
		int legendWidth = 0;
		Map<Source, Integer> counts = new EnumMap<>(Source.class);
		for (NumberId id : NumberId.values()) counts.merge(yours.num(id).source(), 1, Integer::sum);
		for (Source source : Source.values()) {
			int count = counts.getOrDefault(source, 0);
			// closed: only sources in use
			if (!numbersOpen && count == 0) continue;
			String words = count == 1 && source == Source.START ? "starting value" : COUNT_WORDS.get(source);
			String text = numbersOpen ? LOOKS.get(source).name() : count + " " + words;
			legend.child(legend(source, text, numbersOpen ? null : count));
			legendWidth += ICON + 3 + font.width(text) + 10;
		}
		boolean anyTyped = yours.numbers().values().stream().anyMatch(n -> n.source() == Source.SET)
				|| yours.layouts().values().stream().anyMatch(l -> l.efficiency().source() == Source.SET || l.route().source() == Source.SET);
		IconButton reset = numbersOpen && anyTyped ? resetAll() : null;
		int resetWidth = reset == null ? 0 : reset.horizontalSizing().get().value + 14;

		FlowLayout top = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		top.gap(10);
		top.verticalAlignment(VerticalAlignment.CENTER);
		// show sits w/ the title, views apart
		FlowLayout head = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		head.gap(4);
		head.verticalAlignment(VerticalAlignment.CENTER);
		head.child(title);
		head.child(show);
		top.child(head);
		top.child(toggles(views));
		top.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.fixed(1)));
		// legend gets its own line when it doesnt fit
		boolean oneRow = font.width("Your numbers") + 4 + showWidth + viewsWidth + legendWidth + resetWidth + 30 <= inner - 12;
		if (oneRow) top.child(legend);
		if (reset != null) {
			// apart from the rest
			reset.margins(Insets.left(oneRow ? 14 : 0));
			top.child(reset);
		}
		FlowLayout bar = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(PANEL);
		bar.padding(Insets.of(4, 4, 6, 6));
		bar.gap(4);
		bar.child(top);
		if (!oneRow) bar.child(legend);
		return bar;
	}

	private static final Map<Source, String> COUNT_WORDS = new EnumMap<>(
			Map.of(Source.TRACKED, "tracked", Source.SET, "set by you", Source.START, "starting values", Source.CALCULATED, "calculated"));

	// 1st click arms, 2nd wipes every typed number
	private IconButton resetAll() {
		IconButton reset = new IconButton(Component.literal(resetArmed ? "§cReally reset all?" : "§cReset all"), RESET_ALL_ICON, 8, 8, b -> {
			if (!resetArmed) {
				resetArmed = true;
				keepArmed = true;
				screen.rebuild(true);
				return;
			}
			fields.clear();
			Surveyor.SETTINGS.change(s -> s.withCalculator(s.calculator().withoutTyped()));
			screen.rebuild(true);
		});
		reset.verticalSizing(Sizing.fixed(14));
		tip(reset, resetArmed ? List.of("§fReally reset all?", "§7Click again to undo every number you typed.", "§7This can't be undone")
				: List.of("§fReset all", "§7Undo every number you typed", "§8Right-click one number to undo only that"));
		return reset;
	}

	// count null = the open legend
	private static FlowLayout legend(Source source, String text, Integer count) {
		Look look = LOOKS.get(source);
		FlowLayout item = UIContainers.horizontalFlow(Sizing.content(), Sizing.content());
		item.gap(3);
		item.verticalAlignment(VerticalAlignment.CENTER);
		item.child(icon(source));
		// yellow like a typed box
		item.child(label((source == Source.SET && count != null ? "§e" : "§7") + text));
		List<String> tip = new ArrayList<>(List.of(look.colour() + look.name(), "§7" + look.hint()));
		if (count != null) tip.add("§8" + count + " of your " + NumberId.values().length + " numbers");
		tip(item, tip);
		return item;
	}

	private FlowLayout segments(YourNumbers yours, int inner) {
		int perRow = (inner - 2 * GAP) / 3 >= SEGMENT_MIN ? 3 : 1;
		FlowLayout segments = perRow == 3 ? UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content())
				: UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		segments.gap(GAP);
		int segmentWidth = perRow == 3 ? (inner - 2 * GAP) / 3 : inner;
		// side by side = all as tall as the longest
		int most = 0;
		for (NumberId.Segment segment : NumberId.Segment.values()) {
			int rows = 0;
			for (NumberId id : NumberId.values()) {
				if (id.segment() == segment) rows++;
			}
			most = Math.max(most, rows);
		}
		NumberId.Segment lastSegment = NumberId.Segment.values()[NumberId.Segment.values().length - 1];
		for (NumberId.Segment segment : NumberId.Segment.values()) {
			// last takes the leftover px
			int width = perRow == 3 && segment == lastSegment ? inner - 2 * (segmentWidth + GAP) : segmentWidth;
			FlowLayout panel = panel(Sizing.fixed(width), segment.displayName(), null);
			int index = 0;
			for (NumberId id : NumberId.values()) {
				if (id.segment() != segment) continue;
				panel.child(numberRow(id, yours, width - 12, index++));
			}
			for (int i = index; perRow == 3 && i < most; i++) panel.child(UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW)));
			segments.child(panel);
		}
		return segments;
	}

	// name + box + icon
	private static final int LAYOUT_MIN = 170;

	// heading tip null = none
	private static FlowLayout panel(Sizing width, String heading, List<String> tip) {
		FlowLayout panel = UIContainers.verticalFlow(width, Sizing.content());
		panel.surface(PANEL);
		panel.padding(Insets.of(5, 5, 6, 6));
		panel.gap(1);
		LabelComponent title = label("§7" + heading);
		title.margins(Insets.bottom(2));
		if (tip != null) tip(title, tip);
		panel.child(title);
		panel.child(JournalScreen.divider());
		return panel;
	}

	// filled down then across, a gems layouts stay together. crystals last, 1 row per group
	private FlowLayout layoutPanel(YourNumbers yours, int inner, boolean route) {
		ShaftNames names = ShaftNames.defaults();
		FlowLayout panel = route
				? panel(Sizing.fill(100), "Route blocks per layout", List.of("§fRoute blocks per layout", "§7Gem blocks you break yourself until a layout's",
						"§7route is done. You leave then, even with cold left", "§80 = no route, you mine until you freeze", "§8Click one to type your own"))
				: panel(Sizing.fill(100), "Efficiency per layout", List.of("§fEfficiency per layout", "§7Your Efficiency in each shaft layout: some have",
						"§7their gems closer together than others", "§8Click one to type your own"));
		List<GameData.Layout> layouts = new ArrayList<>();
		List<GameData.Layout> crystals = new ArrayList<>();
		for (GameData.Layout layout : GameData.defaults().layouts()) {
			if (!yours.layouts().containsKey(layout.id())) continue;
			if (!Layouts.crystalGroup(layout)) layouts.add(layout);
			else if (crystals.stream().noneMatch(c -> route || c.group().equals(layout.group()))) crystals.add(layout);
		}
		int plain = layouts.size();
		layouts.addAll(crystals);
		int space = inner - 12;
		int columns = Math.max(1, Math.min(3, (space + GAP) / (LAYOUT_MIN + GAP)));
		int perColumn = (layouts.size() + columns - 1) / columns;
		int columnWidth = (space - (columns - 1) * GAP) / columns;
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		row.gap(GAP);
		FlowLayout column = null;
		int made = 0;
		int i = 0;
		while (i < layouts.size()) {
			String gem = layouts.get(i).gem();
			int end = i;
			// crystals = 1 block
			while (end < layouts.size() && (i >= plain || end < plain && layouts.get(end).gem().equals(gem))) end++;
			// next column if this gem ends further past the even split than it starts short of it
			if (column == null || made < columns && column.children().size() + (end - i) / 2.0 > perColumn) {
				column = UIContainers.verticalFlow(Sizing.fixed(columnWidth), Sizing.content());
				column.gap(1);
				row.child(column);
				made++;
			}
			for (; i < end; i++) {
				column.child(route ? routeRow(layouts.get(i), yours, names, columnWidth, column.children().size())
						: layoutRow(layouts.get(i), yours, names, columnWidth, column.children().size()));
			}
		}
		panel.child(row);
		return panel;
	}

	private FlowLayout layoutRow(GameData.Layout layout, YourNumbers yours, ShaftNames names, int width, int index) {
		YourNumbers.LayoutNum mine = yours.layouts().get(layout.id());
		String name = Layouts.groupName(names, layout, GameData.defaults());
		return row(name, Layouts.format(mine.efficiency().value()), mine.efficiency(), NumberId.EFFICIENCY, Layouts.typedKey(layout), width, index,
				layoutTip(name, mine, layout, yours, names));
	}

	private FlowLayout pastPanel(int inner) {
		PastShafts.Check check = Surveyor.GUIDE.cachedPast();
		ShaftNames names = ShaftNames.defaults();
		FlowLayout panel = panel(Sizing.fill(100), "Past shafts", List.of("§fPast shafts", "§7Your newest " + PastShafts.NEWEST + " mined shafts: what the list",
				"§7says for that layout and corpse count, against", "§7what you made. Gems and other drops at today's", "§7prices, corpse loot left out", "§7Shafts with a campfire are left out too",
				"§8Your numbers come from the newest of these, so on", "§8average they match. Look at how far single shafts are off"));
		if (check.lines().isEmpty()) {
			panel.child(label("§8No mined shafts yet"));
			return panel;
		}
		double off = check.medianOff();
		int count = check.lines().size();
		// median, said plainly
		panel.child(label((count == 1 ? "§7Your shaft made §f" : "§7Your middle shaft made §f") + Format.percent(Math.abs(off))
				+ (off >= 0 ? " more" : " less") + " §7than the list said" + (count == 1 ? "" : " §8(" + count + " shafts)")));
		int index = 0;
		for (PastShafts.Line line : check.lines()) {
			FlowLayout row = new HoverFlow(Sizing.fill(100), Sizing.fixed(ROW), HoverFlow.ROW);
			row.verticalAlignment(VerticalAlignment.CENTER);
			row.gap(8);
			row.padding(Insets.horizontal(2));
			if (index % 2 == 1) row.surface(Surface.flat(0x08FFFFFF));
			String name = line.entry().shaftId() == null ? "Mineshaft" : names.shaftName(line.entry().shaftId());
			LabelComponent title = label("§" + colour(names, line.entry().shaftType()) + name + " §8" + line.looted() + " looted, "
					+ Format.ago(System.currentTimeMillis() - line.entry().enteredAt()));
			title.horizontalSizing(Sizing.expand());
			row.child(title);
			row.child(label("§7List §f" + Coins.format(line.predicted())));
			row.child(label("§7Made §6" + Coins.format(line.made())));
			LabelComponent diff = label((Math.abs(line.off()) > PAST_OFF ? "§e" : "§7") + (line.off() >= 0 ? "+" : "-") + Format.percent(Math.abs(line.off())));
			diff.horizontalSizing(Sizing.fixed(30));
			row.child(diff);
			panel.child(row);
			index++;
		}
		return panel;
	}

	private FlowLayout routeRow(GameData.Layout layout, YourNumbers yours, ShaftNames names, int width, int index) {
		Num num = yours.layouts().get(layout.id()).route();
		String name = Layouts.routeName(names, layout);
		String value = Layouts.formatRoute(num.value());
		List<String> lines = new ArrayList<>();
		lines.add("§f" + name);
		lines.add("§7Gem blocks you break until the route is done");
		lines.add("");
		String soFar = num.tracked() == null ? null
				: "§8Your " + num.basis() + (num.basis() == 1 ? " shaft" : " shafts") + " left early here: " + Layouts.formatRoute(num.tracked());
		switch (num.source()) {
			case SET -> {
				lines.add("§eSet by you §f" + value);
				if (soFar != null) lines.add(soFar);
				lines.add("§8Right-click to undo");
			}
			case TRACKED -> {
				lines.add("§aTracked §f" + value + " §8" + basis(num, false));
				lines.add("§8The newest " + Layouts.LAST_SHAFTS + " you left early here, whatever the range");
			}
			case START -> {
				lines.add("§7Starting value §f" + value);
				lines.add("§8No limit, you mine until you freeze");
				lines.add("§8Yours after " + Layouts.MIN_ROUTE_SHAFTS + " shafts here you leave with cold left");
				if (soFar != null) lines.add(soFar);
			}
			default -> {
				lines.add("§9Calculated §f" + value);
				lines.add("§8From the routes players use, simulated");
				lines.add("§8Yours after " + Layouts.MIN_ROUTE_SHAFTS + " shafts here you leave with cold left");
				if (soFar != null) lines.add(soFar);
			}
		}
		ShaftModel.Guide worth = guide();
		ShaftModel.Row row = worth == null ? null : worth.layouts().get(layout.id());
		// crystals: 1 route, each gem at its own pace
		if (row != null && num.value() > 0 && !Layouts.crystalGroup(layout)) {
			double inside = row.lapis().getFirst().seconds();
			lines.add(inside < worth.freeze() - 1 ? "§7Done after §f" + NumberId.FREEZE.format(inside) + "§7, you freeze at " + NumberId.FREEZE.format(worth.freeze())
					: "§7You freeze before it is done");
		}
		return row(name, value, num, Layouts::parseRoute, Layouts.routeKey(layout), width, index, lines);
	}

	private static List<String> layoutTip(String name, YourNumbers.LayoutNum mine, GameData.Layout layout, YourNumbers yours, ShaftNames names) {
		Num num = mine.efficiency();
		NumberId id = NumberId.EFFICIENCY;
		List<String> lines = new ArrayList<>();
		lines.add("§f" + name);
		lines.add("§7Your Efficiency in this layout, as the HUD");
		lines.add("§7shows it in a shaft");
		lines.add("");
		String value = Layouts.format(num.value());
		String soFar = num.tracked() == null ? null
				: "§8Your " + num.basis() + (num.basis() == 1 ? " shaft" : " shafts") + " here: " + Layouts.format(num.tracked());
		switch (num.source()) {
			case SET -> {
				lines.add("§eSet by you §f" + value);
				if (soFar != null) lines.add(soFar);
				lines.add("§8Right-click to undo");
			}
			case TRACKED -> {
				lines.add("§aTracked §f" + value + " §8" + basis(num, false));
				lines.add("§8Your newest " + Layouts.LAST_SHAFTS + " shafts here, whatever the range");
			}
			default -> {
				lines.add("§9Calculated §f" + value);
				boolean typed = yours.num(id).source() == Source.SET;
				lines.add("§8" + (typed ? "The Efficiency you set" : "Your Efficiency over all layouts") + ", at this gem's ticks");
				if (typed) lines.add("§8Undo the Efficiency you set to use tracked ones");
				else lines.add("§8Tracked once you mine a shaft here");
				if (soFar != null) lines.add(soFar);
			}
		}
		if (Layouts.crystalGroup(layout)) {
			List<String> gems = GameData.defaults().layouts().stream().filter(l -> l.group().equals(layout.group())).map(l -> names.name(l.gem())).toList();
			lines.add("§8One map: " + String.join(", ", gems));
			return lines;
		}
		String gem = names.name(layout.gem());
		lines.add(mine.gemShafts() >= Layouts.MIN_GEM_SHAFTS ? "§8" + mine.seen() + " of your " + mine.gemShafts() + " " + gem + " shafts"
				: "§8Counted as " + Format.percent(mine.weight()) + " of " + gem + " shafts until you have " + Layouts.MIN_GEM_SHAFTS);
		return lines;
	}

	private FlowLayout numberRow(NumberId id, YourNumbers yours, int width, int index) {
		Num num = yours.num(id);
		List<String> tip = rowTip(id, num, yours.fiesta());
		String name = id.displayName();
		// the spawn gem tile named it, now this does (review 11-27)
		if (id == NumberId.SPAWN_GEM) {
			ShaftNames names = ShaftNames.defaults();
			String gem = yours.spawnGem();
			if (gem != null) name += ", §" + colour(names, gem) + names.name(gem);
			// after the description
			tip.add(tip.indexOf(""), gem == null ? "§8No spawn gem mined in this range"
					: "§7" + id.format(num.value()) + " of your shafts are §" + colour(names, gem) + names.name(gem));
		}
		return row(name, id.format(num.value()), num, id.typeable() ? id : null, Fiesta.typedKey(id, yours.fiesta().now()), width, index, tip);
	}

	// parseAs null = not typeable, plain text
	private FlowLayout row(String name, String text, Num num, NumberId parseAs, String key, int width, int index, List<String> tip) {
		return row(name, text, num, parseAs == null ? null : (java.util.function.Function<String, Double>) parseAs::parse, key, width, index, tip);
	}

	private FlowLayout row(String name, String text, Num num, java.util.function.Function<String, Double> parseAs, String key, int width, int index,
			List<String> tip) {
		FlowLayout row = new HoverFlow(Sizing.fill(100), Sizing.fixed(ROW), HoverFlow.ROW);
		row.verticalAlignment(VerticalAlignment.CENTER);
		row.gap(4);
		row.padding(Insets.horizontal(2));
		if (index % 2 == 1) row.surface(Surface.flat(0x08FFFFFF));
		LabelComponent title = label(clip(name, width - BOX - ICON - 14));
		title.horizontalSizing(Sizing.expand());
		row.child(title);
		if (parseAs != null) {
			TextBoxComponent box = UIComponents.textBox(Sizing.fixed(BOX));
			box.verticalSizing(Sizing.fixed(ROW - 1));
			box.setMaxLength(12);
			box.text(text);
			// argb in 26.2, w/o alpha its invisible. trust me
			box.setTextColor(num.source() == Source.SET ? 0xFFFFFF55 : 0xFFFFFFFF);
			fields.add(new Field(parseAs, key, box, box.getValue()));
			row.child(box);
			row.mouseDown().subscribe((event, doubleClick) -> {
				if (event.button() != 1 || num.source() != Source.SET) return false;
				reset(key);
				return true;
			});
		} else {
			LabelComponent value = label("§7" + text);
			value.horizontalSizing(Sizing.fixed(BOX));
			row.child(value);
		}
		row.child(icon(num.source()));
		tip(row, tip);
		return row;
	}

	// one line if all 3 share the older shafts
	private static List<String> stateLines(NumberId id, YourNumbers.PerState fiesta) {
		List<String> lines = new ArrayList<>();
		Map<Fiesta.State, YourNumbers.StateNum> states = fiesta.of(id);
		if (states.values().stream().allMatch(YourNumbers.StateNum::pool)) {
			Num num = states.get(Fiesta.State.NONE).num();
			lines.add("§fEvery Fiesta state §f" + id.format(num.value()) + " §8tracked " + basis(num, true));
			lines.add("§8Older shafts don't say if the Fiesta was on");
			return lines;
		}
		for (Fiesta.State state : Fiesta.State.values()) {
			YourNumbers.StateNum stateNum = states.get(state);
			Num num = stateNum.num();
			String colour = state == fiesta.now() ? "§f" : "§7";
			String how = switch (num.source()) {
				case SET -> "set by you";
				case TRACKED -> "tracked " + basis(num, stateNum.pool());
				case START -> "starting value";
				case CALCULATED -> "scaled from " + stateNum.from().displayName();
			};
			lines.add(colour + state.displayName() + " " + colour + id.format(num.value()) + " §8" + how);
		}
		return lines;
	}

	// when scaled turns into ur own
	private static String ownOnce(NumberId id, Fiesta.State state) {
		String with = switch (state) {
			case NONE -> "without the Fiesta";
			case COLE -> "with the Fiesta on";
			case FLASK -> "with the Fiesta and a flask";
		};
		return (id == NumberId.COINS_PER_SPAWN ? "Your own after " + Learner.MIN_SPAWNS + " spawns " : "Your own once you mine a shaft ") + with;
	}

	private List<String> rowTip(NumberId id, Num num, YourNumbers.PerState fiesta) {
		List<String> lines = new ArrayList<>();
		lines.add("§f" + id.displayName());
		lines.addAll(wrap("§7", id.description()));
		// was the campfire tile's
		if (id == NumberId.CAMPFIRE) lines.add("§7Your Cold builds faster later on");
		lines.add("");
		String value = id.format(num.value());
		if (fiesta.of(id) != null && fiesta.now() != null) {
			lines.addAll(stateLines(id, fiesta));
			if (num.source() == Source.CALCULATED) lines.add("§8" + ownOnce(id, fiesta.now()));
			if (num.source() == Source.SET) {
				if (num.tracked() != null) lines.add("§aTracked §f" + id.format(num.tracked()) + " §8" + basis(num, false));
				lines.add("§8Right-click to undo");
			}
			return lines;
		}
		if (fiesta.of(id) != null) lines.add("§8Fiesta unknown, all your shafts together");
		if (id == NumberId.EFFICIENCY && num.source() != Source.START) {
			lines.add("§8Over every layout. Each layout has its own,");
			lines.add("§8under Per layout");
		}
		switch (num.source()) {
			case SET -> {
				lines.add("§eSet by you §f" + value);
				if (num.tracked() != null) lines.add("§aTracked §f" + id.format(num.tracked()) + " §8" + basis(num, false));
				lines.add("§8Right-click to undo");
			}
			case TRACKED -> {
				lines.add("§aTracked §f" + value + " §8" + basis(num, false));
				if (ALL_TIME.contains(id)) lines.add("§8From every shaft you have, whatever the range");
				if (Learner.fromMinedShafts(id) && minedShafts() > 0) lines.add("§8Your newest mined shafts, whatever the range");
			}
			case START -> {
				lines.add("§7Starting value §f" + value);
				lines.add("§8" + startHint(id));
			}
			case CALCULATED -> {
				lines.add("§9Calculated §f" + value);
				lines.add("§8" + switch (id) {
					case CAMPFIRE -> "From your freeze time";
					case KEY_LOOT -> Surveyor.SETTINGS.get().calculator().keyLoot() == KeyLoot.PRISTINE_ONLY ? "Pristine only (Settings, Journal)"
							: "The wiki's loot table at your prices";
					default -> "From your ticks on the gems you mined";
				});
				if (id == NumberId.KEY_LOOT && num.tracked() != null) {
					lines.add("§aYour own §f" + id.format(num.tracked()) + " §8" + basis(num, false));
				}
			}
		}
		return lines;
	}

	private static String startHint(NumberId id) {
		return switch (id) {
			case LOST_TICKS -> "Tracked once a shaft has " + Learner.MIN_BOOST_BLOCKS + " blocks in boost";
			case CLOSE_CALL, KEY_CLOSE_CALL -> "Not tracked, type your own";
			default -> "Tracked once you have enough shafts";
		};
	}

	// older = saved before fiesta was
	private static String basis(Num num, boolean older) {
		if (num.basis() == 0) return "";
		// "1 shaft"
		String unit = num.basis() == 1 && num.basisText().endsWith("s") ? num.basisText().substring(0, num.basisText().length() - 1) : num.basisText();
		return "from " + num.basis() + (older ? " older " : " ") + unit;
	}

	private static String sourceLine(Num num) {
		return switch (num.source()) {
			case SET -> "Set by you";
			case TRACKED -> ("Tracked " + basis(num, false)).strip();
			case START -> "Starting value, nothing tracked yet";
			case CALCULATED -> "Calculated";
		};
	}

	private static String sourceName(Source source) {
		return LOOKS.get(source).name();
	}

	// drawn, pack fonts can lose a glyph
	private record Look(String name, String colour, String hint, String[] shape, int rgb) {
	}

	private static final Map<Source, Look> LOOKS = new EnumMap<>(Map.of(
			Source.TRACKED, new Look("Tracked", "§a", "From your own shafts", new String[] {".###.", "#####", "#####", "#####", ".###."}, 0xFF55FF55),
			Source.SET, new Look("Set by you", "§e", "You typed it, right-click it to undo", new String[] {"..#..", ".###.", "#####", ".###.", "..#.."},
					0xFFFFFF55),
			Source.START, new Look("Starting value", "§7", "Used until enough is tracked", new String[] {".###.", "#...#", "#...#", "#...#", ".###."},
					0xFFAAAAAA),
			Source.CALCULATED, new Look("Calculated", "§9", "Worked out from your other numbers",
					new String[] {"#####", ".....", "#####", ".....", "#####"}, 0xFF5599FF)));

	static UIComponent icon(Source source) {
		String[] shape = LOOKS.get(source).shape();
		int rgb = LOOKS.get(source).rgb();
		FlowLayout icon = UIContainers.horizontalFlow(Sizing.fixed(ICON), Sizing.fixed(ICON));
		icon.surface((graphics, c) -> {
			for (int y = 0; y < shape.length; y++) {
				for (int x = 0; x < shape[y].length(); x++) {
					if (shape[y].charAt(x) == '#') graphics.fill(c.x() + x, c.y() + y, c.x() + x + 1, c.y() + y + 1, rgb);
				}
			}
		});
		return icon;
	}

	private void reset(String key) {
		// else saveTyped puts unsaved text back
		fields.clear();
		Surveyor.SETTINGS.change(s -> s.withCalculator(s.calculator().withTyped(key, null)));
		screen.rebuild(true);
	}

	// bad text = dropped
	void saveTyped() {
		for (Field field : fields) {
			String text = field.box.getValue();
			Double value = text.equals(field.shown) ? null : field.parse.apply(text);
			if (value != null) Surveyor.SETTINGS.change(s -> s.withCalculator(s.calculator().withTyped(field.key, value)));
		}
		fields.clear();
	}

	// caxton scrolls 1st letter out at 0 width. once per build, boxes are laid out by then
	void rehome() {
		if (!needsRehome) return;
		needsRehome = false;
		for (Field field : fields) {
			if (!field.box.isFocused()) ScreenParts.rehome(field.box);
		}
	}

	// caxton cancels onValueChange, poll
	void poll() {
		for (Field field : fields) {
			boolean focused = field.box.isFocused();
			if (field.wasFocused && !focused) {
				// clicked from one box to the next: the new one gets focus back after the rebuild
				refocus = fields.stream().filter(f -> f.box.isFocused()).map(f -> f.key).findFirst().orElse(null);
				screen.rebuild(true);
				return;
			}
			field.wasFocused = focused;
		}
	}

	// after the build is on screen, focus needs the root
	void refocus(FocusHandler focus) {
		String key = refocus;
		refocus = null;
		if (key == null || focus == null) return;
		for (Field field : fields) {
			if (!field.key.equals(key)) continue;
			// owo only sets an EditBox focused on keyboard cycle
			focus.focus(field.box, UIComponent.FocusSource.KEYBOARD_CYCLE);
			field.wasFocused = true;
			return;
		}
	}

	// enter + esc save and let go. true = handled
	boolean enter() {
		for (Field field : fields) {
			if (field.box.isFocused()) {
				screen.rebuild(true);
				return true;
			}
		}
		return false;
	}

	private static void tip(UIComponent component, List<String> lines) {
		applyTooltip(component, lines.stream().map(line -> (Component) Component.literal(line)).toList());
	}

	private static String clip(String text, int max) {
		return ScreenParts.clip(font(), text, Math.max(10, max));
	}

	private static Font font() {
		return Minecraft.getInstance().font;
	}

	// tooltips dont wrap
	static List<String> wrap(String color, String text) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String next = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && font().width(next) > TIP_WIDTH) {
				lines.add(color + line);
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(next);
			}
		}
		if (!line.isEmpty()) lines.add(color + line);
		return lines;
	}
}
