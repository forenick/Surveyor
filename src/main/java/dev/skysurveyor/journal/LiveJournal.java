package dev.skysurveyor.journal;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.ToLongFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.skysurveyor.detection.ShaftEvent;
import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.money.LiveMoney;
import dev.skysurveyor.settings.LiveSettings;
import dev.skysurveyor.settings.SummarySettings;
import dev.skysurveyor.util.DataFolder;

// summary only goes to local chat or a toast, nothing sent to the server
public final class LiveJournal {
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");

	private final Journal journal;
	private final OutsideMining outside;
	private final Stash stash;
	private final PriceCheck priceCheck = new PriceCheck();
	private final Breaks breaks;
	private final LiveMoney money;
	private final LiveSettings settings;
	// every .jsonl write + load, in order per file
	private final ExecutorService io = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Surveyor journal");
		thread.setDaemon(true);
		return thread;
	});
	private final Saved<JournalEntry> saved = new Saved<>();
	private final Saved<MiningEntry> savedMining = new Saved<>();
	private final Saved<StashEntry> savedStash = new Saved<>();
	private final Saved<BreakEntry> savedBreaks = new Saved<>();
	// client thread only, loaded entries go in front
	private final List<JournalEntry> entries = saved.list;
	private final List<MiningEntry> mining = savedMining.list;
	private final List<StashEntry> stashed = savedStash.list;
	private final List<BreakEntry> paused = savedBreaks.list;
	private Consumer<ToastText> toasts = text -> {
	};
	private final List<Consumer<JournalEntry>> entryListeners = new ArrayList<>();

	public LiveJournal(BooleanSupplier inMiningArea, LiveMoney money, LiveSettings settings) {
		journal = new Journal(TextPatterns.defaults(), inMiningArea, this::onEntry);
		outside = new OutsideMining(TextPatterns.defaults(), inMiningArea, journal::activeSession, this::onMining);
		stash = new Stash(TextPatterns.defaults(), inMiningArea, journal::activeSession, savedStash::add);
		breaks = new Breaks(inMiningArea, journal::activeSession, savedBreaks::add);
		this.money = money;
		this.settings = settings;
	}

	public void onToast(Consumer<ToastText> listener) {
		toasts = listener;
	}

	public void addEntryListener(Consumer<JournalEntry> listener) {
		entryListeners.add(listener);
	}

	public void start(Minecraft client) {
		Path folder = DataFolder.in(client.gameDirectory.toPath());
		saved.load(client, new JournalFile(folder.resolve("journal.jsonl"), io), () -> {
			journal.resume(entries);
			resumeActivity();
		});
		savedMining.load(client, new JsonLinesFile<>(folder.resolve("mining.jsonl"), MiningEntry.class, entry -> entry.items() != null, io),
				this::resumeActivity);
		savedStash.load(client, new JsonLinesFile<>(folder.resolve("stash.jsonl"), StashEntry.class, entry -> entry.grew() > 0, io), () -> {
		});
		savedBreaks.load(client, new JsonLinesFile<>(folder.resolve("breaks.jsonl"), BreakEntry.class, entry -> entry.to() > entry.from(), io),
				() -> {
				});
	}

	// client thread list + its file
	private static final class Saved<T> {
		final List<T> list = new ArrayList<>();
		private JsonLinesFile<T> file;

		void load(Minecraft client, JsonLinesFile<T> file, Runnable after) {
			this.file = file;
			file.load().thenAccept(loaded -> client.execute(() -> {
				list.addAll(0, loaded);
				after.run();
				LOGGER.info("Loaded {} entries from {}", loaded.size(), file.path());
			}));
		}

		void add(T entry) {
			list.add(entry);
			if (file != null) file.append(entry);
		}
	}

	// a restart mid session is a gap too
	private void resumeActivity() {
		Journal.Session session = journal.session();
		if (session == null) return;
		long last = -1;
		if (!entries.isEmpty() && entries.getLast().sessionId() == session.id()) last = entries.getLast().endedAt();
		if (!mining.isEmpty() && mining.getLast().sessionId() == session.id()) last = Math.max(last, mining.getLast().at());
		if (last < 0) return;
		journal.resumeActivity(session.id(), last);
		breaks.resume(session.id(), last);
	}

	public void onEvent(ShaftEvent event) {
		boolean wasRunning = journal.searchRunning();
		long now = System.currentTimeMillis();
		journal.onEvent(event, now);
		outside.onEvent(event, now);
		stash.onEvent(event, now);
		breaks.onEvent(event, now);
		// prices age in a long shaft too
		if (priceCheck.onEvent(event, now)) money.refresh();
		logTimerStart(wasRunning);
	}

	public void onChat(String legacy) {
		stash.onChat(System.currentTimeMillis(), legacy);
	}

	public void onBlockHit() {
		boolean wasRunning = journal.searchRunning();
		long now = System.currentTimeMillis();
		journal.onBlockHit(now);
		outside.onBlockHit(now);
		stash.onBlockHit(now);
		priceCheck.onBlockHit(now);
		breaks.onBlockHit(now);
		logTimerStart(wasRunning);
	}

	public void onWorldChange() {
		outside.onWorldChange(System.currentTimeMillis());
	}

	private void logTimerStart(boolean wasRunning) {
		if (!wasRunning && journal.searchRunning()) LOGGER.info("Spawn timer started");
	}

	// only goes up, change = new entries. client thread only
	public int entryCount() {
		return entries.size();
	}

	public List<JournalEntry> entries() {
		return List.copyOf(entries);
	}

	public List<JournalEntry> entries(int from) {
		return List.copyOf(entries.subList(from, entries.size()));
	}

	// changes when the file loads (loaded entries go in front). client thread only
	public JournalEntry firstEntry() {
		return entries.isEmpty() ? null : entries.getFirst();
	}

	// new object on every change
	public Journal.Session activeSession(long now) {
		return journal.activeSession(now);
	}

	public List<JournalEntry> sessionEntries(Journal.Session session) {
		return trailing(entries, JournalEntry::sessionId, session);
	}

	// sessions are saved in order, so its entries are the last ones
	private static <T> List<T> trailing(List<T> list, ToLongFunction<T> sessionId, Journal.Session session) {
		if (session == null) return List.of();
		int first = list.size();
		while (first > 0 && sessionId.applyAsLong(list.get(first - 1)) == session.id()) first--;
		return List.copyOf(list.subList(first, list.size()));
	}

	// only goes up, change = new entries. client thread only
	public int miningCount() {
		return mining.size();
	}

	public List<MiningEntry> mining(int from) {
		return List.copyOf(mining.subList(from, mining.size()));
	}

	// changes when the file loads (loaded entries go in front). client thread only
	public MiningEntry firstMining() {
		return mining.isEmpty() ? null : mining.getFirst();
	}

	public List<MiningEntry> sessionMining(Journal.Session session) {
		return trailing(mining, MiningEntry::sessionId, session);
	}

	// items that went to the material stash in that session. client thread only
	public long stashed(long sessionId) {
		return Stash.grew(stashed, sessionId);
	}

	// only goes up, change = a new break. client thread only
	public int breakCount() {
		return paused.size();
	}

	public List<BreakEntry> breaks() {
		return List.copyOf(paused);
	}

	// saved + the one going on now, for live rates. client thread only
	public List<BreakEntry> sessionBreaks(Journal.Session session, long now) {
		if (session == null) return List.of();
		List<BreakEntry> list = new ArrayList<>(trailing(paused, BreakEntry::sessionId, session));
		BreakEntry current = breaks.current(now);
		if (current != null && current.sessionId() == session.id()) list.add(current);
		return list;
	}

	public Long spawnTimerMillis(long now) {
		return journal.spawnTimerMillis(now);
	}

	public void newSession() {
		journal.newSession(System.currentTimeMillis());
	}

	public void close() {
		io.shutdown();
		try {
			if (!io.awaitTermination(5, TimeUnit.SECONDS)) LOGGER.warn("Journal writes did not finish in time");
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	// keeps prices fresh while mining, fetches only whats stale
	private void onMining(MiningEntry found) {
		savedMining.add(money.withPrices(found));
		money.refresh();
	}



	private void onEntry(JournalEntry finished) {
		JournalEntry entry = money.withPrices(finished);
		saved.add(entry);
		entryListeners.forEach(listener -> listener.accept(entry));
		SummarySettings.Show show = settings.get().summary().show();
		if (show.chat()) {
			var chat = Minecraft.getInstance().gui.hud.getChat();
			for (String line : SummaryText.lines(entry, journal.session(), money.value(entry), money.settings())) {
				chat.addClientSystemMessage(Component.literal(line));
			}
		}
		if (show.toast()) toasts.accept(ToastText.of(entry, journal.session(), money.value(entry), money.settings().isIronman()));
	}
}
