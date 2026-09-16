# Modular Forge

A security-focused Spring Boot 4 REST API starter organized as removable feature modules. It targets Java 21 and keeps optional features behind configuration switches and small ports, so a storage, audit, notification, or second-factor implementation can be replaced without spreading edits across the application.

## What is included

- User and admin authentication with short-lived JWT access tokens
- Rotating refresh tokens, server-side revocation, and browser cookie support
- Argon2id password hashing with a separate application pepper
- Email verification, password reset, account lockout, and CAPTCHA integration
- Optional admin management, profile, audit, TOTP 2FA, R2 image storage, backups, Sentry, and OpenAPI modules
- SQL/MongoDB repository adapters: MySQL, PostgreSQL, MariaDB, H2 and MongoDB profiles
- Public module discovery at `GET /api/v1/modules`
- Unit, slice, integration, module-boundary, and disabled-module context tests

The complete module matrix is in [docs/MODULES.md](docs/MODULES.md). Read [docs/MODULAR_PLAN.md](docs/MODULAR_PLAN.md) before adding or removing a feature.

## Quick start

Requirements: JDK 21 and Docker or local instances of the selected database and Redis. Maven is provided through the wrapper.

```bash
cp .env.template .env
openssl rand -base64 32
```

Put independent 32-byte Base64 values in `PEPPER`, `TOKEN_HASH_SECRET`, and `JWT_SECRET`. Then choose a database profile:

```bash
# MySQL is the default Maven driver
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql,dev

# PostgreSQL
./mvnw -Ddb=postgresql spring-boot:run -Dspring-boot.run.profiles=postgresql,dev

# MongoDB (requires a replica set)
./mvnw -Ddb=mongodb spring-boot:run -Dspring-boot.run.profiles=mongodb,dev

# MariaDB
./mvnw -Ddb=mariadb spring-boot:run -Dspring-boot.run.profiles=mariadb,dev
```

PowerShell uses `./mvnw.cmd` in place of `./mvnw`. Environment variables can be supplied by the IDE, shell, container runtime, or a secrets manager; Spring Boot does not automatically import `.env` files.

For a zero-infrastructure smoke run, use the H2 driver and let rate limiting fail open only in development:

```bash
./mvnw -Ddb=h2 spring-boot:run -Dspring-boot.run.profiles=h2,dev \
  -Dspring-boot.run.arguments="--app.rate-limit.fail-open=true"
```

## Modules

Optional modules are off or on through `app.modules.<id>.enabled`. High-integration adapters are disabled by default:

```properties
app.modules.two-factor.enabled=false
app.modules.image-storage.enabled=false
app.modules.database-backup.enabled=false
app.modules.observability.enabled=false
```

A switch is useful for deployment variants. Physical removal is documented separately so dead dependencies, configuration, tests, and database objects are not left behind:

- [Remove a module](docs/MODULE_REMOVAL.md)
- [Add a module](docs/MODULE_ADDITION.md)
- [Database choices](docs/DATABASES.md)
- [Security model](docs/SECURITY.md)

## Verify

```bash
./mvnw clean verify
./mvnw -Psecurity-audit -DskipTests verify
```

The security audit profile runs OWASP Dependency-Check and fails for a known dependency vulnerability with CVSS 7 or higher. An NVD API key is recommended for CI.

## Production notes

- SQL schemas use Flyway and Hibernate `validate`; follow [database migration instructions](docs/DATABASES.md) before upgrading an existing database.
- Keep `COOKIE_SECURE=true`, TLS at the trusted edge, and `FORWARD_HEADERS_STRATEGY=none` unless proxy headers are sanitized by that edge.
- Redis is fail-closed by default because local-only rate limits are unsafe in a multi-instance deployment.
- OpenAPI, seed accounts, R2, TOTP, backups, and observability are opt-in.
- Browser refresh/logout requests use an HttpOnly refresh cookie plus an `XSRF-TOKEN`/`X-XSRF-TOKEN` double-submit token. Obtain it from `GET /api/v1/auth/csrf`.

## License

Released under [CC0 1.0](LICENSE).

### Kafka application events

Optional, disabled by default, and independent of SQL/MongoDB selection. Publishes verified account email changes after transaction commit. See [configuration, event schema and delivery limits](docs/modules/kafka.md).
