package dev.jobflow.ingestion;

import java.time.Duration;
import java.util.Objects;

/** Executes one delivery; polling/scheduling is deliberately kept outside this lifecycle port. */
public final class SqsBackfillWorker {
    private final BackfillQueue queue;
    private final BatchProcessor processor;
    private final BatchLifecycle lifecycle;
    private final int maxAttempts;
    private final Duration retryVisibilityTimeout;

    public SqsBackfillWorker(
            BackfillQueue queue,
            BatchProcessor processor,
            BatchLifecycle lifecycle,
            int maxAttempts,
            Duration retryVisibilityTimeout) {
        this.queue = Objects.requireNonNull(queue);
        this.processor = Objects.requireNonNull(processor);
        this.lifecycle = Objects.requireNonNull(lifecycle);
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be positive");
        this.maxAttempts = maxAttempts;
        this.retryVisibilityTimeout = Objects.requireNonNull(retryVisibilityTimeout);
    }

    public void pollOnce() {
        for (BackfillQueue.QueuedBatch batch : queue.receive()) {
            process(batch);
        }
    }

    public void process(BackfillQueue.QueuedBatch batch) {
        try {
            if (!lifecycle.claim(batch.payload())) {
                // Duplicate delivery after a terminal transition is safe to drop.
                queue.acknowledge(batch.receipt());
                return;
            }
        } catch (IllegalArgumentException exception) {
            // A deployment or local reset can leave an already-published message
            // whose database batch was deleted. Drop only that known-stale delivery;
            // unrelated lifecycle failures must still surface for diagnosis.
            if ("GMAIL_BACKFILL_BATCH_NOT_FOUND".equals(exception.getMessage())) {
                queue.acknowledge(batch.receipt());
                return;
            }
            throw exception;
        }
        try {
            processor.process(batch.payload());
            lifecycle.succeeded(batch.payload());
            queue.acknowledge(batch.receipt());
        } catch (RetryableBatchException exception) {
            if (batch.receiveAttempt() >= maxAttempts) {
                lifecycle.deadLettered(batch.payload(), exception.code());
                queue.deadLetter(batch);
            } else {
                lifecycle.retryableFailure(batch.payload(), exception.code());
                queue.retry(batch.receipt(), retryVisibilityTimeout);
            }
        } catch (NonRetryableBatchException exception) {
            lifecycle.failed(batch.payload(), exception.code());
            queue.acknowledge(batch.receipt());
            throw exception;
        }
    }

    @FunctionalInterface
    public interface BatchProcessor {
        void process(BackfillQueue.BatchPayload payload);
    }

    public interface BatchLifecycle {
        boolean claim(BackfillQueue.BatchPayload payload);

        void succeeded(BackfillQueue.BatchPayload payload);

        void retryableFailure(BackfillQueue.BatchPayload payload, String code);

        void failed(BackfillQueue.BatchPayload payload, String code);

        void deadLettered(BackfillQueue.BatchPayload payload, String code);
    }

    public static class RetryableBatchException extends RuntimeException {
        private final String code;

        public RetryableBatchException(String code) {
            super(code);
            this.code = code;
        }

        public String code() { return code; }
    }

    public static class NonRetryableBatchException extends RuntimeException {
        private final String code;

        public NonRetryableBatchException(String code) {
            super(code);
            this.code = code;
        }

        public NonRetryableBatchException(String code, Throwable cause) {
            super(code, cause);
            this.code = code;
        }

        public String code() { return code; }
    }
}
