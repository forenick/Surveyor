package dev.skysurveyor.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardTest {
	private final List<String> reports = new ArrayList<>();
	private final Guard guard = new Guard((what, count, error) -> reports.add(what + " " + count + " " + error.getMessage()));

	@Test
	void runsAndReportsNothingWhenNothingThrows() {
		List<String> ran = new ArrayList<>();
		assertTrue(guard.attempt("tick", () -> ran.add("ran")));
		assertEquals(List.of("ran"), ran);
		assertEquals(List.of(), reports);
	}

	@Test
	void catchesARuntimeExceptionAndCarriesOn() {
		assertFalse(guard.attempt("chat", () -> Integer.parseInt(",")));
		assertEquals(1, reports.size());
		assertTrue(reports.getFirst().startsWith("chat 1 "));
	}

	@Test
	void listenersHearLoggedFailures() {
		List<Guard.Failure> heard = new ArrayList<>();
		Guard.addListener(failure -> {
			throw new IllegalStateException("broken listener");
		});
		Guard.addListener(heard::add);
		assertFalse(Guard.run("listener test", () -> Integer.parseInt("x")));
		Guard.Failure failure = heard.stream().filter(f -> f.what().equals("listener test")).findFirst().orElseThrow();
		assertEquals(1, failure.count());
		assertTrue(failure.error().startsWith("java.lang.NumberFormatException"));
		assertTrue(failure.frame().contains("GuardTest"), failure.frame());
	}

	@Test
	void failuresKeepTheFirstErrorAndCount() {
		for (int i = 0; i < 3; i++) guard.attempt("kinds", () -> Integer.parseInt("y" + System.nanoTime()));
		List<Guard.Failure> all = guard.all();
		assertEquals(1, all.size());
		assertEquals(3, all.getFirst().count());
		assertTrue(all.getFirst().error().contains("NumberFormatException"));
		assertEquals(3, guard.total(), "every error, not just the logged ones");
	}

	@Test
	void errorsAreNotCaught() {
		assertThrows(StackOverflowError.class, () -> guard.attempt("hud", () -> {
			throw new StackOverflowError();
		}));
		assertEquals(List.of(), reports);
	}

	@Test
	void reportsTheFirstAndThenEveryNthPerKind() {
		for (int i = 0; i < 2 * Guard.LOG_EVERY + 5; i++) guard.attempt("tick", GuardTest::boom);
		assertEquals(List.of("tick 1 boom", "tick " + Guard.LOG_EVERY + " boom", "tick " + 2 * Guard.LOG_EVERY + " boom"), reports);
	}

	@Test
	void eachKindIsCountedOnItsOwn() {
		guard.attempt("tick", GuardTest::boom);
		guard.attempt("tick", GuardTest::boom);
		guard.attempt("render", GuardTest::boom);
		// same label, other place in the code
		guard.attempt("tick", () -> {
			throw new IllegalStateException("other");
		});
		assertEquals(List.of("tick 1 boom", "render 1 boom", "tick 1 other"), reports);
	}

	@Test
	void aThrowingListenerDoesntStopTheNextOne() {
		List<String> got = new ArrayList<>();
		List<Consumer<String>> listeners = List.of(value -> {
			throw new IllegalStateException("first");
		}, got::add, value -> {
			throw new IllegalStateException("third");
		}, value -> got.add(value + "!"));
		guard.attemptEach("listener", listeners, "event");
		assertEquals(List.of("event", "event!"), got);
		assertEquals(List.of("listener 1 first", "listener 1 third"), reports);
	}

	@Test
	void getFallsBackWhenItThrows() {
		Object fallback = new Object();
		assertSame(fallback, guard.attemptGet("tab list", () -> {
			throw new IllegalStateException("no tab");
		}, () -> fallback));
		assertEquals("ok", guard.attemptGet("tab list", () -> "ok", () -> {
			throw new AssertionError("fallback built w/o a throw");
		}));
	}

	private static void boom() {
		throw new IllegalStateException("boom");
	}
}
