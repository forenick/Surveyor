package dev.skysurveyor.screen;

import java.util.function.Consumer;

import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.UIComponent;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.Surveyor;

import static dev.skysurveyor.screen.ScreenParts.label;

// quick mark hotkey. only writes a line to the recording, nothing sent
public final class QuickMarkScreen extends BaseOwoScreen<FlowLayout> implements ModScreen {
	private static final int WIDTH = 220;
	private static final int BUTTON = 16;

	private final long pressedAt;
	private FlowLayout root;
	private TextBoxComponent note;
	private boolean focusNote = true;

	public QuickMarkScreen(long pressedAt) {
		super(Component.literal("Mark"));
		this.pressedAt = pressedAt;
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
		root.horizontalAlignment(HorizontalAlignment.CENTER);
		root.verticalAlignment(VerticalAlignment.CENTER);
		FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(Math.min(WIDTH, width - 16)), Sizing.content());
		panel.surface(ScreenParts.PANEL);
		panel.padding(Insets.of(7));
		panel.gap(5);
		panel.child(label("§fMark this moment"));
		if (!Surveyor.RECORDER.isRecording()) {
			panel.child(ScreenParts.wrapped("§7Not recording. Start with /sv record start or turn on Auto-record in Settings."));
			panel.child(button("Close", b -> onClose()));
			root.child(panel);
			return;
		}
		note = UIComponents.textBox(Sizing.fill(100));
		note.verticalSizing(Sizing.fixed(BUTTON));
		note.setMaxLength(200);
		note.setHint(Component.literal("What happened? (optional)").setStyle(EditBox.SEARCH_HINT_STYLE));
		panel.child(note);
		FlowLayout buttons = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
		buttons.gap(4);
		buttons.child(button("Bug", b -> mark("bug")));
		buttons.child(button("Wrong number", b -> mark("wrong number")));
		buttons.child(button("Note", b -> mark("note")));
		panel.child(buttons);
		panel.child(label("§8Enter = Note, Esc = cancel"));
		root.child(panel);
	}

	private ButtonComponent button(String text, Consumer<ButtonComponent> onPress) {
		ButtonComponent button = PackButton.button(Component.literal(text), onPress);
		button.horizontalSizing(Sizing.fixed(font.width(text) + 16));
		button.verticalSizing(Sizing.fixed(BUTTON));
		return button;
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
		if (focusNote && note != null) {
			focusNote = false;
			root.focusHandler().focus(note, UIComponent.FocusSource.MOUSE_CLICK);
		}
		super.extractRenderState(graphics, mouseX, mouseY, delta);
	}

	@Override
	public boolean keyPressed(KeyEvent key) {
		if (key.isConfirmation() && note != null) {
			mark("note");
			return true;
		}
		return super.keyPressed(key);
	}

	// caxton cancels onValueChange, read getValue on submit
	private void mark(String category) {
		String text = note == null ? "" : note.getValue().strip();
		boolean written = Surveyor.RECORDER.quickMark(category, text, pressedAt);
		onClose();
		if (written && minecraft != null) {
			minecraft.gui.hud.getChat().addClientSystemMessage(Component.literal("§7[Surveyor] Marked: " + category));
		}
	}
}
