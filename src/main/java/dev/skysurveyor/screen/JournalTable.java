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
import dev.skysurveyor.screen.TableColumns.Column;
import dev.skysurveyor.settings.SettingsList;

import static dev.skysurveyor.screen.ScreenParts.label;

// the journals tables: header, cells, column widths, shaft sort. which columns fit is TableColumns
final class JournalTable {
	// display choices
	static final int ROW_HEIGHT = 12;
	static final int COLUMN_GAP = 6;
	static final int SPAWN_BAR = 26;
	private static final Color GREEN = Color.ofArgb(0xCC55FF55);
	// session table order, null = played order. kept while the game runs
	private static String shaftSort;
	private static boolean shaftSortFlipped;

	private final Font font;
	private final Runnable rebuild;

	// rebuild = after a sort click
	JournalTable(Font font, Runnable rebuild) {
		this.font = font;
		this.rebuild = rebuild;
	}

	static List<Column> fit(List<Column> columns, int available) {
		return TableColumns.fit(columns, available, COLUMN_GAP);
	}

	// measured bc pack fonts (caxton, bold) are wider, never below the width in the list
	List<Column> measured(List<Column> columns, String key, List<String> texts) {
		return measured(columns, key, widest(texts));
	}

	// widest = cells measured by the caller, f.e. text + bar
	List<Column> measured(List<Column> columns, String key, int widest) {
		return columns.stream().map(c -> c.key().equals(key) ? TableColumns.measure(c, Math.max(widest, font.width(c.title()))) : c).toList();
	}

	// spawn cells: text + the bar, which keeps its own room
	int barCellWidth(List<String> texts) {
		return widest(texts) + 3 + SPAWN_BAR;
	}

	int widest(List<String> texts) {
		return texts.stream().mapToInt(font::width).max().orElse(0);
	}

	// display row -> entry index, by the sort if its column shows
	static int[] shaftOrder(List<JournalEntry> shafts, List<Double> coins, List<Column> columns) {
		return TableColumns.shaftOrder(shafts, coins, TableColumns.shownSort(shaftSort, columns), shaftSortFlipped,
				Surveyor.MONEY.settings().metalTierUsed().tier());
	}

	// sortable false = headers not clickable. rows scroll = the callers, so it can keep it
	FlowLayout panel(List<Column> columns, KeptScroll rows, boolean sortable) {
		FlowLayout table = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
		table.surface(ScreenParts.PANEL);
		table.padding(Insets.of(5));
		FlowLayout header = tableRow();
		for (Column column : columns) {
			List<Component> tips = headerTips(column);
			if (sortable) {
				header.child(sortHeader(column, tips));
				continue;
			}
			LabelComponent title = cell(column, "§7" + column.title());
			if (tips == null) {
				header.child(title);
				continue;
			}
			// row high + both, so the whole cell shows it
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

	// what a column counts, null = says it already
	private static List<Component> headerTips(Column column) {
		List<String> lines = switch (column.title()) {
			case "Corpses" -> List.of("Looted / in the shaft", "L Lapis, U Umber, T Tungsten, V Vanguard");
			case "Lapis" -> List.of("Lapis corpses looted");
			case "Mined" -> List.of("Gems in Fine, metal as " + SettingsList.label(Surveyor.MONEY.settings().metalTierUsed()));
			case "Spawn" -> List.of("Mining time until the shaft spawned");
			default -> null;
		};
		if (lines == null) return null;
		List<Component> tips = new ArrayList<>();
		tips.add(Component.literal("§f" + column.title()));
		lines.forEach(line -> tips.add(Component.literal("§7" + line)));
		return tips;
	}

	// active column: white + arrow, if the arrow fits
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
		// both, so the whole cell shows it
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

	// 1st click best/biggest first, 2nd flips, 3rd back to played order
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

	// text + a bar, slowest = full bar
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
