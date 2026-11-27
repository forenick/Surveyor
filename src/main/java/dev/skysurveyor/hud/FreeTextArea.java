package dev.skysurveyor.hud;

import io.wispforest.owo.ui.component.TextAreaComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Whence;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

final class FreeTextArea extends TextAreaComponent {
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

	// vanilla eats the wheel even w/ nothing to scroll
	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		return maxScrollAmount() > 0 && super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
