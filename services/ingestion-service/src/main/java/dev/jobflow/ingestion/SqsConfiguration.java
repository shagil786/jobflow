package dev.jobflow.ingestion;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(prefix = "jobflow.sqs", name = "enabled", havingValue = "true")
public class SqsConfiguration {
    @Bean(destroyMethod = "close")
    SqsClient sqsClient(
            @Value("${AWS_REGION:ap-south-1}") String region,
            @Value("${JOBFLOW_SQS_ENDPOINT:}") String endpoint) {
        var builder = SqsClient.builder().region(Region.of(region));
        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }

    @Bean
    BackfillQueue backfillQueue(
            SqsClient client,
            @Value("${JOBFLOW_SQS_BACKFILL_QUEUE_URL:}") String queueUrl,
            @Value("${JOBFLOW_SQS_BACKFILL_DLQ_URL:}") String deadLetterQueueUrl,
            @Value("${JOBFLOW_SQS_VISIBILITY_TIMEOUT_SECONDS:900}") long visibilityTimeoutSeconds,
            @Value("${JOBFLOW_SQS_MAX_MESSAGES:1}") int maxMessages) {
        if (queueUrl.isBlank()) throw new IllegalStateException("JOBFLOW_SQS_BACKFILL_QUEUE_URL is required when SQS is enabled");
        return new SqsBackfillQueue(client, queueUrl, deadLetterQueueUrl, Duration.ofSeconds(visibilityTimeoutSeconds), maxMessages);
    }
}
