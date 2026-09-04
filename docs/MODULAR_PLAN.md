# Modular plan

This file is the first stop for a person or coding agent changing ModulithForge. The goal is practical removability: switching a module off removes its runtime surface; deleting its package and declared dependencies must leave unrelated modules compilable.

## Module shape

Each feature owns a top-level package below `dev.modulithforge`:

```text
feature/
  FeatureController.java
  FeatureService.java
  FeatureRepository.java
  FeatureEntity.java
  dto/
  config/
```

Small modules do not need every layer. What matters is that their implementation does not leak into another package.

There are three module kinds:

- `REQUIRED`: foundational code. It can be replaced only as part of an architectural fork.
- `OPTIONAL`: a feature with a runtime toggle and a documented physical-removal path.
- `REPLACEABLE`: an adapter behind a stable port. Remove the adapter or add another implementation without changing its consumer.

The runtime catalog at `GET /api/v1/modules` reports the kind, current state, dependencies, Java package, toggle, and guide.

## Dependency direction

```text
optional adapter -> stable port <- consumer
       module     -> identity/auth/security/shared
       module     -X-> another optional implementation
```

Approved ports currently are:

| Purpose | Port | Default implementation |
|---|---|---|
| second login step | `auth.SecondFactorGateway` | `twofactor.TwoFactorAuthService` |
| profile image deletion | `profile.ProfileImageStorage` | `storage.r2.ImageUploadService` |
| notifications | `shared.notification.NotificationGateway` | `notification.EmailService` |
| audit events | `shared.audit.*` | services in `audit` |
| optional security filter | `security.SecurityFilterExtension` | audit filter extension |

ArchUnit tests fail if another package imports the TOTP, R2, audit, or SMTP implementation.

## Adding a module

1. Create one package and one kebab-case module id.
2. List owned routes, tables, configuration, dependencies, and data migration in `docs/modules/<id>.md`.
3. Add `app.modules.<id>.enabled`. Default to `false` if credentials, infrastructure, cost, or external traffic are involved.
4. Put the condition on every controller, service, scheduled job, and configuration bean.
5. If core code calls it, define a narrow port outside the implementation package. Do not add module fields to shared account entities.
6. Register it in `ModuleCatalogService` and `docs/MODULES.md`.
7. Add enabled behavior tests, a disabled context test, and a boundary rule.
8. Run the verification commands in `MODULE_ADDITION.md`.

## Removing a module

Use two passes:

1. Disable it in configuration and verify that routes/jobs/beans disappear.
2. Follow its guide to delete the package, tests, dependencies, resource files, configuration keys, and database objects.

Search before and after removal:

```bash
rg "dev\.modulithforge\.<module>|app\.modules\.<module>|MODULE_<MODULE>" .
./mvnw clean verify
```

Never delete a shared port merely because its current adapter is removed. Delete the port only if no consumer or replacement needs it.

## Definition of done

- The module catalog and docs agree with configuration defaults.
- Disabled context starts and exposes no module route or scheduled job.
- Other features do not import the implementation package.
- Secrets are validated at startup and never logged or persisted in plaintext.
- Database changes include forward and rollback migration notes.
- MySQL, PostgreSQL, MariaDB, and H2 compile with the selected Maven driver where the change touches persistence.
- `./mvnw clean verify` passes.
