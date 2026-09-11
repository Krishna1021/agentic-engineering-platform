package io.krishna.agentic.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.krishna.agentic.workflow.domain.Workflow;
import io.krishna.agentic.workflow.persistence.WorkflowStore;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Tag("postgres")
@Testcontainers
@SpringBootTest
@ActiveProfiles("test")
class PostgresPersistenceTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:17-alpine");
    @Autowired WorkflowStore store;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
    }

    @Test
    void migrationSnapshotRoundTripAndRejectedTransaction() {
        Workflow original = store.create(Workflow.create("Build an application with documented tests", null, "operator", Instant.now()));
        assertThat(store.get(original.id())).isEqualTo(original);
        assertThatThrownBy(() -> store.change(original.id(), "INVALID", "operator", "reject", workflow -> {
            throw new IllegalArgumentException("Rejected before commit");
        })).isInstanceOf(IllegalArgumentException.class);
        assertThat(store.events(original.id(), 0)).hasSize(1);
    }
}
