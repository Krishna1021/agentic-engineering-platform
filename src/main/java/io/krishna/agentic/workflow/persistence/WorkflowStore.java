package io.krishna.agentic.workflow.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.krishna.agentic.workflow.domain.Workflow;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WorkflowStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    public WorkflowStore(JdbcTemplate jdbc, ObjectMapper mapper, Clock clock) {
        this.jdbc = jdbc;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public Workflow create(Workflow workflow) {
        jdbc.update("INSERT INTO workflows (id, status, snapshot) VALUES (?, ?, ?)",
                workflow.id(), workflow.status().name(), encode(workflow));
        audit(workflow, "CREATED", workflow.createdBy(), "Requirements submitted");
        return workflow;
    }

    public Workflow get(UUID id) {
        return load(id, false);
    }

    @Transactional
    public Workflow change(UUID id, String type, String actor, String detail, UnaryOperator<Workflow> change) {
        Workflow current = load(id, true);
        Workflow updated = change.apply(current);
        if (updated.equals(current)) {
            return current;
        }
        jdbc.update("UPDATE workflows SET status = ?, snapshot = ? WHERE id = ?",
                updated.status().name(), encode(updated), id);
        audit(updated, type, actor, detail);
        return updated;
    }

    public List<UUID> runnableIds() {
        return jdbc.query("SELECT id FROM workflows WHERE status IN ('QUEUED', 'RUNNING') ORDER BY id LIMIT 100",
                (row, index) -> row.getObject("id", UUID.class));
    }

    public List<AuditEvent> events(UUID id, long after) {
        get(id);
        return jdbc.query("SELECT * FROM audit_events WHERE workflow_id = ? AND sequence > ? ORDER BY sequence LIMIT 200",
                (row, index) -> new AuditEvent(row.getLong("sequence"), id, row.getInt("revision"),
                        row.getString("event_type"), row.getString("actor"), row.getString("detail"),
                        row.getTimestamp("occurred_at").toInstant()), id, after);
    }

    private Workflow load(UUID id, boolean lock) {
        List<String> rows = jdbc.query("SELECT snapshot FROM workflows WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                (row, index) -> row.getString(1), id);
        if (rows.isEmpty()) {
            throw new WorkflowNotFoundException(id);
        }
        try {
            return mapper.readValue(rows.get(0), Workflow.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored workflow cannot be decoded", exception);
        }
    }

    private void audit(Workflow workflow, String type, String actor, String detail) {
        jdbc.update("""
                INSERT INTO audit_events (workflow_id, revision, event_type, actor, detail, occurred_at, snapshot)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, workflow.id(), workflow.revision(), type, actor, detail,
                Timestamp.from(clock.instant()), encode(workflow));
    }

    private String encode(Workflow workflow) {
        try {
            return mapper.writeValueAsString(workflow);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Workflow cannot be encoded", exception);
        }
    }
}
