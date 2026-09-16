# Module catalog

The live view is available without authentication at `GET /api/v1/modules`. Configuration values below are the source for runtime activation; Maven profiles select database drivers, not feature modules. SQL and MongoDB providers use the same feature repository ports; see [database selection](DATABASES.md).

| Id | Kind | Default | Package | Depends on |
|---|---|---:|---|---|
| identity | required | on | `identity` | — |
| authentication | required | on | `auth` | identity, security, rate-limit, notification port |
| security | required | on | `security` | identity, rate-limit |
| rate-limit | required | on | `ratelimit` | Redis |
| notification-email | replaceable | on | `notification` | notification port, SMTP |
| admin-management | optional | on | `admin` | identity, security, audit port, notification port |
| user-profile | optional | on | `profile` | identity, authentication, notification port |
| two-factor | optional | off | `twofactor` | authentication port, identity |
| image-storage | replaceable | off | `storage.r2` | profile storage port, AWS S3 SDK |
| audit | optional | on | `audit` | identity, security extension, notification port |
| database-backup | optional | off | `backup` | MySQL/MariaDB tools, separate encryption key, email |
| observability | optional | off | `observability` | Sentry SDK |
| api-docs | optional | off | `observability.OpenApiConfig` | springdoc |
| kafka | optional | off | `kafka` | shared event contract, Kafka broker |
| seed-data | optional | off | `bootstrap` | identity |

All guides are in `docs/modules/`. A configuration switch leaves code and dependencies present; a physical removal follows the corresponding guide.

## Stability rules

- Required modules are part of the starter's identity and are not drop-in removable.
- Optional modules may depend on required modules, never the reverse through a concrete class.
- Replaceable modules implement a port owned by the consumer or `shared`.
- Audit and notification have fallback beans so the context remains constructible after their implementations are disabled. The notification fallback fails explicitly when a workflow tries to send mail; install a replacement for working registration/reset delivery.
