package dev.jobflow.ingestion;

import java.util.concurrent.Executor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
class GmailClassificationAsyncConfiguration {
    @Bean(name = "gmailClassificationExecutor")
    Executor gmailClassificationExecutor(
            @Value("${JOBFLOW_GMAIL_CLASSIFICATION_THREADS:4}") int threads,
            @Value("${JOBFLOW_GMAIL_CLASSIFICATION_QUEUE_CAPACITY:1000}") int queueCapacity) {
        if (threads < 1) throw new IllegalArgumentException("JOBFLOW_GMAIL_CLASSIFICATION_THREADS must be positive");
        if (queueCapacity < 1) throw new IllegalArgumentException("JOBFLOW_GMAIL_CLASSIFICATION_QUEUE_CAPACITY must be positive");
        var executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(threads);
        executor.setMaxPoolSize(threads);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("gmail-classifier-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
