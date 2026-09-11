package io.krishna.agentic.workflow.api;

import io.krishna.agentic.workflow.application.WorkflowService;
import io.krishna.agentic.workflow.persistence.AuditEvent;
import io.krishna.agentic.workflow.persistence.WorkflowStore;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflows")
public class WorkflowController {
    private final WorkflowService service;
    private final WorkflowStore store;
    private final WorkflowMapper mapper;

    public WorkflowController(WorkflowService service, WorkflowStore store, WorkflowMapper mapper) {
        this.service = service;
        this.store = store;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<WorkflowResponse> create(@Valid @RequestBody WorkflowRequests.Create request, Principal actor) {
        var workflow = service.submit(request.requirements(), request.repository(), actor.getName());
        return ResponseEntity.created(URI.create("/api/v1/workflows/" + workflow.id())).body(mapper.toResponse(workflow));
    }

    @GetMapping("/{id}")
    public WorkflowResponse get(@PathVariable UUID id) {
        return mapper.toResponse(store.get(id));
    }

    @GetMapping("/{id}/events")
    public List<AuditEvent> events(@PathVariable UUID id, @RequestParam(defaultValue = "0") long after) {
        if (after < 0) {
            throw new IllegalArgumentException("Event cursor must be nonnegative");
        }
        return store.events(id, after);
    }

    @GetMapping("/{id}/summary")
    public EngineeringSummary summary(@PathVariable UUID id) {
        return mapper.toSummary(store.get(id));
    }

    @PostMapping("/{id}/revisions")
    public WorkflowResponse revise(@PathVariable UUID id, @Valid @RequestBody WorkflowRequests.Revise request, Principal actor) {
        return mapper.toResponse(service.revise(id, request.revision(), request.requirements(), actor.getName()));
    }

    @PostMapping("/{id}/clarifications")
    public WorkflowResponse clarify(@PathVariable UUID id, @Valid @RequestBody WorkflowRequests.Clarify request, Principal actor) {
        return mapper.toResponse(service.clarify(id, request.revision(), request.answer(), actor.getName()));
    }

    @PostMapping("/{id}/approvals")
    public WorkflowResponse approve(@PathVariable UUID id, @Valid @RequestBody WorkflowRequests.Decision request, Principal actor) {
        return mapper.toResponse(service.approve(id, request.revision(), actor.getName(), request.reason()));
    }

    @PostMapping("/{id}/stop")
    public WorkflowResponse stop(@PathVariable UUID id, @Valid @RequestBody WorkflowRequests.Decision request, Principal actor) {
        return mapper.toResponse(service.stop(id, request.revision(), actor.getName(), request.reason()));
    }

    @PostMapping("/{id}/recover")
    public WorkflowResponse recover(@PathVariable UUID id, @Valid @RequestBody WorkflowRequests.Recover request, Principal actor) {
        return mapper.toResponse(service.recover(id, request.revision(), actor.getName()));
    }
}
