# Scope and limitations

This increment delivers the platform source implementation. It is not a verified
production release or a completed URL-shortener assessment submission.

1. One active orchestrator instance is supported. Distributed leases are not implemented.
2. The lifecycle graph is governed and expandable for repair; model-authored arbitrary DAGs are not implemented.
3. Replanning conservatively invalidates the whole revision. Selective reuse is future work.
4. The demo provider generates a fixed Java scaffold. Live model generation remains unverified.
5. Build execution supports offline Gradle/Java 17. Dependency-rich builds need prepared worker dependencies.
6. Repository input is a bounded local text-source snapshot. Git URL cloning, archives, binary assets and deletes are not implemented.
7. Safe-stop prevents new work and approval. In-flight external work is bounded by its own deadline, not instantly cancelled.
8. Recovery starts a new revision; uncertain operations are not automatically replayed.
9. Generated tests can share model mistakes. Human acceptance review and independent application tests remain essential.
10. Completion means reviewed workspace output. Publishing and deployment are separate future actions.
11. Basic role authentication is for a trusted local/team prototype, not a multi-tenant deployment.
12. Build/test execution and container behavior must be verified when the user authorizes builds.

Recommended next increment after verification: strengthen artifact storage and
worker dependency provisioning, then add the requested application's greenfield,
brownfield and ambiguous assessment scenarios without embedding that domain in the engine.
