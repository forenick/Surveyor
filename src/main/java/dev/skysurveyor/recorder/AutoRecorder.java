package dev.skysurveyor.recorder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BooleanSupplier;
import java.util.function.LongFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import dev.skysurveyor.journal.Journal;
import dev.skysurveyor.settings.DevSettings;
import dev.skysurveyor.settings.LiveSettings;

// starts + stops the recorder for the journal session, deletes old auto recordings on start. local files only
public final class AutoRecorder {
	private static final int CHECK_TICKS = 20;

	private final ShaftRecorder recorder;
	private final LiveSettings settings;
	private final LongFunction<Journal.Session> activeSession;
	private final BooleanSupplier inArea;
	private final AutoRecordRule rule = new AutoRecordRule();
	private int tick;

	public AutoRecorder(ShaftRecorder recorder, LiveSettings settings, LongFunction<Journal.Session> activeSession, BooleanSupplier inArea) {
		this.recorder = recorder;
		this.settings = settings;
		this.activeSession = activeSession;
		this.inArea = inArea;
	}

	public void onTick(Minecraft client) {
		if (++tick % CHECK_TICKS != 0) return;
		// file couldnt be written, no retry every second
		Long failed = recorder.takeFailedAuto();
		if (failed != null) rule.stoppedByHand(failed);
		boolean on = settings.get().dev().autoRecord() == DevSettings.AutoRecord.MINING_AREAS;
		Journal.Session session = activeSession.apply(System.currentTimeMillis());
		Long id = session == null ? null : session.id();
		switch (rule.next(on, recorder.isRecording(), recorder.autoSession(), id, inArea.getAsBoolean())) {
			case START -> {
				recorder.start(client, id, false);
				chat(client, "§7[Surveyor] Auto-recording started.");
			}
			case STOP -> {
				Path file = recorder.stop();
				if (file != null) chat(client, "§7[Surveyor] Recording saved: " + file.getFileName());
			}
			case NONE -> {
			}
		}
	}

	// /sv record stop, no new auto recording until the next session
	public void stoppedByHand() {
		Journal.Session session = activeSession.apply(System.currentTimeMillis());
		rule.stoppedByHand(session == null ? null : session.id());
	}

	// io thread, after config.json loaded (else a Forever setting reads as the default)
	public void deleteOld(Path folder) {
		int days = settings.get().dev().keepRecordings().days();
		try {
			for (Path file : RecordingFiles.expired(folder, days, System.currentTimeMillis())) {
				Files.deleteIfExists(file);
				ShaftRecorder.LOGGER.info("Deleted old auto recording {}", file.getFileName());
			}
		} catch (IOException | RuntimeException e) {
			ShaftRecorder.LOGGER.warn("Could not delete old auto recordings: {}", e.toString());
		}
	}

	private static void chat(Minecraft client, String text) {
		client.gui.hud.getChat().addClientSystemMessage(Component.literal(text));
	}
}
