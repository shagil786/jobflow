package dev.jobflow.ingestion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public final class SqsBackfillQueue implements BackfillQueue {
    private final SqsClient client;
    private final String queueUrl;
    private final String deadLetterQueueUrl;
    private final Duration visibilityTimeout;
    private final int maxMessages;
    private final ObjectMapper mapper;

    public SqsBackfillQueue(SqsClient client, String queueUrl, Duration visibilityTimeout, int maxMessages) {
        this(client, queueUrl, null, visibilityTimeout, maxMessages);
    }

    public SqsBackfillQueue(
            SqsClient client,
            String queueUrl,
            String deadLetterQueueUrl,
            Duration visibilityTimeout,
            int maxMessages) {
        this.client = Objects.requireNonNull(client);
        this.queueUrl = requireText(queueUrl, "queueUrl");
        this.deadLetterQueueUrl = deadLetterQueueUrl == null || deadLetterQueueUrl.isBlank() ? null : deadLetterQueueUrl;
        this.visibilityTimeout = requireDuration(visibilityTimeout, "visibilityTimeout");
        if (maxMessages < 1 || maxMessages > 10) {
            throw new IllegalArgumentException("maxMessages must be between 1 and 10");
        }
        this.maxMessages = maxMessages;
        this.mapper = new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public void publish(BatchPayload payload) {
        client.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(serialize(payload)).build());
    }

    @Override
    public List<QueuedBatch> receive() {
        var response = client.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(queueUrl)
                .maxNumberOfMessages(maxMessages)
                .visibilityTimeout(Math.toIntExact(visibilityTimeout.toSeconds()))
                .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                .waitTimeSeconds(10)
                .build());
        return response.messages().stream().map(this::toQueuedBatch).toList();
    }

    @Override
    public void acknowledge(Receipt receipt) {
        client.deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(requireText(receipt.receiptHandle(), "receiptHandle"))
                .build());
    }

    @Override
    public void retry(Receipt receipt, Duration retryVisibilityTimeout) {
        Duration timeout = requireDuration(retryVisibilityTimeout, "retryVisibilityTimeout");
        client.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                .queueUrl(queueUrl)
                .receiptHandle(requireText(receipt.receiptHandle(), "receiptHandle"))
                .visibilityTimeout(Math.toIntExact(timeout.toSeconds()))
                .build());
    }

    @Override
    public void deadLetter(QueuedBatch batch) {
        if (deadLetterQueueUrl == null) {
            throw new IllegalStateException("dead-letter queue URL is not configured");
        }
        client.sendMessage(SendMessageRequest.builder()
                .queueUrl(deadLetterQueueUrl)
                .messageBody(serialize(batch.payload()))
                .build());
        acknowledge(batch.receipt());
    }

    String serialize(BatchPayload payload) {
        try {
            return mapper.writeValueAsString(new VersionedBatch("gmail-backfill-batch.v1", payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize Gmail backfill batch", exception);
        }
    }

    private QueuedBatch toQueuedBatch(Message message) {
        try {
            VersionedBatch envelope = mapper.readValue(message.body(), VersionedBatch.class);
            if (!"gmail-backfill-batch.v1".equals(envelope.schemaVersion())) {
                throw new IllegalArgumentException("Unsupported Gmail backfill schema version");
            }
            int receiveAttempt = parseReceiveAttempt(message);
            return new QueuedBatch(
                    envelope.payload(),
                    new Receipt(message.messageId(), message.receiptHandle()),
                    receiveAttempt);
        } catch (JsonProcessingException | RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Gmail backfill queue message", exception);
        }
    }

    private static int parseReceiveAttempt(Message message) {
        String count = message.attributesAsStrings().get("ApproximateReceiveCount");
        if (count == null) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(count));
        } catch (NumberFormatException exception) {
            return 1;
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }

    private static Duration requireDuration(Duration value, String name) {
        if (value == null || value.isNegative() || value.isZero()) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    static record VersionedBatch(String schemaVersion, BatchPayload payload) {}
}
