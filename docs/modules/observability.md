# Sentry observability module

Id `observability`; package `dev.modularforge.observability` contains its verification endpoint; default off; toggle `app.modules.observability.enabled`.

The Sentry starter is activated through configuration and classpath auto-configuration. Default PII sending is disabled.

## Remove

1. Set the toggle to `false` and confirm the verification route is absent and no events leave the process.
2. Delete `SentryVerificationController` and its tests.
3. Remove `sentry-spring-boot-4`, `sentry-logback`, `sentry-async-profiler`, `sentry.version`, and all `sentry.*`/`SENTRY_*` settings.
4. Keep `OpenApiConfig` only if the separate API docs module remains; otherwise delete the whole observability package.
5. Remove the catalog entry and run `./mvnw clean verify`.
