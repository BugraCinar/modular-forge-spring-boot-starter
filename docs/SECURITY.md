# Security baseline

Reviewed on 2026-09-04. This is a code baseline, not a substitute for deployment threat modelling, secret rotation, patch monitoring, or an independent penetration test.

## Dependency baseline

- Spring Boot 4.1.1 / Spring Framework 7.0.9+
- Java 21 with Maven Enforcer allowing supported JDKs below 27
- Springdoc 3.1.0
- Swagger UI 5.32.14, pinned above the DOMPurify fix for CVE-2026-75838
- Apache Tomcat 11.0.25, pinned above the 11.0.24 security boundary
- MySQL Connector/J 26.7.0, replacing the affected 9.7.x line
- AWS SDK 2.54.12
- Sentry 8.55.0
- ArchUnit 1.5.0
- OWASP Dependency-Check 13.0.0 in the `security-audit` Maven profile

Spring's 2026 advisories affecting older Spring Security 7.0.x and Spring Data JPA 4.0.x lines are avoided by the Boot 4.1.1 dependency set. Continue using Dependabot and CodeQL; both configurations are included under `.github/`.

## Changes in this baseline

- Required secrets no longer have working fallback values and are decoded/length-checked at startup.
- JWT signing and validation are pinned to HS256, issuer is verified, and every token receives a `jti`.
- New passwords require 12-128 characters; hashing enforces an Argon2id floor and clears temporary character arrays.
- Reset, verification, refresh, and 2FA challenge values use hashes/previews instead of plaintext persistence or logs.
- TOTP secrets use AES-256-GCM with a random IV; accepted codes cannot be immediately replayed; challenge expiry and attempt count are stored transactionally.
- Refresh token rotation revokes atomically and detects reuse.
- Cookie-authorized refresh/logout operations require CSRF tokens; cookie scope is narrowed to `/api/v1/auth`.
- CORS refuses wildcard origins with credentials.
- R2 deletion checks parsed scheme, host, port, and normalized path instead of string prefixes.
- Uploads are bounded and validated by content signature.
- CAPTCHA calls have connection/read timeouts and can validate hostname, action, and score.
- Client-provided sort fields use allowlists and page size is capped.
- Framework error bodies hide exception details; health details, OpenAPI, seed data, telemetry, and infrastructure modules are conservative by default.
- Trusted proxy headers are disabled until deployment configuration opts in.
- Unused dotenv, Bouncy Castle, and legacy Apache HttpClient dependencies were removed from the runtime graph.
- Spring Boot DevTools was removed because it is unnecessary in a reusable runtime starter and was being falsely matched to a Spring Tools IDE advisory.
- Swagger UI is pinned to a build containing DOMPurify 3.4.13 or newer after the scanner identified CVE-2026-75838 in 5.32.11.
- The NVD scan identified 2026 advisories in Tomcat 11.0.24 and MySQL Connector/J 9.7.0; the dependency set now resolves Tomcat 11.0.25 and Connector/J 26.7.0.

## Secret generation

Generate independent values; never reuse them across environments:

```bash
openssl rand -base64 32  # PEPPER
openssl rand -base64 32  # TOKEN_HASH_SECRET
openssl rand -base64 32  # JWT_SECRET
openssl rand -base64 32  # TWO_FACTOR_ENCRYPTION_KEY, if enabled
```

Prefer a container secret mount with Spring `configtree:` or a managed secrets service. Do not commit `.env`; it is an example format and is not loaded automatically.

## Browser contract

Call `GET /api/v1/auth/csrf` before a cookie-authorized refresh or logout. Copy the `token` value into the returned `headerName` (normally `X-XSRF-TOKEN`). The refresh cookie remains HttpOnly and is not readable by JavaScript.

## CI checks

```bash
./mvnw clean verify
./mvnw -Psecurity-audit -DskipTests verify
```

Set `NVD_API_KEY` for reliable Dependency-Check updates. Review results rather than suppressing an advisory globally; a suppression needs an owner, rationale, affected scope, and expiry date.
The scheduled GitHub workflow reads that value from the repository secret named `NVD_API_KEY`. Without a key, it updates from OWASP Dependency-Check's public NVD cache and then scans offline, so the audit is not silently skipped.

## Deployment checklist

- Terminate modern TLS at a trusted edge and validate forwarded headers there.
- Use a real Redis deployment with fail-closed behavior and authentication/network isolation.
- Use database TLS hostname verification and a least-privileged application user.
- Rotate all starter/test values and keep secrets out of logs, process arguments, and images.
- Configure CSP at the UI/edge in addition to API security headers.
- Keep actuator exposure minimal and protect non-health endpoints with admin authorization and network policy.
- Keep Redis in the readiness group when distributed rate limiting is configured to fail closed.
- Send audit events to tamper-resistant storage when the product's risk level requires it.
- Test account enumeration, authorization, mass assignment, upload parsing, SSRF, token replay, and rate-limit bypass against the completed product.

## References

- [Spring security advisories](https://spring.io/security/)
- [Spring Security CSRF guidance](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Spring Boot external configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Spring Boot actuator security](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)
- [Apache Tomcat 11 security reports](https://tomcat.apache.org/security-11.html)
- [Oracle July 2026 Critical Patch Update](https://www.oracle.com/security-alerts/cpujul2026.html)
