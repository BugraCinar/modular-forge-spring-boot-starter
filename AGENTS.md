# Repository guide for coding agents

Read `docs/MODULAR_PLAN.md` and the target file under `docs/modules/` before changing a feature module.

## Architecture rules

- Production code lives below `dev.modularforge`.
- A feature owns its controllers, services, DTOs, persistence, and configuration inside one top-level package.
- Optional modules must not be imported by another feature. Put the smallest necessary contract in `shared`, `auth`, or `security`, then inject that contract.
- Adapters implement ports; core code never injects an adapter class. R2 implements `ProfileImageStorage`, SMTP implements `NotificationGateway`, audit implements the audit ports, and TOTP implements `SecondFactorGateway`.
- Every optional Spring bean needs a module condition. A disabled module must remove its HTTP routes and scheduled jobs.
- Do not place optional-module fields in `User` or `Admin`. Use a sidecar entity owned by the module.
- Keep `GET /api/v1/modules`, `docs/MODULES.md`, the module guide, `.env.template`, and `ModuleCatalogService` synchronized.

## Safe change sequence

1. State the module, its dependencies, owned tables/routes/configuration, and its public port.
2. Make the change inside the module package. If another package must know a concrete class, stop and introduce a port.
3. Add a disabled-module test and an ArchUnit boundary rule when a new removable boundary is introduced.
4. Update the module-specific removal guide, including schema migration and dependency cleanup.
5. Run `./mvnw clean verify`. Run each affected database profile and `-Psecurity-audit` for dependency changes.

## Security conventions

- Never log credentials, access tokens, refresh tokens, reset tokens, verification tokens, TOTP secrets, CAPTCHA responses, or encryption keys.
- Persist only keyed token hashes and short masked previews. Secrets require startup validation and have no usable production default.
- Client-controlled sort fields, URLs, redirects, file names, content types, and proxy headers need an allowlist or exact validation.
- Return generic 5xx messages; keep diagnostic details in server logs.
- Keep browser cookie authentication protected by CSRF. Do not weaken CORS with wildcard origins and credentials.
- Do not enable seed data, API docs, database `update`, insecure database transport, or fail-open rate limiting in production.

## Code style

- Prefer constructor injection for new code.
- Keep names descriptive and methods small. A comment should explain a non-obvious decision, not narrate the next line.
- Avoid decorative comment banners, generated-sounding essays, stale TODOs, and commented-out code.
- Preserve unrelated user changes and never rewrite history to make a change fit.
