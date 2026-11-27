package dev.skysurveyor.hud;

import dev.skysurveyor.hud.HudValue.Kind;

public final class HudColors {
	// nicks pick (2026-11-12)
	static final int COLD_YELLOW = -50;
	// nicks pick (2026-11-12)
	static final int COLD_RED = -80;

	private HudColors() {
	}

	// same kind only, old rule limits
	public static boolean comparable(HudValue a, HudValue b) {
		if (!a.hasNumber() || !b.hasNumber()) return false;
		return family(a.kind()) == family(b.kind());
	}

	private static Kind family(Kind kind) {
		return kind == Kind.DECIMAL ? Kind.INTEGER : kind;
	}
}
