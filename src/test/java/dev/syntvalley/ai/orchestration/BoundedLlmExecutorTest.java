package dev.syntvalley.ai.orchestration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.syntvalley.ai.backend.LlmBackend;
import dev.syntvalley.ai.backend.LlmErrorKind;
import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResponse;
import dev.syntvalley.ai.backend.LlmResult;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class BoundedLlmExecutorTest {
    private static final long DEADLINE_MILLIS = 5_000;

    private static LlmBackend instantSuccess() {
        return request -> new LlmResult.Success(new LlmResponse(request.id(), "ok", 1));
    }

    private static LlmBackend alwaysFailing() {
        return request -> new LlmResult.Failure(request.id(), LlmErrorKind.CONNECT_FAILED, "refused");
    }

    /** Blocks every generate call until the latch opens, then succeeds. */
    private static LlmBackend blockedBy(CountDownLatch latch) {
        return request -> {
            try {
                if (!latch.await(DEADLINE_MILLIS, TimeUnit.MILLISECONDS)) {
                    return new LlmResult.Failure(request.id(), LlmErrorKind.TIMEOUT, "latch never opened");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return new LlmResult.Failure(request.id(), LlmErrorKind.CANCELLED, "interrupted");
            }
            return new LlmResult.Success(new LlmResponse(request.id(), "ok", 1));
        };
    }

    private static List<LlmResult> awaitDrain(BoundedLlmExecutor executor, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        java.util.ArrayList<LlmResult> collected = new java.util.ArrayList<>();
        while (collected.size() < expected && System.currentTimeMillis() < deadline) {
            collected.addAll(executor.drainCompleted(expected - collected.size()));
            if (collected.size() < expected) {
                Thread.sleep(5);
            }
        }
        return collected;
    }

    private static void awaitQueueEmpty(BoundedLlmExecutor executor) throws InterruptedException {
        long deadline = System.currentTimeMillis() + DEADLINE_MILLIS;
        while (executor.queuedCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(5);
        }
        assertEquals(0, executor.queuedCount(), "worker should have taken the queued job");
    }

    @Test
    void completesAndDrainsWithPerTickCap() throws InterruptedException {
        BoundedLlmExecutor executor = new BoundedLlmExecutor(
                instantSuccess(), 1, 4, 8, new CircuitBreaker(3, 1000), System::currentTimeMillis);
        try {
            LlmRequest request = LlmRequest.of("diagnostic", "ping");
            assertInstanceOf(SubmitResult.Accepted.class, executor.submit(request));

            List<LlmResult> drained = awaitDrain(executor, 1);
            assertEquals(1, drained.size());
            LlmResult.Success success = assertInstanceOf(LlmResult.Success.class, drained.get(0));
            assertEquals(request.id(), success.requestId());
            assertEquals(1, executor.completedCount());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void overloadedQueueRejectsImmediately() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        BoundedLlmExecutor executor = new BoundedLlmExecutor(
                blockedBy(latch), 1, 1, 8, new CircuitBreaker(10, 1000), System::currentTimeMillis);
        try {
            assertInstanceOf(SubmitResult.Accepted.class, executor.submit(LlmRequest.of("t", "runs")));
            awaitQueueEmpty(executor); // the single worker is now stuck inside the backend
            assertInstanceOf(SubmitResult.Accepted.class, executor.submit(LlmRequest.of("t", "queued")));

            SubmitResult third = executor.submit(LlmRequest.of("t", "overflow"));
            SubmitResult.Rejected rejected = assertInstanceOf(SubmitResult.Rejected.class, third);
            assertEquals(SubmitResult.Rejection.QUEUE_FULL, rejected.reason());
        } finally {
            latch.countDown();
            executor.shutdown();
        }
    }

    @Test
    void repeatedFailuresOpenTheCircuit() throws InterruptedException {
        BoundedLlmExecutor executor = new BoundedLlmExecutor(
                alwaysFailing(), 1, 4, 8, new CircuitBreaker(2, 3_600_000), System::currentTimeMillis);
        try {
            executor.submit(LlmRequest.of("t", "one"));
            executor.submit(LlmRequest.of("t", "two"));
            List<LlmResult> failures = awaitDrain(executor, 2);
            assertEquals(2, failures.size());
            assertEquals(CircuitBreaker.State.OPEN, executor.circuitState());

            SubmitResult next = executor.submit(LlmRequest.of("t", "three"));
            SubmitResult.Rejected rejected = assertInstanceOf(SubmitResult.Rejected.class, next);
            assertEquals(SubmitResult.Rejection.CIRCUIT_OPEN, rejected.reason());
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void shutdownRejectsNewWorkAndIgnoresLateCompletions() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        BoundedLlmExecutor executor = new BoundedLlmExecutor(
                blockedBy(latch), 1, 4, 8, new CircuitBreaker(10, 1000), System::currentTimeMillis);
        try {
            executor.submit(LlmRequest.of("t", "inflight"));
            awaitQueueEmpty(executor); // the worker is inside the hung backend call

            executor.shutdown();
            SubmitResult afterShutdown = executor.submit(LlmRequest.of("t", "late"));
            SubmitResult.Rejected rejected = assertInstanceOf(SubmitResult.Rejected.class, afterShutdown);
            assertEquals(SubmitResult.Rejection.SHUTDOWN, rejected.reason());

            latch.countDown(); // the hung call now finishes — its result must never surface
            long deadline = System.currentTimeMillis() + 500;
            while (System.currentTimeMillis() < deadline && executor.droppedCount() == 0) {
                Thread.sleep(5);
            }
            assertTrue(executor.drainCompleted(8).isEmpty(), "late completions are dropped, not delivered");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void hungBackendNeverBlocksSubmitOrDrain() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        BoundedLlmExecutor executor = new BoundedLlmExecutor(
                blockedBy(latch), 1, 2, 8, new CircuitBreaker(10, 1000), System::currentTimeMillis);
        try {
            executor.submit(LlmRequest.of("t", "hang"));
            long start = System.currentTimeMillis();
            executor.submit(LlmRequest.of("t", "queued"));
            assertTrue(executor.drainCompleted(8).isEmpty());
            assertTrue(System.currentTimeMillis() - start < 1_000,
                    "submit and drain return immediately while the backend hangs");
        } finally {
            latch.countDown();
            executor.shutdown();
        }
    }
}
