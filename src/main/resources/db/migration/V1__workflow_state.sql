CREATE TABLE workflows (
    id UUID PRIMARY KEY,
    status VARCHAR(40) NOT NULL,
    snapshot TEXT NOT NULL
);

CREATE INDEX workflows_status_idx ON workflows(status);

CREATE TABLE audit_events (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflows(id),
    revision INTEGER NOT NULL CHECK (revision > 0),
    event_type VARCHAR(60) NOT NULL,
    actor VARCHAR(200) NOT NULL,
    detail VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    snapshot TEXT NOT NULL
);

CREATE INDEX audit_workflow_idx ON audit_events(workflow_id, sequence);
