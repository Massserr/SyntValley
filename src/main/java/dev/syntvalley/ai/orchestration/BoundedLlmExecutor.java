package dev.syntvalley.ai.orchestration;

import dev.syntvalley.ai.backend.LlmBackend;
import dev.syntvalley.ai.backend.LlmRequest;
import dev.syntvalley.ai.backend.LlmResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * The only place LLM work runs: a fixed number of worker threads over a bounded queue, with a bounded
 * completion inbox the server drains with a per-tick cap. The server thread never blocks here — submit
 * returns an immediate typed result and completions are picked up later. A generation counter makes
 * shutdown safe: results finishing after {@link #shutdown()} belong to an old generation and are dropped,
 * so a late completion can never leak into a stopped or freshly started server.
 */
public final class BoundedLlmExecutor {
    private final LlmBackend backend;
    private final CircuitBreaker circuitBreaker;
    private final LongSupplier clockMillis;
    private final BlockingQueue<Job> queue;
    private final ConcurrentLinkedQueue<Completion> inbox = new ConcurrentLinkedQueue<>();
    private final int inboxCapacity;
    private final AtomicLong generation = new AtomicLong(1);
    private final AtomicLong completedCount = new AtomicLong();
    private final AtomicLong droppedCount = new AtomicLong();
    private final List<Thread> workers = new ArrayList<>();
    private volatile boolean running = true;

    private record Job(LlmRequest request, long generation) {
    }

    private record Completion(LlmResult result, long generation) {
    }

    public BoundedLlmExecutor(
            LlmBackend backend, int workerCount, int queueCapacity, int inboxCapacity,
            CircuitBreaker circuitBreaker, LongSupplier clockMillis) {
        this.backend = Objects.requireNonNull(backend, "backend");
        this.circuitBreaker = Objects.requireNonNull(circuitBreaker, "circuitBreaker");
        this.clockMillis = Objects.requireNonNull(clockMillis, "clockMillis");
        if (workerCount < 1 || queueCapacity < 1 || inboxCapacity < 1) {
            throw new IllegalArgumentException("workerCount, queueCapacity and inboxCapacity must be positive");
        }
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.inboxCapacity = inboxCapacity;
        for (int index = 0; index < workerCount; index++) {
            Thread worker = new Thread(this::workLoop, "syntvalley-llm-worker-" + index);
            worker.setDaemon(true);
            workers.add(worker);
            worker.start();
        }
    }

    /** Non-blocking submit with an immediate typed outcome; called from the server thread. */
    public SubmitResult submit(LlmRequest request) {
        Objects.requireNonNull(request, "request");
        if (!running) {
            return new SubmitResult.Rejected(SubmitResult.Rejection.SHUTDOWN);
        }
        synchronized (circuitBreaker) {
            if (!circuitBreaker.allow(clockMillis.getAsLong())) {
                return new SubmitResult.Rejected(SubmitResult.Rejection.CIRCUIT_OPEN);
            }
        }
        if (!queue.offer(new Job(request, generation.get()))) {
            return new SubmitResult.Rejected(SubmitResult.Rejection.QUEUE_FULL);
        }
        return new SubmitResult.Accepted(request.id());
    }

    /** Drains up to {@code max} completions of the current generation; called from the server thread. */
    public List<LlmResult> drainCompleted(int max) {
        List<LlmResult> drained = new ArrayList<>(Math.min(max, 8));
        long current = generation.get();
        Completion completion;
        while (drained.size() < max && (completion = inbox.poll()) != null) {
            if (completion.generation() == current) {
                drained.add(completion.result());
            } else {
                droppedCount.incrementAndGet();
            }
        }
        return List.copyOf(drained);
    }

    /**
     * Stops accepting work, drops the queue, and invalidates every in-flight generation. Workers stuck in
     * a hung backend call are abandoned (daemon threads); their late results are ignored by generation.
     */
    public void shutdown() {
        running = false;
        generation.incrementAndGet();
        queue.clear();
        inbox.clear();
        for (Thread worker : workers) {
            worker.interrupt();
        }
    }

    public int queuedCount() {
        return queue.size();
    }

    public int inboxCount() {
        return inbox.size();
    }

    public long completedCount() {
        return completedCount.get();
    }

    public long droppedCount() {
        return droppedCount.get();
    }

    public CircuitBreaker.State circuitState() {
        synchronized (circuitBreaker) {
            return circuitBreaker.state();
        }
    }

    private void workLoop() {
        while (running) {
            Job job;
            try {
                job = queue.poll(50, TimeUnit.MILLISECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
            if (job == null) {
                continue;
            }
            LlmResult result;
            try {
                result = backend.generate(job.request());
            } catch (RuntimeException unexpected) {
                result = new LlmResult.Failure(
                        job.request().id(), dev.syntvalley.ai.backend.LlmErrorKind.MALFORMED_RESPONSE,
                        "backend threw: " + unexpected.getClass().getSimpleName());
            }
            synchronized (circuitBreaker) {
                if (result instanceof LlmResult.Success) {
                    circuitBreaker.onSuccess();
                } else {
                    circuitBreaker.onFailure(clockMillis.getAsLong());
                }
            }
            if (job.generation() == generation.get() && inbox.size() < inboxCapacity) {
                inbox.add(new Completion(result, job.generation()));
                completedCount.incrementAndGet();
            } else {
                droppedCount.incrementAndGet();
            }
        }
    }
}
