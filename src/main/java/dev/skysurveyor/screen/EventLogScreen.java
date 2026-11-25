package dev.skysurveyor.screen;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.debug.EventLog;

import static dev.skysurveyor.screen.ScreenParts.label;

// /sv debug log: last events, unmatched chat + errors, newest on top. copy = clipboard on click only
public final class EventLogScreen extends BaseOwoScreen<FlowLayout> implements ModScreen {
	private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
	private static final int PADDING = 8;
	private static final int BUTTON = 16;

	private final EventLog log;
	private LabelComponent copied;

	public EventLogScreen(EventLog log) {
		super(Component.literal("Surveyor Event Log"));
		this.log = log;
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
		root.surface(Surface.VANILLA_TRANSLUCENT);
		root.padding(Insets.of(PADDING));
		root.gap(5);
		List<EventLog.Line> lines = log.lines();
		FlowLayout bar = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		bar.surface(ScreenParts.PANEL);
		bar.padding(Insets.of(4));
		bar.gap(4);
		bar.verticalAlignment(VerticalAlignment.CENTER);
		LabelComponent title = label("§fSurveyor §7Event log §8(" + lines.size() + " of the last " + EventLog.SIZE + ")");
		title.margins(Insets.left(2));
		bar.child(title);
		bar.child(UIContainers.horizontalFlow(Sizing.expand(), Sizing.content()));
		copied = label("");
		bar.child(copied);
		bar.child(button("Copy", b -> {
			minecraft.keyboardHandler.setClipboard(text(lines));
			copied.text(Component.literal("§aCopied"));
		}));
		bar.child(button("Close", b -> onClose()));
		root.child(bar);

		int textWidth = width - 2 * PADDING - 2 * 6 - 4;
		FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
		list.gap(1);
		List<UIComponent> rows = new ArrayList<>();
		for (int i = lines.size() - 1; i >= 0; i--) rows.add(label(ScreenParts.clip(font, line(lines.get(i)), textWidth)));
		if (rows.isEmpty()) rows.add(label("§8Nothing yet. Shaft events, chat Surveyor doesn't know and errors show up here."));
		list.children(rows);
		KeptScroll scroll = new KeptScroll(Sizing.fill(100), Sizing.expand(), list);
		scroll.surface(ScreenParts.PANEL);
		scroll.padding(Insets.of(6));
		root.child(scroll);
	}

	private ButtonComponent button(String text, Consumer<ButtonComponent> onPress) {
		ButtonComponent button = PackButton.button(Component.literal(text), onPress);
		button.horizontalSizing(Sizing.fixed(font.width(text) + 20));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	private static String line(EventLog.Line line) {
		String color = switch (line.kind()) {
			case EVENT -> "§f";
			case UNMATCHED -> "§e";
			case ERROR -> "§c";
		};
		return "§8" + clock(line.at()) + " " + color + line.text();
	}

	private static String text(List<EventLog.Line> lines) {
		StringBuilder text = new StringBuilder();
		for (EventLog.Line line : lines) {
			text.append(clock(line.at())).append(' ').append(line.kind().name().toLowerCase(Locale.ROOT)).append(' ')
					.append(ScreenParts.strip(line.text())).append('\n');
		}
		return text.toString();
	}

	private static String clock(long at) {
		return LocalTime.ofInstant(Instant.ofEpochMilli(at), ZoneId.systemDefault()).format(CLOCK);
	}
}
