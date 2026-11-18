package dev.skysurveyor.util;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// every call from the game into us. a throw in a packet handler = vanilla disconnects, in a tick = crash
// RuntimeException only, Errors still go up
public final class Guard {
	// display choice, a throw per tick or frame cant flood the log
	static final int LOG_EVERY = 1000;
	private static final Logger LOGGER = LoggerFactory.getLogger("Surveyor");
	private static final Guard SHARED = new Guard((what, count, error) -> {
		if (count == 1) LOGGER.error("Error in {}, carrying on", what, error);
		else LOGGER.error("Error in {} again ({} times), carrying on", what, count, error);
	});

	interface Report {
		void failed(String what, long count, RuntimeException error);
	}

	private final Report report;
	private final Map<String, AtomicLong> counts = new ConcurrentHashMap<>();

	Guard(Report report) {
		this.report = report;
	}

	// false = it threw
	public static boolean run(String what, Runnable task) {
		return SHARED.attempt(what, task);
	}

	// fallback only built when it threw
	public static <T> T get(String what, Supplier<T> task, Supplier<T> fallback) {
		return SHARED.attemptGet(what, task, fallback);
	}

	// each on its own, one throwing doesnt skip the rest
	public static <T> void each(String what, List<? extends Consumer<? super T>> listeners, T value) {
		SHARED.attemptEach(what, listeners, value);
	}

	public static void each(String what, List<? extends Runnable> tasks) {
		for (Runnable task : tasks) SHARED.attempt(what, task);
	}

	boolean attempt(String what, Runnable task) {
		try {
			task.run();
			return true;
		} catch (RuntimeException e) {
			failed(what, e);
			return false;
		}
	}

	<T> T attemptGet(String what, Supplier<T> task, Supplier<T> fallback) {
		try {
			return task.get();
		} catch (RuntimeException e) {
			failed(what, e);
			return fallback.get();
		}
	}

	<T> void attemptEach(String what, List<? extends Consumer<? super T>> listeners, T value) {
		for (Consumer<? super T> listener : listeners) attempt(what, () -> listener.accept(value));
	}

	// kind = where in our code it threw, so 2 bugs under one label both get their stack
	private void failed(String what, RuntimeException error) {
		String key = what + "|" + error.getClass().getName() + "|" + ownFrame(error.getStackTrace());
		long count = counts.computeIfAbsent(key, k -> new AtomicLong()).incrementAndGet();
		if (count == 1 || count % LOG_EVERY == 0) report.failed(what, count, error);
	}

	private static String ownFrame(StackTraceElement[] stack) {
		for (StackTraceElement frame : stack) {
			if (frame.getClassName().startsWith("dev.skysurveyor.")) return frame.toString();
		}
		return stack.length > 0 ? stack[0].toString() : "";
	}
}
