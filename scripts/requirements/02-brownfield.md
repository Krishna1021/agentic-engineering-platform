Improve the supplied existing URL-shortener repository, preserving its public creation and redirect contracts. This is a brownfield task: reason from the existing implementation, do not replace it with an unrelated scaffold. Use Java 17 and plain Groovy Gradle.

First identify impacted controller/service/persistence/schema/test/documentation components, current data flow and compatibility risks. Provide requirement-specific steps and acceptance criteria.

Implement or correct UTC daily redirect analytics with total and daily counts at GET /api/links/{shortCode}/analytics. Use atomic increments and no visitor-identifying data. Unknown codes return 404 and do not count. Analytics failures must not block valid redirects; document best-effort counting. Preserve existing data using an additive Flyway migration, without editing previously applied migrations.

Fix non-atomic code collision handling so existing links cannot be overwritten. Test collision then success, exhausted retries and concurrent collisions deterministically. Reject hostless HTTP URLs and distinguish infrastructure failures from client conflicts. Preserve existing valid behavior and API compatibility.

Ensure JUnit Platform is enabled, tests actually run and test database settings are isolated. Add regression tests for existing endpoints plus new analytics, concurrency and migration behavior. Separate actual PostgreSQL validation from offline/default tests; do not claim evidence that was not executed.

Produce a changed-component rationale, baseline-vs-change behavior, compatibility assessment, migration/rollback limitations, test evidence, risks and assumptions under docs/. Do not deploy, publish, remove unrelated functionality or silently rebuild the application from scratch.
