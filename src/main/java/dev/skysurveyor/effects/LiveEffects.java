package dev.skysurveyor.effects;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.mixin.PlayerTabOverlayAccessor;
import dev.skysurveyor.util.GameText;
import dev.skysurveyor.util.Guard;
import dev.skysurveyor.util.SafeWrite;
import dev.skysurveyor.util.TextUtil;

// reads the held item, chat, tab footer + screens the player opened. client thread
public final class LiveEffects {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final String FILE = "effects.json";
	private static final Gson GSON = new Gson();
	// count drop comes a server trip after the click. display choice
	private static final long USE_WINDOW_MILLIS = 2_000;
	// a crash loses at most this much countdown
	private static final long SAVE_EVERY_MILLIS = 30_000;

	private final EffectText text = new EffectText(TextPatterns.defaults());
	private final EffectTimers timers = new EffectTimers(text);
	private Path file;
	private Consumer<Runnable> io;
	private boolean loaded;
	private long lastUse = Long.MIN_VALUE / 2;
	private int tick;
	private boolean onSkyblock;
	private ItemStack lastStack;
	private int lastCount;
	private int lastSlot = -1;
	private int savedVersion;
	private long savedAt;

	public void start(Path folder, Consumer<Runnable> io) {
		this.file = folder.resolve(FILE);
		this.io = io;
		io.accept(() -> {
			Map<String, Long> read = read(file);
			// null = broken file still there, never save over it
			if (read == null) return;
			Minecraft.getInstance().execute(() -> Guard.run("effects load", () -> {
				timers.restore(read);
				loaded = true;
			}));
		});
	}

	// missing = none, unreadable = moved aside + none, null = couldnt move it
	private static Map<String, Long> read(Path file) {
		if (!Files.isRegularFile(file)) return Map.of();
		try {
			JsonObject json = GSON.fromJson(Files.readString(file), JsonObject.class);
			Map<String, Long> left = json == null || !json.has("left") ? null
					: GSON.fromJson(json.get("left"), new TypeToken<LinkedHashMap<String, Long>>() {
					}.getType());
			return left == null ? Map.of() : left;
		} catch (IOException | JsonParseException | IllegalStateException e) {
			LOGGER.warn("Could not read {}, starting w/o consumable timers: {}", file, e.toString());
			try {
				Files.move(file, file.resolveSibling(FILE + "." + System.currentTimeMillis() + ".bad"));
			} catch (IOException moveFailed) {
				LOGGER.warn("Could not move {} aside, not saving consumable timers: {}", file, moveFailed.toString());
				return null;
			}
			return Map.of();
		}
	}

	public Map<Consumable, Long> left() {
		return timers.all();
	}

	// right-click, PASS always
	public void onUse() {
		lastUse = System.currentTimeMillis();
	}

	public void onChat(String legacy) {
		timers.onChat(System.currentTimeMillis(), legacy);
	}

	public void onTick(Minecraft client) {
		long now = System.currentTimeMillis();
		tick++;
		if (client.level == null) onSkyblock = false;
		else if (tick % 10 == 0) {
			List<String> board = GameText.scoreboardLines(client.level.getScoreboard());
			onSkyblock = !board.isEmpty() && text.skyblock(board.getFirst());
		}
		timers.tick(now, onSkyblock);
		LocalPlayer player = client.player;
		if (player != null) {
			if (client.options.keyUse.isDown()) lastUse = now;
			// a click in a menu isnt eating
			held(player.getInventory(), now, client.gui.screen() == null);
		}
		if (tick % 10 == 0 && client.level != null) {
			Component footer = ((PlayerTabOverlayAccessor) client.gui.hud.getTabList()).skysurveyor$getFooter();
			if (footer != null) timers.onFooter(now, text.footer(TextUtil.toLegacy(footer)));
			screen(client);
		}
		if (loaded && (timers.version() != savedVersion || !timers.isEmpty() && onSkyblock && now - savedAt >= SAVE_EVERY_MILLIS)) {
			save(now);
		}
	}

	private void held(Inventory inventory, long now, boolean noMenu) {
		int slot = inventory.getSelectedSlot();
		ItemStack stack = inventory.getSelectedItem();
		int count = stack.isEmpty() ? 0 : stack.getCount();
		if (stack == lastStack && count == lastCount && slot == lastSlot) return;
		lastStack = stack;
		lastCount = count;
		lastSlot = slot;
		String name = stack.isEmpty() ? null : TextUtil.toLegacy(stack.getHoverName());
		timers.onHeld(now, slot, name, count, () -> lore(stack), noMenu && now - lastUse <= USE_WINDOW_MILLIS);
	}

	private static List<String> lore(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) return List.of();
		List<String> lines = new ArrayList<>();
		lore.lines().forEach(line -> lines.add(TextUtil.toLegacy(line)));
		return lines;
	}

	// container screens only, not ur own inventory
	private void screen(Minecraft client) {
		if (!(client.gui.screen() instanceof AbstractContainerScreen<?> shown) || shown instanceof InventoryScreen
				|| shown instanceof CreativeModeInventoryScreen) {
			return;
		}
		String title = TextUtil.toLegacy(shown.getTitle());
		if (!text.isScreen(title)) return;
		List<EffectText.Item> items = new ArrayList<>();
		for (Slot slot : shown.getMenu().slots) {
			if (slot.container instanceof Inventory || slot.getItem().isEmpty()) continue;
			items.add(new EffectText.Item(TextUtil.toLegacy(slot.getItem().getHoverName()), lore(slot.getItem())));
		}
		timers.onScreen(text.screen(title, items));
	}

	private void save(long now) {
		savedVersion = timers.version();
		savedAt = now;
		if (file == null) return;
		JsonObject json = new JsonObject();
		json.add("left", GSON.toJsonTree(timers.saved()));
		String out = GSON.toJson(json);
		Path target = file;
		io.accept(() -> {
			try {
				SafeWrite.write(target, out);
			} catch (IOException e) {
				LOGGER.warn("Could not save {}: {}", target, e.toString());
			}
		});
	}

	// before the io thread closes
	public void close() {
		if (loaded) save(System.currentTimeMillis());
	}
}
