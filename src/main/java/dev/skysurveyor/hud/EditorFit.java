package dev.skysurveyor.hud;

final class EditorFit {
	private EditorFit() {
	}

	// widest text, never under the vanilla font's old fixed width
	static int widest(int min, int[] widths) {
		int widest = min;
		for (int w : widths) widest = Math.max(widest, w);
		return widest;
	}

	// long word only if the rest of the row keeps what the short one leaves it in the smallest panel
	static boolean sameAsBoxFits(int inner, int smallestInner, int longButton, int shortButton) {
		return inner - longButton >= smallestInner - shortButton;
	}

	static int clampScroll(int scroll, int content, int visible) {
		return Math.max(0, Math.min(scroll, content - visible));
	}
}
