# Audit module

Id `audit`; package `dev.modularforge.audit`; default on; toggle `app.modules.audit.enabled`.

It owns admin audit APIs, activity/auth-error/sensitive-access services, the MVC interceptor, security filter extension, cleanup job, and these tables: `admin_activity_log`, `user_activity_log`, `authentication_error_logs`, `sensitive_endpoint_access_logs`.

Core consumers use `shared.audit` ports. When disabled, no-op port beans keep business workflows running and the audit filter/interceptor/controllers/jobs are absent.

## Remove

1. Set the toggle to `false` and verify audit routes disappear.
2. Export records required by retention or incident policy.
3. Delete `src/main/java/dev/modularforge/audit` and audit tests.
4. Drop the four owned tables only after the retention decision.
5. Remove `app.security.log-auth-errors`, `app.security.log-sensitive-access`, alert, and retention settings.
6. Keep the `shared.audit` ports and disabled fallbacks while callers use them. Remove both only after those call sites are intentionally deleted.
7. Remove the catalog entry and run `./mvnw clean verify`.
