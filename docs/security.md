# Security and execution policy

- Operate behind TLS outside localhost; Basic credentials must not travel over plaintext networks.
- The API is stateless and intended for programmatic JSON clients. No browser session UI is provided.
- Operator and approver are separate identities. Submission cannot approve its own outcome.
- Requirements, repository contents and model outputs are untrusted engineering inputs.
- Model output is structured and bounded. Only analysis can ask clarification questions.
- Test generation writes under src/test; documentation writes Markdown under docs.
- Proposed paths cannot escape a revision, use absolute paths, or differ only by case.
- Supported source extensions are Java, Gradle, Markdown, JSON, YAML, properties and SQL.
- Symlinks are rejected. Source roots and workspace parents must be administrator-controlled.
- Files are limited to 100 KB each, 300 files, and 2 MB total source/proposal content.
- Generated code runs only through the fixed Docker/Gradle capability, never a model shell command.
- Workers receive no API credentials, Docker socket, network, or extra capabilities.
- Build logs are bounded; model responses are bounded while streaming.
- Approval checks the validated source fingerprint for the current revision.

## Important boundaries

The Docker daemon is privileged infrastructure. The host/API account and configured
repository/workspace roots are trusted. Container isolation is not a claim of
protection against kernel or Docker vulnerabilities. Use a dedicated worker host
before accepting hostile public inputs.

Only supported text files are copied. Binary resources, wrappers, hidden metadata,
and other ecosystems may need a richer repository adapter. Approved codebases must
be free of embedded secrets before model submission: source context can be sent to
the configured provider. There is no general secret-scanning/DLP implementation.

Audit writes are append-only through application code. The database owner can still
modify records; this is not a cryptographically immutable compliance ledger. Use
separate migration/runtime database roles and immutable backups for stronger controls.

Authentication is shared operator/approver access for one trusted team, not tenant
isolation. Production OIDC, per-project permissions, quotas, dependency provenance,
network egress policy and retention enforcement are future increments.
