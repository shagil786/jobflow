package dev.jobflow.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

class SqsBackfillQueueTest {
    private static final String QUEUE_URL = "http://localhost:4566/000000000000/jobflow-backfill";

    @Test
    void publishesIdentifierOnlyVersionedPayload() {
        SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
        when(client.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("message-1").build());
        SqsBackfillQueue queue = new SqsBackfillQueue(client, QUEUE_URL, Duration.ofSeconds(30), 10);
        BackfillQueue.BatchPayload payload = payload();

        queue.publish(payload);

        var request = org.mockito.ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(request.getValue().messageBody())
                .contains("\"schemaVersion\":\"gmail-backfill-batch.v1\"")
                .contains(payload.batchId().toString())
                .contains(payload.connectionId().toString())
                .doesNotContain("refreshToken")
                .doesNotContain("rawBody");
    }

    @Test
    void receivesAndDeserializesQueuedBatchWithReceipt() {
        SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
        SqsBackfillQueue queue = new SqsBackfillQueue(client, QUEUE_URL, Duration.ofSeconds(30), 10);
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(Message.builder()
                                .messageId("message-1")
                                .receiptHandle("receipt-1")
                                .body(queue.serialize(payload()))
                                .build())
                        .build());

        List<BackfillQueue.QueuedBatch> received = queue.receive();

        assertThat(received).singleElement().satisfies(item -> {
            assertThat(item.receipt()).isEqualTo(new BackfillQueue.Receipt("message-1", "receipt-1"));
            assertThat(item.payload()).isEqualTo(payload());
        });
        var request = org.mockito.ArgumentCaptor.forClass(ReceiveMessageRequest.class);
        verify(client).receiveMessage(request.capture());
        assertThat(request.getValue().maxNumberOfMessages()).isEqualTo(10);
        assertThat(request.getValue().visibilityTimeout()).isEqualTo(30);
        assertThat(request.getValue().messageSystemAttributeNames())
                .contains(software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
    }

    @Test
    void acknowledgesAndRetriesUsingReceiptHandle() {
        SqsClient client = org.mockito.Mockito.mock(SqsClient.class);
        SqsBackfillQueue queue = new SqsBackfillQueue(client, QUEUE_URL, Duration.ofSeconds(30), 10);
        BackfillQueue.Receipt receipt = new BackfillQueue.Receipt("message-1", "receipt-1");

        queue.acknowledge(receipt);
        queue.retry(receipt, Duration.ofSeconds(45));

        var delete = org.mockito.ArgumentCaptor.forClass(DeleteMessageRequest.class);
        verify(client).deleteMessage(delete.capture());
        assertThat(delete.getValue().receiptHandle()).isEqualTo("receipt-1");
        var change = org.mockito.ArgumentCaptor.forClass(software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest.class);
        verify(client).changeMessageVisibility(change.capture());
        assertThat(change.getValue().receiptHandle()).isEqualTo("receipt-1");
        assertThat(change.getValue().visibilityTimeout()).isEqualTo(45);
    }

    private static BackfillQueue.BatchPayload payload() {
        return new BackfillQueue.BatchPayload(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                UUID.fromString("00000000-0000-0000-0000-000000000003"),
                "tenant-1",
                "user-1",
                1,
                Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-21T00:00:00Z"),
                "correlation-1",
                "attempt-1");
    }
}
