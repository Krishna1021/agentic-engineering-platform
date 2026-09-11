# Scope and limitations

The platform build issue is resolved, and the assessment scenario catalog and Reviewer Guide are included. Platform test verification is distinct from generated-application acceptance. Successful end-to-end URL-shortener submission and production readiness are not claimed.

1. One active orchestrator instance is supported. Distributed leases are not implemented.
2. The lifecycle graph is governed and expandable for repair; model-authored arbitrary DAGs are not implemented.
3. Replanning conservatively invalidates the whole revision. Selective reuse is future work.
4. The demo provider generates a fixed Java scaffold. Historical live generation produced artifacts, but the recorded runs failed before application acceptance.
5. Build execution supports offline Gradle/Java 17. Dependency-rich builds use the prepared worker cache described in setup.md; uncached dependencies still fail offline.
6. Repository input is a bounded local text-source snapshot. Git URL cloning, archives, binary assets and deletes are not implemented.
7. Safe-stop prevents new work and approval. In-flight external work is bounded by its own deadline, not instantly cancelled.
8. Recovery starts a new revision; uncertain operations are not automatically replayed.
9. Generated tests can share model mistakes. Human acceptance review and independent application tests remain essential.
10. Completion means reviewed workspace output. Publishing and deployment are separate future actions.
11. Basic role authentication is for a trusted local/team prototype, not a multi-tenant deployment.
12. Current platform verification is recorded in testing.md. Generated application and exact worker-image validation remain separate checks.

Remaining assessment work: complete successful model-driven generation, actual application tests and human review for each cataloged scenario. Distributed leases and immutable artifact storage remain future production work.
