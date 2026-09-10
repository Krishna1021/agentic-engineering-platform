package io.krishna.agentic.config;

import io.krishna.agentic.workflow.domain.WorkflowEngine;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformProperties.class)
public class PlatformConfiguration {
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    WorkflowEngine workflowEngine(PlatformProperties properties) {
        return new WorkflowEngine(properties.maxAttempts(), properties.maxRepairs());
    }

    @Bean(destroyMethod = "shutdownNow")
    ExecutorService taskExecutor(PlatformProperties properties) {
        return Executors.newFixedThreadPool(properties.parallelism());
    }
}
