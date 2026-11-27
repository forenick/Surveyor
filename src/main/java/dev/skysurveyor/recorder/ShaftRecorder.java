package dev.skysurveyor.recorder;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.Surveyor;
import dev.skysurveyor.debug.ChatWatch;
import dev.skysurveyor.debug.ShaftChecks;
import dev.skysurveyor.detection.EventText;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.ShaftEvent.ShaftLeft;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.mixin.BossHealthOverlayAccessor;
import dev.skysurveyor.mixin.PlayerTabOverlayAccessor;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.Settings;
import dev.skysurveyor.util.GameText;
import dev.skysurveyor.util.Guard;
import dev.skysurveyor.util.TextUtil;

// dev tool. read only, never sends anything
public final class ShaftRecorder {
	static final Logger LOGGER = LoggerFactory.getLogger("Surveyor Recorder");

	private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
	public static final String EXTENSION = ".jsonl.gz";
	private static final double ENTITY_SCAN_RADIUS = 48;
	// keeps player chat out of recordings
	private static final TextPatterns PATTERNS = TextPatterns.defaults();

	// display choice, only on game close
	private static final long CLOSE_WAIT_MILLIS = 5_000;

	private RecordingWriter writer;
	private Long autoSession;
	private Long failedAuto;
	private String lastName;
	private int sameName;
	private final Set<String> marked = new HashSet<>();
	private final NameMask names = NameMask.defaults();
	private boolean anonymize;
	private boolean extra;
	// none = nothing to write at start
	private String lastBossBars = "[]";
	// static bc describeItem is. client thread only
	private static boolean skipProfiles;
	private long lastBlockHit = Long.MIN_VALUE;
	private long startMillis;
	private int tick;

	private ClientLevel lastLevel;
	private List<String> lastScoreboard = List.of();
	private List<String> lastTabList = List.of();
	private String lastTabHeaderFooter = "";
	private String lastHeldItem = "";
	private final Map<Integer, String> knownStands = new HashMap<>();
	private AbstractContainerScreen<?> lastScreen;
	private String lastScreenSignature = "";

	public boolean isRecording() {
		return writer != null;
	}

	public Path start(Minecraft client) {
		return start(client, null, false);
	}

	// session != null = auto, stops w/ that session. extra = boss bar + sounds too
	// file opens on the recorder thread, failure shows up in onTick
	public Path start(Minecraft client, Long session, boolean extra) {
		stop();
		String name = LocalDateTime.now().format(FILE_NAME);
		// 2 starts in 1 sec, so the name in chat is real. writer still skips taken names
		sameName = name.equals(lastName) ? sameName + 1 : 1;
		lastName = name;
		if (sameName > 1) name += "_" + sameName;
		writer = new RecordingWriter(client.gameDirectory.toPath(), name, EXTENSION);
		failedAuto = null;
		startMillis = System.currentTimeMillis();
		autoSession = session;
		this.extra = extra;
		lastBossBars = "[]";
		marked.clear();
		names.reset();
		anonymize = Surveyor.SETTINGS.get().dev().isAnonymize();
		skipProfiles = anonymize;
		lastScreen = null;
		lastScreenSignature = "";
		// only real world changes, live tracker has none here either
		lastLevel = client.level;
		resetSnapshots();
		JsonObject event = event("start");
		event.addProperty("time", LocalDateTime.now().toString());
		// has live + live_entry lines (0.24.0+)
		event.addProperty("live", true);
		// replay skips events added after it (0.25.0-alpha.16+)
		event.addProperty("version", Surveyor.modVersion());
		if (session != null) {
			event.addProperty("auto", true);
			event.addProperty("session", session);
		}
		event.add("settings", LiveSettings.toJson(Surveyor.SETTINGS.get()));
		if (anonymize) event.addProperty("anonymized", true);
		if (extra) event.addProperty("extra", true);
		// replay needs the same patterns
		if (!TextPatterns.overrides().isEmpty()) {
			JsonArray overrides = new JsonArray();
			TextPatterns.overrides().forEach(overrides::add);
			event.add("patternOverrides", overrides);
		}
		write(event);
		return writer.file();
	}

	// auto session whose file failed, once. null = none
	public Long takeFailedAuto() {
		Long failed = failedAuto;
		failedAuto = null;
		return failed;
	}

	// null = manual or not recording
	public Long autoSession() {
		return writer == null ? null : autoSession;
	}

	// 0 = not recording
	public long writtenBytes() {
		return writer == null ? 0 : writer.written();
	}

	public Path file() {
		return writer == null ? null : writer.file();
	}

	// replay numbers depend on settings
	public void onSettings(Settings settings) {
		if (writer == null) return;
		JsonObject event = event("settings");
		event.add("settings", LiveSettings.toJson(settings));
		write(event);
	}

	public Path stop() {
		return stop(false);
	}

	// wait = game closing, else recorder thread finishes on its own
	public Path stop(boolean wait) {
		if (writer == null) {
			return null;
		}
		write(event("stop"));
		Path file = writer.file();
		writer.close();
		if (wait) writer.awaitClosed(CLOSE_WAIT_MILLIS);
		writer = null;
		return file;
	}

	// once per new target (every tick in creative), only new pos written
	public void onBlockHit(BlockPos pos) {
		if (writer == null || pos.asLong() == lastBlockHit) return;
		lastBlockHit = pos.asLong();
		JsonObject event = event("block_hit");
		writePos(event, pos);
		write(event);
	}

	// to check block count vs sacks
	public void onBlockBreak(BlockPos pos, String block, int ticks, boolean clean, boolean boost, List<String> gone) {
		if (writer == null) return;
		JsonObject event = event("block_break");
		writePos(event, pos);
		event.addProperty("block", block);
		event.addProperty("ticks", ticks);
		event.addProperty("clean", clean);
		event.addProperty("boost", boost);
		if (!gone.isEmpty()) {
			JsonArray array = new JsonArray();
			gone.forEach(array::add);
			event.add("gone", array);
		}
		write(event);
	}

	private static void writePos(JsonObject event, BlockPos pos) {
		event.addProperty("x", pos.getX());
		event.addProperty("y", pos.getY());
		event.addProperty("z", pos.getZ());
	}

	public void mark(String note) {
		if (writer == null) return;
		JsonObject event = event("mark");
		event.addProperty("note", note);
		write(event);
	}

	// at = key press time
	public boolean quickMark(String category, String note, long at) {
		if (writer == null) return false;
		JsonObject event = new JsonObject();
		event.addProperty("t", Math.max(0, at - startMillis));
		event.addProperty("type", "mark");
		event.addProperty("note", note);
		event.addProperty("auto", "key");
		event.addProperty("category", category);
		write(event);
		return true;
	}

	// once per kind + key per file, so claude finds the odd spots fast
	private void autoMark(String kind, String key, String note) {
		if (writer == null || !marked.add(kind + "|" + key)) return;
		JsonObject event = event("mark");
		event.addProperty("note", note);
		event.addProperty("auto", kind);
		write(event);
	}

	// client thread
	public void onError(Guard.Failure failure) {
		if (writer == null) return;
		JsonObject event = event("error");
		event.addProperty("what", failure.what());
		event.addProperty("count", failure.count());
		event.addProperty("error", failure.error());
		event.addProperty("frame", failure.frame());
		write(event);
		autoMark("error", failure.what() + "|" + failure.frame(), failure.what() + ": " + failure.error());
	}

	// ChatWatch flagged, checked once in Surveyor
	public void onUnmatched(String text) {
		autoMark("unmatched", ChatWatch.key(text), text);
	}

	// what live detection made of the lines above, replay checks it
	public void onLiveEvent(ShaftEvent event) {
		if (writer == null) return;
		JsonObject json = event("live");
		json.addProperty("event", EventText.of(event));
		write(json);
		if (event instanceof ShaftLeft left) {
			for (String failed : ShaftChecks.failed(left.shaft())) autoMark("check", failed, failed);
		}
	}

	// /sv debug reload mid recording, empty = built-in only
	public void onPatterns(List<String> overrides) {
		if (writer == null) return;
		JsonObject json = event("patterns");
		JsonArray lines = new JsonArray();
		overrides.forEach(lines::add);
		json.add("patternOverrides", lines);
		write(json);
	}

	public void onLiveEntry(JournalEntry entry) {
		if (writer == null) return;
		JsonObject json = event("live_entry");
		json.addProperty("shaft", entry.shaftId());
		// recording time, like t
		json.addProperty("entered", entry.enteredAt() - startMillis);
		if (entry.searchMillis() != null) json.addProperty("search", entry.searchMillis());
		write(json);
	}

	public void onTitle(Component text, boolean subtitle) {
		if (writer == null) return;
		JsonObject event = event(subtitle ? "subtitle" : "title");
		event.addProperty("text", text.getString());
		event.addProperty("legacy", TextUtil.toLegacy(text));
		write(event);
	}

	public void onGameMessage(Component message, boolean overlay) {
		if (writer == null) return;
		String plain = message.getString();
		if (!overlay && PATTERNS.matches("chat.private", TextPatterns.clean(plain))) return;
		JsonObject event = event(overlay ? "actionbar" : "chat");
		event.addProperty("text", plain);
		event.addProperty("legacy", TextUtil.toLegacy(message));
		List<String> hovers = TextUtil.hoverTexts(message);
		if (!hovers.isEmpty()) {
			JsonArray array = new JsonArray();
			hovers.forEach(array::add);
			event.add("hover", array);
		}
		write(event);
	}

	public void onTick(Minecraft client) {
		// counts while off too, same ticks as LiveTracker
		tick++;
		if (writer != null && writer.failure() != null) {
			String why = writer.failure();
			failedAuto = autoSession;
			writer.close();
			writer = null;
			client.gui.hud.getChat().addClientSystemMessage(Component.literal("§c[Surveyor] Could not write the recording: " + why));
		}
		if (writer == null) return;
		if (client.level != lastLevel) {
			lastLevel = client.level;
			resetSnapshots();
			write(event("world_change"));
		}
		if (client.level == null || client.player == null) return;

		// contents come a few ticks after it opens
		if (tick % 5 == 0) recordScreen(client.gui.screen());
		if (extra && tick % 10 == 0) recordBossBars(client);
		if (tick % 10 == 0) {
			recordScoreboard(client.level.getScoreboard());
			recordTabList(client);
			recordHeldItem(client.player);
		}
		if (tick % 20 == 0) {
			recordArmorStands(client.level, client.player);
		}
		if (tick % 100 == 0) {
			JsonObject event = event("position");
			addPosition(event, client.player.position());
			write(event);
		}
	}

	// container screens only (sell sacks, sacks, menus), not ur inventory
	private void recordScreen(Screen screen) {
		AbstractContainerScreen<?> shown = screen instanceof AbstractContainerScreen<?> container && !(screen instanceof InventoryScreen)
				&& !(screen instanceof CreativeModeInventoryScreen) && Surveyor.SETTINGS.get().dev().isRecordScreens() ? container : null;
		if (shown != lastScreen) {
			if (lastScreen != null) write(event("screen_close"));
			lastScreen = shown;
			lastScreenSignature = "";
		}
		if (shown == null) return;
		JsonArray slots = new JsonArray();
		for (Slot slot : shown.getMenu().slots) {
			if (slot.container instanceof Inventory) continue;
			JsonObject item = describeItem(slot.getItem());
			if (item == null) continue;
			item.addProperty("slot", slot.index);
			slots.add(item);
		}
		String title = TextUtil.toLegacy(shown.getTitle());
		String signature = title + slots;
		if (signature.equals(lastScreenSignature)) return;
		boolean update = !lastScreenSignature.isEmpty();
		lastScreenSignature = signature;
		JsonObject event = event("screen");
		event.addProperty("title", title);
		if (update) event.addProperty("update", true);
		event.add("slots", slots);
		write(event);
	}

	// extra mode. idk if the mines have one, this finds out
	private void recordBossBars(Minecraft client) {
		JsonArray bars = new JsonArray();
		for (LerpingBossEvent bar : ((BossHealthOverlayAccessor) client.gui.hud.getBossOverlay()).skysurveyor$getEvents().values()) {
			JsonObject json = new JsonObject();
			json.addProperty("name", TextUtil.toLegacy(bar.getName()));
			json.addProperty("progress", Math.round(bar.getProgress() * 100) / 100.0);
			json.addProperty("color", bar.getColor().name());
			bars.add(json);
		}
		String signature = bars.toString();
		if (signature.equals(lastBossBars)) return;
		lastBossBars = signature;
		JsonObject event = event("bossbar");
		event.add("bars", bars);
		write(event);
	}

	// extra mode, packet thread = client thread (after ensureRunningOnSameThread)
	public void onSound(Holder<SoundEvent> sound, SoundSource source, double x, double y, double z, float volume, float pitch, Integer entity) {
		if (writer == null || !extra) return;
		String id = sound.unwrapKey().map(key -> key.identifier().toString()).orElseGet(() -> sound.value().location().toString());
		String path = id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
		if (!SoundFilter.keep(path)) return;
		JsonObject event = event("sound");
		event.addProperty("sound", id);
		event.addProperty("source", source.getName());
		if (entity != null) event.addProperty("entity", entity);
		else addPosition(event, new Vec3(x, y, z));
		event.addProperty("volume", Math.round(volume * 100) / 100.0);
		event.addProperty("pitch", Math.round(pitch * 100) / 100.0);
		write(event);
	}

	// own name from login, not tab. every write bc a joiners 1st line can beat any tick
	private void updateNames(Minecraft client) {
		List<String> online = new ArrayList<>();
		if (client.getConnection() != null) {
			for (PlayerInfo info : client.getConnection().getOnlinePlayers()) online.add(info.getProfile().name());
		}
		names.update(client.getUser().getName(), online);
	}

	private void resetSnapshots() {
		lastScoreboard = List.of();
		lastTabList = List.of();
		lastTabHeaderFooter = "";
		lastHeldItem = "";
		knownStands.clear();
	}

	private void recordScoreboard(Scoreboard scoreboard) {
		List<String> lines = GameText.scoreboardLines(scoreboard);
		if (!lines.equals(lastScoreboard)) {
			lastScoreboard = lines;
			write(linesEvent("scoreboard", lines));
		}
	}

	private void recordTabList(Minecraft client) {
		if (client.getConnection() == null) return;
		List<String> lines = GameText.tabListLines(client);
		if (!lines.equals(lastTabList)) {
			lastTabList = lines;
			write(linesEvent("tablist", lines));
		}

		PlayerTabOverlayAccessor accessor = (PlayerTabOverlayAccessor) client.gui.hud.getTabList();
		String header = legacyOrEmpty(accessor.skysurveyor$getHeader());
		String footer = legacyOrEmpty(accessor.skysurveyor$getFooter());
		if (!(header + "\n" + footer).equals(lastTabHeaderFooter)) {
			lastTabHeaderFooter = header + "\n" + footer;
			JsonObject event = event("tab_header_footer");
			event.addProperty("header", header);
			event.addProperty("footer", footer);
			write(event);
		}
	}

	private void recordHeldItem(LocalPlayer player) {
		JsonObject item = describeItem(player.getInventory().getSelectedItem());
		String signature = item == null ? "" : item.toString();
		if (!signature.equals(lastHeldItem)) {
			lastHeldItem = signature;
			JsonObject event = event("held_item");
			event.add("item", item);
			write(event);
		}
	}

	private void recordArmorStands(ClientLevel level, LocalPlayer player) {
		AABB area = player.getBoundingBox().inflate(ENTITY_SCAN_RADIUS);
		Set<Integer> seen = new HashSet<>();
		for (ArmorStand stand : level.getEntitiesOfClass(ArmorStand.class, area)) {
			JsonObject description = describeArmorStand(stand);
			// decoration, skip for small files
			if (!description.has("name") && !description.has("equipment")) continue;
			seen.add(stand.getId());
			String signature = description.toString();
			if (!signature.equals(knownStands.put(stand.getId(), signature))) {
				JsonObject event = event("armor_stand");
				event.add("stand", description);
				write(event);
			}
		}
		knownStands.keySet().removeIf(id -> {
			if (seen.contains(id)) return false;
			JsonObject event = event("armor_stand_gone");
			event.addProperty("id", id);
			write(event);
			return true;
		});
	}

	private static JsonObject describeArmorStand(ArmorStand stand) {
		JsonObject description = new JsonObject();
		description.addProperty("id", stand.getId());
		if (stand.hasCustomName()) {
			description.addProperty("name", TextUtil.toLegacy(Objects.requireNonNull(stand.getCustomName())));
		}
		addPosition(description, stand.position());
		description.addProperty("invisible", stand.isInvisible());
		description.addProperty("small", stand.isSmall());
		description.addProperty("marker", stand.isMarker());
		JsonObject equipment = new JsonObject();
		for (EquipmentSlot slot : EquipmentSlot.VALUES) {
			JsonObject item = describeItem(stand.getItemBySlot(slot));
			if (item != null) equipment.add(slot.getName(), item);
		}
		if (!equipment.isEmpty()) description.add("equipment", equipment);
		return description;
	}

	private static JsonObject describeItem(ItemStack stack) {
		if (stack.isEmpty()) return null;
		JsonObject item = new JsonObject();
		item.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
		item.addProperty("count", stack.getCount());
		item.addProperty("name", TextUtil.toLegacy(stack.getHoverName()));
		CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
		if (customData != null && !customData.isEmpty()) {
			item.addProperty("custom_data", customData.toString());
		}
		var lore = stack.get(DataComponents.LORE);
		if (lore != null && !lore.lines().isEmpty()) {
			JsonArray lines = new JsonArray();
			lore.lines().forEach(line -> lines.add(TextUtil.toLegacy(line)));
			item.add("lore", lines);
		}
		// uuid + skin, left out when anonymizing
		var profile = stack.get(DataComponents.PROFILE);
		if (profile != null && !skipProfiles) {
			item.addProperty("profile", profile.toString());
		}
		return item;
	}

	private void write(JsonObject event) {
		if (anonymize) {
			updateNames(Minecraft.getInstance());
			names.apply(event);
		}
		writer.write(event);
	}

	private JsonObject event(String type) {
		JsonObject event = new JsonObject();
		event.addProperty("t", System.currentTimeMillis() - startMillis);
		event.addProperty("type", type);
		return event;
	}

	private JsonObject linesEvent(String type, List<String> lines) {
		JsonObject event = event(type);
		JsonArray array = new JsonArray();
		lines.forEach(array::add);
		event.add("lines", array);
		return event;
	}

	private static void addPosition(JsonObject target, Vec3 position) {
		target.addProperty("x", Math.round(position.x * 10) / 10.0);
		target.addProperty("y", Math.round(position.y * 10) / 10.0);
		target.addProperty("z", Math.round(position.z * 10) / 10.0);
	}

	private static String legacyOrEmpty(Component component) {
		return component == null ? "" : TextUtil.toLegacy(component);
	}
}
