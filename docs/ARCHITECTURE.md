# Architecture

ModulithForge is a modular monolith: one deployable Spring Boot application with explicit feature boundaries. It avoids the network and operational cost of microservices while keeping a later extraction path visible.

```text
HTTP
  -> security + rate limiting
  -> auth / admin / profile / module endpoint
       -> identity and token persistence
       -> shared ports
            -> audit implementation
            -> SMTP implementation
            -> TOTP implementation
            -> R2 implementation
```

## Packages

- `identity`: user/admin entities and repositories
- `auth`: registration, login, password flows, JWT/refresh-token coordination, and second-factor port
- `security`: filter chain, JWT validation, authorization, CORS, CAPTCHA, and filter extension point
- `ratelimit`: distributed Redis rate limiting
- `admin`, `profile`: optional application features
- `twofactor`, `storage.r2`, `notification`: optional or replaceable adapters
- `audit`: event persistence, admin audit APIs, MVC interceptor, and sensitive endpoint filter
- `backup`, `observability`, `bootstrap`: operational opt-ins
- `modules`: runtime catalog
- `shared`: neutral errors, configuration, web utilities, and cross-module ports

## Authentication model

Access tokens are signed HS256 JWTs with issuer, subject, role, auth-version, issued/expiry times, and a unique id. Account state and auth-version are checked on protected requests. Password/session changes increment auth-version or revoke refresh tokens.

Refresh, verification, and password-reset values are generated with a CSPRNG. The database stores a keyed hash and a short masked preview; the raw value exists only long enough to return or send it. Browser refresh tokens use Secure HttpOnly cookies and CSRF protection. API/mobile clients may send the refresh token body contract without cookie authority.

TOTP is a sidecar to `Admin`, not a field on it. Encrypted secrets and hashed, expiring, attempt-limited login challenges live in `two_factor_credentials`. This is what makes physical module deletion local.

## Persistence

JPA `open-in-view` is disabled. Services that map lazy relationships to DTOs keep the mapping inside read-only transactions. Production uses `ddl-auto=validate`; applications should add versioned Flyway or Liquibase migrations before deployment.

## Operational boundaries

Redis rate limiting fails closed outside explicitly configured development/test environments. Health details are not public. Sentry does not send default PII. OpenAPI, seed accounts, backups, R2, TOTP, and Sentry are off until enabled.
