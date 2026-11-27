package dev.skysurveyor.calc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.skysurveyor.detection.TextPatterns;
import dev.skysurveyor.journal.JournalEntry;
import dev.skysurveyor.journal.JournalFile;
import dev.skysurveyor.journal.JournalStats;
import dev.skysurveyor.journal.JournalStats.SessionSummary;
import dev.skysurveyor.journal.JsonLinesFile;
import dev.skysurveyor.journal.MiningEntry;
import dev.skysurveyor.money.ItemIds;
import dev.skysurveyor.money.MiningValue;
import dev.skysurveyor.money.PriceBook;
import dev.skysurveyor.money.ShaftValue;
import dev.skysurveyor.settings.MoneySettings;

// nicks live prism files, read only. skips w/o them
record NickFiles(List<JournalEntry> entries, List<MiningEntry> mining, PriceBook prices, List<SessionSummary> sessions) {
	static final Path FOLDER = Path.of(System.getProperty("user.home"), ".local/share/PrismLauncher/instances/26.2/minecraft/skysurveyor");

	static NickFiles load() throws IOException {
		if (!Files.exists(FOLDER.resolve("journal.jsonl"))) return null;
		List<JournalEntry> entries = new JournalFile(FOLDER.resolve("journal.jsonl"), Runnable::run).load().join();
		List<MiningEntry> mining = new JsonLinesFile<>(FOLDER.resolve("mining.jsonl"), MiningEntry.class, e -> e.items() != null, Runnable::run).load().join();
		JsonObject json = JsonParser.parseString(Files.readString(FOLDER.resolve("prices.json"), StandardCharsets.UTF_8)).getAsJsonObject();
		PriceBook book = PriceBook.fromJson(json);
		Path items = FOLDER.resolve("items.json");
		if (Files.exists(items)) {
			JsonObject cached = JsonParser.parseString(Files.readString(items, StandardCharsets.UTF_8)).getAsJsonObject();
			if (cached.has("npc")) book = book.withNpc(PriceBook.parseBins(cached.getAsJsonObject("npc")));
		}
		return new NickFiles(entries, mining, book, JournalStats.sessions(entries, mining, List.of()));
	}

	ShaftValue value(JournalEntry entry, MoneySettings settings) {
		return ShaftValue.of(entry, ShaftValue.layered(entry.prices(), prices::get), settings, ItemIds.bundled(Map.of()), TextPatterns.defaults());
	}

	Double outside(MiningEntry entry, MoneySettings settings) {
		return MiningValue.coins(entry, prices::get, settings, ItemIds.bundled(Map.of()), TextPatterns.defaults());
	}
}
