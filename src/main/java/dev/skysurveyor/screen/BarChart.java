package dev.skysurveyor.screen;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;

import io.wispforest.owo.ui.component.BoxComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.settings.JournalSettings.ChartType;

import static dev.skysurveyor.screen.ScreenParts.label;

// stats tab: one bar per session, oldest left. the maths is ChartAxis
final class BarChart {
	record Bar(SessionSummary session, double value, int argb, List<Component> tooltip) {
	}

	// what a chart shows besides its bars. average null = no line
	record Look(ChartType type, int lineArgb, double[] steps, DoubleFunction<String> axisText, Double average, int averageArgb,
			String legend, List<String> averageTip, String empty) {
	}

	// display choices
	static final int AXIS_GAP = 3;
	private static final int MIN_PLOT = 60;
	private static final int PLOT_TOP = 4;
	private static final int BELOW_BASE = 5;
	private static final int DATES = 10;

	private BarChart() {
	}

	// room = the columns inner width, height = the screens. click on a bar opens its session
	static FlowLayout of(Font font, List<Bar> all, Look look, int room, int screenHeight, Consumer<SessionSummary> open) {
		FlowLayout wrapper = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		if (all.isEmpty()) {
			wrapper.child(label(look.empty()));
			return wrapper;
		}
		// display choice
		int chartHeight = Math.clamp(screenHeight / 3, 50, 110);
		ChartAxis.Scale scale = ChartAxis.scale(all.stream().mapToDouble(Bar::value).max().orElse(0),
				all.stream().mapToDouble(Bar::value).min().orElse(0), look.steps());
		List<Double> ticks = scale.ticks();
		int axisWidth = 0;
		for (double value : ticks) axisWidth = Math.max(axisWidth, font.width(look.axisText().apply(value)));
		axisWidth += 2;

		int plotWidth = Math.max(MIN_PLOT, room - axisWidth - AXIS_GAP);
		ChartAxis.Bars bars = ChartAxis.bars(plotWidth, all.size());
		int barWidth = bars.width();
		int barGap = bars.gap();
		List<Bar> shown = all.size() > bars.max() ? all.subList(all.size() - bars.max(), all.size()) : all;

		int base = chartHeight - BELOW_BASE;
		int plotHeight = base - PLOT_TOP;
		FlowLayout chart = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fixed(chartHeight));
		chart.gap(AXIS_GAP);
		StackLayout axis = UIContainers.stack(Sizing.fixed(axisWidth), Sizing.fixed(chartHeight));
		StackLayout plot = UIContainers.stack(Sizing.expand(), Sizing.fixed(chartHeight));
		for (double value : ticks) {
			int lineY = scale.y(value, base, plotHeight);
			LabelComponent tick = label("§8" + look.axisText().apply(value));
			tick.horizontalSizing(Sizing.fixed(axisWidth));
			tick.horizontalTextAlignment(HorizontalAlignment.RIGHT);
			tick.positioning(Positioning.absolute(0, lineY - 4));
			axis.child(tick);
			plot.child(line(lineY, scale.isZero(value) ? 0x40FFFFFF : 0x18FFFFFF));
		}
		int zero = scale.y(0, base, plotHeight);
		if (look.type() == ChartType.LINE) {
			int[] xs = new int[shown.size()];
			int[] ys = new int[shown.size()];
			int[] dots = new int[shown.size()];
			for (int i = 0; i < shown.size(); i++) {
				xs[i] = bars.x(i) + barWidth / 2;
				ys[i] = scale.y(shown.get(i).value(), base, plotHeight);
				dots[i] = 0xFF000000 | shown.get(i).argb();
			}
			LinePlot line = new LinePlot(xs, ys, dots, look.lineArgb());
			line.positioning(Positioning.absolute(0, 0));
			plot.child(line);
		}
		for (int i = 0; i < shown.size(); i++) {
			Bar bar = shown.get(i);
			int end = scale.y(bar.value(), base, plotHeight);
			int barHeight = Math.max(1, Math.abs(zero - end));
			BoxComponent box;
			if (look.type() == ChartType.LINE) {
				// invisible column, easy to hover
				box = new HoverFlow.Box(Sizing.fixed(barWidth + barGap), Sizing.fixed(base - PLOT_TOP + 1), HoverFlow.ROW);
				box.color(Color.ofArgb(0));
				box.positioning(Positioning.absolute(bars.x(i) - barGap / 2, PLOT_TOP));
			} else {
				box = new HoverFlow.Box(Sizing.fixed(barWidth), Sizing.fixed(barHeight), HoverFlow.SMALL);
				box.color(Color.ofArgb(bar.argb()));
				box.positioning(Positioning.absolute(bars.x(i), bar.value() < 0 ? zero + 1 : zero - barHeight));
			}
			box.fill(true);
			box.tooltip(bar.tooltip());
			box.mouseDown().subscribe((event, doubleClick) -> {
				open.accept(bar.session());
				return true;
			});
			plot.child(box);
		}
		List<ClientTooltipComponent> averageTips = TooltipLines.of(look.averageTip());
		Double average = look.average();
		if (average != null) {
			int averageY = scale.y(Math.clamp(average, scale.bottom(), scale.top()), base, plotHeight);
			plot.child(line(averageY, look.averageArgb()));
			if (!averageTips.isEmpty()) {
				// 1 px line is too thin to hover
				BoxComponent band = UIComponents.box(Sizing.fill(100), Sizing.fixed(5));
				band.color(Color.ofArgb(0));
				band.positioning(Positioning.absolute(0, averageY - 2));
				band.tooltip(averageTips);
				plot.child(band);
			}
		}
		chart.child(axis);
		chart.child(plot);
		wrapper.child(chart);

		StackLayout dates = UIContainers.stack(Sizing.fill(100), Sizing.fixed(DATES));
		int left = axisWidth + AXIS_GAP + ChartAxis.LEFT;
		String firstText = "§8" + JournalScreen.day(shown.getFirst().session().start());
		LabelComponent first = label(firstText);
		first.positioning(Positioning.absolute(left, 1));
		dates.child(first);
		if (shown.size() > 1) {
			String lastText = "§8" + JournalScreen.day(shown.getLast().session().start());
			int lastX = left + (shown.size() - 1) * (barWidth + barGap) + barWidth - font.width(lastText);
			// display choice: room between the two dates
			if (lastX > left + font.width(firstText) + 6) {
				LabelComponent last = label(lastText);
				last.positioning(Positioning.absolute(lastX, 1));
				dates.child(last);
			}
		}
		wrapper.child(dates);
		if (average != null) {
			LabelComponent legendLabel = label(look.legend());
			if (!averageTips.isEmpty()) legendLabel.tooltip(averageTips);
			wrapper.child(legendLabel);
		}
		return wrapper;
	}

	private static BoxComponent line(int y, int argb) {
		BoxComponent line = UIComponents.box(Sizing.fill(100), Sizing.fixed(1));
		line.fill(true);
		line.color(Color.ofArgb(argb));
		line.positioning(Positioning.absolute(0, y));
		return line;
	}
}
