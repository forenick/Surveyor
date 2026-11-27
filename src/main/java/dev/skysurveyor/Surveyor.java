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
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.calc.Fiesta;
import dev.skysurveyor.calc.LiveGuide;
import dev.skysurveyor.debug.ChatWatch;
import dev.skysurveyor.debug.DebugOverlay;
import dev.skysurveyor.debug.EventLog;
import dev.skysurveyor.debug.LiveDebug;
import dev.skysurveyor.debug.PatternOverrides;
import dev.skysurveyor.detection.EventText;
import dev.skysurveyor.detection.LiveTracker;
import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.effects.Consumable;
import dev.skysurveyor.effects.LiveEffects;
import dev.skysurveyor.hud.EditHudScreen;
import dev.skysurveyor.hud.EntryTitleOverlay;
import dev.skysurveyor.hud.FossilWaypointRenderer;
import dev.skysurveyor.hud.LiveHud;
import dev.skysurveyor.journal.LiveJournal;
import dev.skysurveyor.mining.BlockWatch;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.recorder.AutoRecorder;
import dev.skysurveyor.recorder.MarkKey;
import dev.skysurveyor.recorder.ShaftRecorder;
import dev.skysurveyor.screen.EventLogScreen;
import dev.skysurveyor.screen.JournalScreen;
import dev.skysurveyor.screen.QuickMarkScreen;
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
	// not /st = hypixel /storage. /shaftjournal + /sj from before the rename
	private static final List<String> COMMAND_NAMES = List.of("surveyor", "shaftjournal", "sj", "sv");
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	public static final ShaftRecorder RECORDER = new ShaftRecorder();
	public static final LiveTracker TRACKER = new LiveTracker();
	public static final LiveSettings SETTINGS = new LiveSettings();
	public static final LiveMoney MONEY = new LiveMoney(SETTINGS);
	public static final LiveJournal JOURNAL = new LiveJournal(TRACKER::inMiningArea, MONEY, SETTINGS);
	public static final LiveEffects EFFECTS = new LiveEffects();
	public static final LiveHud HUD = new LiveHud(TRACKER, JOURNAL, MONEY, SETTINGS, EFFECTS::left);
	// read only: election data, sacks msgs, tab list
	public static final Fiesta FIESTA = new Fiesta(TextPatterns.defaults());
	public static final LiveGuide GUIDE = new LiveGuide(task -> Minecraft.getInstance().execute(task));
	private static final EntryTitleOverlay ENTRY_TITLE = new EntryTitleOverlay(() -> SETTINGS.get().entryTitle());
	private static final FossilWaypointRenderer FOSSIL_WAYPOINT = new FossilWaypointRenderer(TRACKER::currentShaft,
			() -> SETTINGS.get().waypoints());
	private static final EventLog EVENT_LOG = new EventLog();
	private static final ChatWatch CHAT_WATCH = ChatWatch.defaults(TextPatterns.defaults());
	private static final DebugOverlay DEBUG_OVERLAY = new DebugOverlay(EVENT_LOG);
	private static final AutoRecorder AUTO_RECORD = new AutoRecorder(RECORDER, SETTINGS, JOURNAL::activeSession,
			Surveyor::inMines);
	// reads the clients world only
	private static final BlockWatch BLOCKS = new BlockWatch((pos, block, ticks, clean, gone) -> {
		Guard.run("recorder block break", () -> RECORDER.onBlockBreak(pos, block, ticks, clean, JOURNAL.boosting(), gone));
		Guard.run("tracker block break", () -> TRACKER.onBlockBreak(block, gone));
		Guard.run("journal block break", () -> JOURNAL.onBlockBreak(block, ticks, clean, gone));
	});

	// unbound, opens our own screen only. old key name, options.txt saves by name
	private static final KeyMapping JOURNAL_KEY = new KeyMapping("key.shaftjournal.journal", InputConstants.Type.KEYSYM,
			InputConstants.UNKNOWN.getValue(), KeyMapping.Category.register(Identifier.fromNamespaceAndPath(MOD_ID, "main")));

	// unbound, only opens our own screen. null = not in controls (MarkKey)
	private static KeyMapping markKey;

	// next tick, after chat closes
	private boolean openJournal;
	private boolean openSettings;
	private boolean openHud;
	private boolean openLog;
	// shown once a world is loaded
	private volatile String pendingChat;

	@Override
	public void onInitializeClient() {
		moveOldDataFolder();
		// every hook guarded, a throw crashes or disconnects
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("recorder tick", () -> RECORDER.onTick(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("tracker tick", () -> TRACKER.onTick(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("block watch tick", () -> {
			BLOCKS.onTick(client);
			if (BLOCKS.holding()) JOURNAL.onStillMining();
		}));
		KeyMappingHelper.registerKeyMapping(JOURNAL_KEY);
		registerMarkKey();
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("open journal", () -> openJournalIfAsked(client)));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Guard.run("settings + prices start", () -> {
			Path folder = DataFolder.in(client.gameDirectory.toPath());
			// moneys io thread, config + price refreshes stay in order
			SETTINGS.start(folder, MONEY::run);
			MONEY.start(folder, Objects.requireNonNullElse(modVersion(), "dev"));
			// queued behind the config load
			MONEY.run(() -> AUTO_RECORD.deleteOld(folder.resolve("recordings")));
			MONEY.run(() -> pendingChat = PatternOverrides.load(folder));
			EFFECTS.start(folder, MONEY::run);
		}));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Guard.run("journal start", () -> JOURNAL.start(client)));
		ClientLifecycleEvents.CLIENT_STARTED.register(client -> Guard.run("hud start", () -> HUD.start(client)));
		// before the hud, so it shows this ticks timers
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("effects tick", () -> EFFECTS.onTick(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("fiesta tick",
				() -> FIESTA.onTick(System.currentTimeMillis(), TRACKER.currentShaft() != null, flaskRunning())));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("hud tick", () -> HUD.onTick(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("auto record", () -> AUTO_RECORD.onTick(client)));
		SETTINGS.onChange((before, after) -> Guard.run("recorder settings", () -> RECORDER.onSettings(after)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("first run hint",
				() -> JOURNAL.onTick(inMines())));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
				(graphics, delta) -> Guard.run("hud render", () -> HUD.render(graphics, delta)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("debug overlay tick", () -> DEBUG_OVERLAY.onTick(client)));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("pending chat", () -> {
			String text = pendingChat;
			if (text == null || client.player == null) return;
			pendingChat = null;
			client.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
		}));
		ClientTickEvents.END_CLIENT_TICK.register(client -> Guard.run("fossil waypoint tick", () -> FOSSIL_WAYPOINT.tick(client)));
		LevelRenderEvents.BEFORE_GIZMOS.register(context -> Guard.run("fossil waypoint", FOSSIL_WAYPOINT::render));
		HudElementRegistry.attachElementAfter(VanillaHudElements.TITLE_AND_SUBTITLE, Identifier.fromNamespaceAndPath(MOD_ID, "entry_title"),
				(graphics, delta) -> Guard.run("entry title render", () -> ENTRY_TITLE.render(graphics, delta)));
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath(MOD_ID, "debug"),
				(graphics, delta) -> Guard.run("debug overlay render", () -> DEBUG_OVERLAY.render(graphics, delta)));
		// one failing doesnt skip journal close
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			// before journal.close, else the shaft ur in is lost
			Guard.run("quit", TRACKER::onQuit);
			// after onQuit so the last shaft is recorded. waits, recorder thread dies w/ the game
			Guard.run("recorder stop", () -> RECORDER.stop(true));
			Guard.run("journal close", JOURNAL::close);
			Guard.run("hud close", HUD::close);
			Guard.run("guide close", GUIDE::close);
			// queues its save, before the io thread closes
			Guard.run("effects close", EFFECTS::close);
			Guard.run("money close", MONEY::close);
		});
		JOURNAL.onToast(HUD::showToast);
		JOURNAL.addEntryListener(RECORDER::onLiveEntry);
		// 1st, so live lines come before the journals
		TRACKER.addListener(RECORDER::onLiveEvent);
		TRACKER.addListener(JOURNAL::onEvent);
		TRACKER.addListener(ENTRY_TITLE::onEvent);
		MONEY.onElection(json -> FIESTA.onElection(System.currentTimeMillis(), json));
		TRACKER.addListener(event -> {
			// mines only, a bazaar claim elsewhere isnt a drop
			if (event instanceof ShaftEvent.SacksChanged sacks && inMines()) FIESTA.onSacks(System.currentTimeMillis(), sacks.added());
			if (event instanceof ShaftEvent.ShaftEntered) FIESTA.onShaftEntered();
		});
		TRACKER.addTabListener(lines -> FIESTA.onTabList(System.currentTimeMillis(), lines));
		JOURNAL.stampEntries(entry -> entry.withFiesta(FIESTA.reading(System.currentTimeMillis()).on(), FIESTA.flaskMillis()));
		TRACKER.addChatListener(JOURNAL::onChat);
		TRACKER.addChatListener(EFFECTS::onChat);
		TRACKER.addListener(event -> {
			if (EventLog.keeps(event)) EVENT_LOG.add(System.currentTimeMillis(), EventLog.Kind.EVENT, EventText.of(event));
		});
		TRACKER.addChatListener(legacy -> {
			String unmatched = CHAT_WATCH.unmatched(legacy);
			if (unmatched == null) return;
			EVENT_LOG.add(System.currentTimeMillis(), EventLog.Kind.UNMATCHED, unmatched);
			if (RECORDER.isRecording()) RECORDER.onUnmatched(unmatched);
		});
		Guard.addListener(failure -> EVENT_LOG.add(System.currentTimeMillis(), EventLog.Kind.ERROR,
				failure.what() + ": " + failure.error() + " at " + failure.frame()));
		// any thread, recorder client thread only. no guard, would loop
		Guard.addListener(failure -> Minecraft.getInstance().execute(() -> {
			try {
				RECORDER.onError(failure);
			} catch (RuntimeException e) {
				LOGGER.warn("Could not record an error: {}", e.toString());
			}
		}));
		TRACKER.addListener(MONEY::onEvent);
		TRACKER.onWorldChange(JOURNAL::onWorldChange);
		TRACKER.onWorldChange(BLOCKS::reset);
		TRACKER.addTabListener(HUD::onTabList);
		TRACKER.onWorldChange(HUD::onWorldChange);
		// own hits only, PASS = never changes the hit
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
			if (level.isClientSide()) {
				Guard.run("block hit", JOURNAL::onBlockHit);
				Guard.run("recorder block hit", () -> RECORDER.onBlockHit(pos));
				Guard.run("block watch hit", () -> BLOCKS.onAttack(level, pos));
			}
			return InteractionResult.PASS;
		});

		// own right-clicks only, PASS = never changes the use
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide()) Guard.run("item use", EFFECTS::onUse);
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
						.then(ClientCommands.literal("start").executes(context -> startRecording(context, false))
								.then(ClientCommands.literal("extra").executes(context -> startRecording(context, true))))
						.then(ClientCommands.literal("stop").executes(this::stopRecording)))
				.then(ClientCommands.literal("debug")
						.then(ClientCommands.literal("info").executes(this::copyDebugInfo))
						.then(ClientCommands.literal("log").executes(context -> {
							openLog = true;
							return 1;
						}))
						.then(ClientCommands.literal("reload").executes(this::reloadPatterns)))
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

	// only now, fabric throws once Options exists. after the folder move, it reads config.json
	private static void registerMarkKey() {
		Path gameDir = FabricLoader.getInstance().getGameDir();
		if (!MarkKey.inControls(gameDir, DataFolder.in(gameDir))) return;
		markKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(MarkKey.NAME, InputConstants.Type.KEYSYM,
				InputConstants.UNKNOWN.getValue(), JOURNAL_KEY.getCategory()));
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
		if (markKey != null && markKey.consumeClick()) {
			while (markKey.consumeClick()) {
				// one screen per press burst
			}
			if (client.gui.screen() == null) client.gui.setScreen(new QuickMarkScreen(System.currentTimeMillis()));
		}
		if (openLog) {
			openLog = false;
			client.gui.setScreen(new EventLogScreen(EVENT_LOG));
			return;
		}
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

	// a shaft counts as the mines too
	private static boolean inMines() {
		return TRACKER.inMiningArea() || TRACKER.currentShaft() != null;
	}

	// client thread
	public static boolean flaskRunning() {
		Long left = EFFECTS.left(Consumable.FIESTA_FLASK);
		return left != null && left > 0;
	}

	// null = unknown. client thread
	public static Fiesta.State fiestaState() {
		return FIESTA.state(System.currentTimeMillis(), flaskRunning());
	}

	public static String modVersion() {
		return FabricLoader.getInstance().getModContainer(MOD_ID)
				.map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse(null);
	}

	// extra = boss bar + sounds, does hypixel use them in the mines?
	private int startRecording(CommandContext<FabricClientCommandSource> context, boolean extra) {
		Path file = RECORDER.start(context.getSource().getClient(), null, extra);
		context.getSource().sendFeedback(Component.literal("§a[Surveyor] Recording to " + file.getFileName()));
		return 1;
	}

	private int stopRecording(CommandContext<FabricClientCommandSource> context) {
		// only auto ones, stopping ur own keeps auto record on
		if (RECORDER.autoSession() != null) AUTO_RECORD.stoppedByHand();
		Path file = RECORDER.stop();
		context.getSource().sendFeedback(Component.literal(file == null
				? "§e[Surveyor] Not recording."
				: "§a[Surveyor] Saved " + file.toAbsolutePath()));
		return 1;
	}

	private int copyDebugInfo(CommandContext<FabricClientCommandSource> context) {
		LiveDebug.copyDebugInfo(context.getSource().getClient(),
				() -> context.getSource().sendFeedback(Component.literal("§a[Surveyor] Debug info copied, paste it into your GitHub issue.")));
		return 1;
	}

	private int reloadPatterns(CommandContext<FabricClientCommandSource> context) {
		Minecraft client = context.getSource().getClient();
		Path folder = DataFolder.in(client.gameDirectory.toPath());
		MONEY.run(() -> {
			PatternOverrides.Read read = PatternOverrides.read(folder);
			// swap between two chat lines + in the recording, so replay switches there too
			client.execute(() -> Guard.run("pattern reload", () -> {
				List<String> before = TextPatterns.overrides();
				String text = PatternOverrides.apply(read);
				if (!TextPatterns.overrides().equals(before)) RECORDER.onPatterns(TextPatterns.overrides());
				pendingChat = text != null ? text : "§7[Surveyor] No skysurveyor/" + PatternOverrides.FILE + ", using the built-in patterns.";
			}));
		});
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
