package dev.skysurveyor.screen;

import java.util.ArrayList;
import java.util.List;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.mining.Efficiency;
import dev.skysurveyor.screen.TableColumns.Column;
import dev.skysurveyor.settings.SettingsList;

import static dev.skysurveyor.screen.ScreenParts.label;

final class JournalTable {
	// display choices
	static final int ROW_HEIGHT = 12;
	static final int COLUMN_GAP = 6;
	static final int SPAWN_BAR = 26;
	private static final Color GREEN = Color.ofArgb(0xCC55FF55);
	// null = played order, not saved
	private static String shaftSort;
	private static boolean shaftSortFlipped;

	private final Font font;
	private final Runnable rebuild;

	JournalTable(Font font, Runnable rebuild) {
		this.font = font;
		this.rebuild = rebuild;
	}

	static List<Column> fit(List<Column> columns, int available) {
		return TableColumns.fit(columns, available, COLUMN_GAP);
	}

	// pack fonts are wider, measure
	List<Column> measured(List<Column> columns, String key, List<String> texts) {
		return measured(columns, key, widest(texts));
	}

	List<Column> measured(List<Column> columns, String key, int widest) {
		return columns.stream().map(c -> c.key().equals(key) ? TableColumns.measure(c, Math.max(widest, font.width(c.title()))) : c).toList();
	}

	int barCellWidth(List<String> texts) {
		return widest(texts) + 3 + SPAWN_BAR;
	}

	int widest(List<String> texts) {
		return texts.stream().mapToInt(font::width).max().orElse(0);
	}

	// display row -> entry index
	static int[] shaftOrder(List<JournalEntry> shafts, List<Double> coins, List<Column> columns) {
		return TableColumns.shaftOrder(shafts, coins, TableColumns.shownSort(shaftSort, columns), shaftSortFlipped,
				Surveyor.MONEY.settings().metalTierUsed().tier());
	}

	// caller owns rows scroll, so it can keep it
	FlowLayout panel(List<Column> columns, KeptScroll rows, boolean sortable) {
		FlowLayout table = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
		table.surface(ScreenParts.PANEL);
		table.padding(Insets.of(5));
		FlowLayout header = tableRow();
		for (Column column : columns) {
			List<Component> tips = headerTips(column, sortable);
			if (sortable) {
				header.child(sortHeader(column, tips));
				continue;
			}
			LabelComponent title = cell(column, "§7" + column.title());
			if (tips == null) {
				header.child(title);
				continue;
			}
			// both, so whole cell shows it
			FlowLayout cell = UIContainers.horizontalFlow(Sizing.fixed(column.total()), Sizing.fixed(ROW_HEIGHT));
			cell.verticalAlignment(VerticalAlignment.CENTER);
			cell.child(title);
			cell.tooltip(tips);
			title.tooltip(tips);
			header.child(cell);
		}
		table.child(header);
		table.child(JournalScreen.divider());
		rows.margins(Insets.top(2));
		rows.tag("table");
		table.child(rows);
		return table;
	}

	// every column, the footer says hover one
	private static List<Component> headerTips(Column column, boolean shafts) {
		String underRate = "- under " + JournalStats.MIN_RATE_MILLIS / 60_000 + " minutes";
		List<String> lines = switch (column.key()) {
			case "date", "start" -> List.of("When the session started");
			case "length" -> List.of("From its start to your last shaft or mining", "Breaks included");
			case "shafts" -> List.of("Shafts you finished");
			case "spawn" -> shafts ? List.of("Mining time until the shaft spawned", "Bar: against the slowest in this session")
					: List.of("Mining time per shaft you spawned", "Only mining in the Dwarven Mines counts", "Bar: against the slowest listed");
			case "rate" -> List.of("Shafts per hour, breaks left out", underRate);
			case "powder" -> List.of("Glacite Powder from shafts", "Corpse loot plus the bonus for leaving");
			case "lapis" -> List.of("Lapis corpses looted");
			case "coins" -> shafts ? List.of("Net coins from this shaft", "Keys and campfires taken off")
					: List.of("Net coins from shafts and from mining outside them", "Keys and campfires taken off");
			case "coinRate" -> List.of("Coins per hour, breaks left out", underRate);
			case "number" -> List.of("The order you entered them in");
			case "shaft" -> List.of("Type, then its layout: 1, 2 or crystal", "The details show the scoreboard's id");
			case "time" -> List.of("When you entered");
			case "inside" -> List.of("Time from entering to leaving");
			case "corpses" -> List.of("Looted / in the shaft", "L Lapis, U Umber, T Tungsten, V Vanguard");
			case "mined" -> List.of("Gems in Fine, metal as " + SettingsList.label(Surveyor.MONEY.settings().metalTierUsed()));
			case "eff" -> List.of("Blocks you broke against nonstop mining", "From your first gem block, no Gemstone Spread",
					"Under " + Efficiency.MIN_BLOCKS + " blocks shows -");
			case "campfire" -> List.of("Campfires you placed, with your Cold at each");
			default -> null;
		};
		if (lines == null) return null;
		List<Component> tips = new ArrayList<>();
		tips.add(Component.literal("§f" + column.title()));
		lines.forEach(line -> tips.add(Component.literal("§7" + line)));
		if (shafts) tips.add(Component.literal("§8Click to sort"));
		return tips;
	}

	private FlowLayout sortHeader(Column column, List<Component> tips) {
		FlowLayout cell = new HoverFlow(Sizing.fixed(column.total()), Sizing.fixed(ROW_HEIGHT), HoverFlow.ROW);
		cell.verticalAlignment(VerticalAlignment.CENTER);
		String text = "§7" + column.title();
		if (column.key().equals(shaftSort)) {
			String arrow = TableColumns.ascending(column.key(), shaftSortFlipped) ? "▲" : "▼";
			text = "§f" + column.title();
			for (String gap : List.of(" ", "")) {
				String withArrow = column.right() ? "§f" + arrow + gap + column.title() : "§f" + column.title() + gap + arrow;
				if (font.width(withArrow) <= column.total()) {
					text = withArrow;
					break;
				}
			}
		}
		LabelComponent title = cell(column, text);
		cell.child(title);
		// both, so whole cell shows it
		if (tips != null) {
			cell.tooltip(tips);
			title.tooltip(tips);
		}
		cell.mouseDown().subscribe((event, doubleClick) -> {
			sortShafts(column.key());
			return true;
		});
		return cell;
	}

	// 3rd click = played order
	private void sortShafts(String key) {
		if (!key.equals(shaftSort)) {
			shaftSort = key;
			shaftSortFlipped = false;
		} else if (!shaftSortFlipped) {
			shaftSortFlipped = true;
		} else {
			shaftSort = null;
		}
		rebuild.run();
	}

	static LabelComponent cell(Column column, String text) {
		LabelComponent label = label(text);
		label.horizontalSizing(Sizing.fixed(column.total()));
		if (column.right()) label.horizontalTextAlignment(HorizontalAlignment.RIGHT);
		return label;
	}

	// slowest = full bar
	static FlowLayout barCell(Column column, Long millis, long slowest) {
		FlowLayout cell = UIContainers.horizontalFlow(Sizing.fixed(column.total()), Sizing.content());
		cell.verticalAlignment(VerticalAlignment.CENTER);
		cell.gap(3);
		if (millis == null) {
			cell.child(label("§8-"));
			return cell;
		}
		LabelComponent text = label(JournalScreen.spawnCell(millis));
		text.horizontalSizing(Sizing.fixed(column.width() - SPAWN_BAR - 3));
		cell.child(text);
		int barMax = SPAWN_BAR + column.extra();
		BoxComponent bar = UIComponents.box(Sizing.fixed(Math.max(1, (int) (barMax * millis / Math.max(1, slowest)))), Sizing.fixed(3));
		bar.fill(true);
		bar.color(GREEN);
		cell.child(bar);
		return cell;
	}

	static FlowLayout tableRow() {
		FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(ROW_HEIGHT));
		row.padding(Insets.horizontal(2));
		row.gap(COLUMN_GAP);
		row.verticalAlignment(VerticalAlignment.CENTER);
		return row;
	}
}
