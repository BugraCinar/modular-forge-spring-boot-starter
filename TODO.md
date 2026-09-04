# Roadmap

The starter is intentionally conservative. These are useful next steps, not hidden setup requirements.

- Add Flyway or Liquibase migrations before the first production deployment.
- Add Testcontainers coverage for every database selected by the project using this starter.
- Replace the SMTP gateway with an outbox-backed notification adapter when guaranteed delivery is required.
- Export audit events to immutable external storage for regulated workloads.
- Add WebAuthn/passkeys as a separate `SecondFactorGateway` implementation.
- Add container and deployment examples after the target platform is known.
