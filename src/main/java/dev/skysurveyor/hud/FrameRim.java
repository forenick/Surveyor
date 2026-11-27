package dev.skysurveyor.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.gui.GuiMetadataSection;
import net.minecraft.client.resources.metadata.gui.GuiSpriteScaling;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;

// packs retexture, read the loaded sprite
final class FrameRim {
	record Rim(int left, int top, int right, int bottom) {
		int width() {
			return left + right;
		}

		int height() {
			return top + bottom;
		}
	}

	static final Rim NONE = new Rim(0, 0, 0, 0);
	// vanilla sprites show 1px past the padding
	static final Rim VANILLA = new Rim(1, 1, 1, 1);
	static final Identifier BACKGROUND = Identifier.withDefaultNamespace("tooltip/background");
	static final Identifier BORDER = Identifier.withDefaultNamespace("tooltip/frame");
	// client thread. new sprites after a pack reload
	private static TextureAtlasSprite seenBackground;
	private static TextureAtlasSprite seenBorder;
	private static Rim seen = VANILLA;

	private FrameRim() {
	}

	// sprite px past the box edge
	private static final int REACH = HudRenderer.FRAME_OVERHANG - HudRenderer.PADDING;

	static Rim get() {
		Minecraft client = Minecraft.getInstance();
		if (client == null) return VANILLA;
		try {
			TextureAtlas atlas = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
			TextureAtlasSprite background = atlas.getSprite(BACKGROUND);
			TextureAtlasSprite border = atlas.getSprite(BORDER);
			if (background != seenBackground || border != seenBorder) {
				seen = larger(measure(background), measure(border));
				seenBackground = background;
				seenBorder = border;
			}
			return seen;
		} catch (RuntimeException e) {
			// atlas mid reload or image freed
			return seen;
		}
	}

	private static Rim larger(Rim a, Rim b) {
		return new Rim(Math.max(a.left, b.left), Math.max(a.top, b.top), Math.max(a.right, b.right), Math.max(a.bottom, b.bottom));
	}

	private static Rim measure(TextureAtlasSprite sprite) {
		SpriteContents contents = sprite.contents();
		GuiSpriteScaling scaling = contents.getAdditionalMetadata(GuiMetadataSection.TYPE).orElse(GuiMetadataSection.DEFAULT).scaling();
		// stretched sprites scale w/ the box, no fixed rim
		if (!(scaling instanceof GuiSpriteScaling.NineSlice slice)) return VANILLA;
		int w = contents.width();
		int h = contents.height();
		int minX = w;
		int minY = h;
		int maxX = -1;
		int maxY = -1;
		for (int frame : contents.getUniqueFrames()) {
			for (int y = 0; y < h; y++) {
				for (int x = 0; x < w; x++) {
					if (contents.isTransparent(frame, x, y)) continue;
					minX = Math.min(minX, x);
					maxX = Math.max(maxX, x);
					minY = Math.min(minY, y);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) return NONE;
		// texture px -> gui px, corners draw 1:1 in slice units
		int left = minX * slice.width() / w;
		int right = slice.width() - 1 - maxX * slice.width() / w;
		int top = minY * slice.height() / h;
		int bottom = slice.height() - 1 - maxY * slice.height() / h;
		return new Rim(rim(left), rim(top), rim(right), rim(bottom));
	}

	private static int rim(int inset) {
		return Math.clamp(REACH - inset, 0, REACH);
	}
}
