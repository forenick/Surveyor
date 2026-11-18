package dev.skysurveyor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.detection.LiveTracker;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.hud.LiveHud;
import dev.skysurveyor.journal.LiveJournal;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.recorder.ShaftRecorder;
import dev.skysurveyor.screen.JournalScreen;
import dev.skysurveyor.screen.SettingsScreen;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.util.DataFolder;
import dev.skysurveyor.util.Guard;

/**
 This is my first real Java project i'll make public. The code is probably ass in some places.
 Author is @forenick

 Yes, i did use AI to help me out in some places. (go to README.MD)

 Fun Fact: Lobsters pee out of their faces. From right under their eyes. :o

 **/

public class Surveyor implements ClientModInitializer {
	public static final String MOD_ID = "skysurveyor";
	// not /st, thats hypixels /storage. /shaftjournal + /sj kept from before the rename
	private static final List<String> COMMAND_NAMES = List.of("surveyor", "shaftjournal", "sj", "sv");
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	public static final ShaftRecorder RECORDER = new ShaftRecorder();
	public static final LiveTracker TRACKER = new LiveTracker();
	public static final LiveSettings SETTINGS = new LiveSettings();
	public static final LiveMoney MONEY = new LiveMoney(SETTINGS);
	public static final LiveJournal JOURNAL = new LiveJournal(TRACKER::inMiningArea, MONEY, SETTINGS);
	public static final LiveHud HUD = new LiveHud(TRACKER, JOURNAL, MONEY, SETTINGS);

	// unbound, only opens our own screen. old key name on purpose, options.txt saves by name
	private static final KeyMapping JOURNAL_KEY = new KeyMapping("key.shaftjournal.journal", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main")));

	// opens next tick once the chat screen is closed
	private boolean openJournal;
	private boolean openSettings;
	private boolean openHud;

	@Override
	public void onInitializeClient() {
		moveOldDataFolder();
		// every hook from the game guarded (util/Guard), a throw would crash or disconnect
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("recorder tick", () -> RECORDER.onTick(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("tracker tick", () -> TRACKER.onTick(client)));
		KeyMappingHelper.registerKeyMapping(JOURNAL_KEY);
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("open journal", () -> openJournalIfAsked(client)));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Guard.run("settings + prices start", () -> {
			Path folder = DataFolder.in(client.gameDirectory.toPath());
			// moneys io thread, config saves + reloads stay in order w/ price refreshes
			SETTINGS.start(folder, MONEY::run);
			MONEY.start(folder, Objects.requireNonNullElse(modVersion(), "dev"));
		}));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Guard.run("journal start", () -> JOURNAL.start(client)));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Guard.run("hud start", () -> HUD.start(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("hud tick", () -> HUD.onTick(client)));
		// a shaft counts as the mines too
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("first run hint",
				() -> JOURNAL.onTick(TRACKER.inMiningArea() || TRACKER.currentShaft() != null)));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
				(graphics, delta) -> Guard.run("hud render", () -> HUD.render(graphics, delta)));
		// each step on its own, one failing doesnt skip the journal close after it
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// before journal.close, else the shaft ur in is lost
			Guard.run("quit", TRACKER::onQuit);
			// after onQuit, so the last shaft is in the recording
			Guard.run("recorder stop", RECORDER::stop);
			Guard.run("journal close", JOURNAL::close);
			Guard.run("hud close", HUD::close);
			Guard.run("money close", MONEY::close);
		});
		JOURNAL.onToast(HUD::showToast);
		JOURNAL.addEntryListener(RECORDER::onLiveEntry);
		// 1st, so live lines come before the journals
		TRACKER.addListener(RECORDER::onLiveEvent);
		TRACKER.addListener(JOURNAL::onEvent);
		TRACKER.addChatListener(JOURNAL::onChat);
		TRACKER.addListener(MONEY::onEvent);
		TRACKER.onWorldChange(JOURNAL::onWorldChange);
		TRACKER.addTabListener(HUD::onTabList);
		TRACKER.onWorldChange(HUD::onWorldChange);
		// own hits only, PASS = never changes the hit
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide()) {
				Guard.run("block hit", JOURNAL::onBlockHit);
				Guard.run("recorder block hit", () -> RECORDER.onBlockHit(pos));
			}
			return InteractionResult.PASS;
		});

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> {
			for (String name : COMMAND_NAMES) dispatcher.register(command(name));
		});
	}

	private LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
		return ClientCommands.literal(name)
				.executes(this::openJournal)
				.then(ClientCommands.literal("journal").executes(this::openJournal))
				.then(ClientCommands.literal("settings").executes(this::openSettings))
				.then(ClientCommands.literal("hud").executes(this::openHud))
				.then(ClientCommands.literal("record")
						.then(ClientCommands.literal("start").executes(this::startRecording))
						.then(ClientCommands.literal("stop").executes(this::stopRecording)))
				.then(ClientCommands.literal("session")
						.then(ClientCommands.literal("new").executes(this::newSession)))
				.then(ClientCommands.literal("mark")
						.then(ClientCommands.argument("note", StringArgumentType.greedyString())
								.executes(this::mark)));
	}

	private int openJournal(CommandContext<FabricClientCommandSource> context) {
		openJournal = true;
		return 1;
	}

	private int openSettings(CommandContext<FabricClientCommandSource> context) {
		openSettings = true;
		return 1;
	}

	private int openHud(CommandContext<FabricClientCommandSource> context) {
		openHud = true;
		return 1;
	}

	private static void moveOldDataFolder() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		try {
			String moved = DataFolder.migrate(gameDir);
			if (moved != null) LOGGER.info("Moved the old {} folder to {}", moved, DataFolder.in(gameDir));
		} catch (IOException e) {
			LOGGER.error("Could not move the old data folder, still using {}", DataFolder.in(gameDir), e);
		}
	}

	private void openJournalIfAsked(Minecraft client) {
		while (JOURNAL_KEY.consumeClick()) openJournal = true;
		if (openHud) {
			openHud = false;
			// no parent, done goes back to the game
			client.gui.setScreen(new EditHudScreen(null, HUD));
			return;
		}
		if (!openJournal && !openSettings) return;
		boolean settings = openSettings;
		openJournal = false;
		openSettings = false;
		MONEY.refresh();
		JournalScreen journal = new JournalScreen();
		// back from settings lands in the journal
		client.gui.setScreen(settings ? new SettingsScreen(journal) : journal);
	}

	public static String modVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	private int startRecording(CommandContext<FabricClientCommandSource> context) {
		try {
			Path file = RECORDER.start(context.getSource().getClient());
			context.getSource().sendFeedback(Component.literal("§a[Surveyor] Recording to " + file.getFileName()));
		} catch (IOException e) {
			context.getSource().sendError(Component.literal("[Surveyor] Could not start recording: " + e.getMessage()));
		}
		return 1;
	}

	private int stopRecording(CommandContext<FabricClientCommandSource> context) {
		Path file = RECORDER.stop();
		context.getSource().sendFeedback(Component.literal(file == null
				? "§e[Surveyor] Not recording."
				: "§a[Surveyor] Saved " + file.toAbsolutePath()));
		return 1;
	}

	private int newSession(CommandContext<FabricClientCommandSource> context) {
		JOURNAL.newSession();
		context.getSource().sendFeedback(Component.literal("§a[Surveyor] Started a new session."));
		return 1;
	}

	private int mark(CommandContext<FabricClientCommandSource> context) {
		if (!RECORDER.isRecording()) {
			context.getSource().sendError(Component.literal("[Surveyor] Not recording."));
			return 0;
		}
		RECORDER.mark(StringArgumentType.getString(context, "note"));
		context.getSource().sendFeedback(Component.literal("§7[Surveyor] Marked."));
		return 1;
	}
}
