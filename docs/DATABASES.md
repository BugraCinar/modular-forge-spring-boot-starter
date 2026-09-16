# Database providers

Feature services inject their existing repository ports (`UserRepository`, token and audit repositories). Each feature owns `persistence.jpa` and `persistence.mongo` adapters. `shared.persistence.EntityRepository` contains store-independent CRUD and pagination contracts. Selecting MongoDB replaces account, token, audit and TOTP persistence together. Shared models currently carry mapping annotations for both stores; adding another engine requires its adapters and schema mapping, not just a connection string.

| Database | Maven option | Spring profile | Schema management |
|---|---|---|---|
| MySQL | `-Ddb=mysql` (default) | `mysql` | Flyway + Hibernate validate |
| PostgreSQL | `-Ddb=postgresql` | `postgresql` | Flyway + Hibernate validate |
| MariaDB | `-Ddb=mariadb` | `mariadb` | Flyway + Hibernate validate |
| H2 | `-Ddb=h2` | `h2` | Flyway; development/tests |
| MongoDB | `-Ddb=mongodb` | `mongodb` | Collections, unique/query indexes and sequence initialization |

```sh
./mvnw -Ddb=mysql spring-boot:run -Dspring-boot.run.profiles=mysql,dev
./mvnw -Ddb=mongodb spring-boot:run -Dspring-boot.run.profiles=mongodb,dev
```

Set `SPRING_PROFILES_ACTIVE=mongodb,prod` and `MONGODB_URI` for a packaged MongoDB deployment. Select exactly one database profile. MongoDB excludes JDBC, JPA and Flyway auto-configuration. SQL profiles exclude MongoDB auto-configuration. Maven selects the packaged JDBC driver; it does not select the Spring runtime profile. Redis still provides rate limiting for every provider.

## MongoDB requirements

Use a replica set (including a single-node replica set for development) or a sharded cluster. Startup rejects standalone MongoDB: account changes, token consumption and session revocation require transactions. Use an authenticated TLS URI in production, for example `mongodb+srv://...`; keep credentials in secret configuration. The localhost example in `.env.template` is only for local development.

Long account/token IDs are retained. An atomic `repository_sequences` collection allocates IDs outside the account transaction with majority write concern (rollbacks may leave harmless ID gaps); unique indexes enforce per-collection username, email and token-hash uniqueness. Account and TOTP updates use optimistic locking, so a stale writer fails instead of replacing newer state. The initializer creates collections before transactions and seeds counters from existing numeric IDs. Preserve counters and indexes when restoring backups. Existing imported documents must conform to the model and use numeric IDs; resolve duplicates before startup.

Changing profiles does **not** transfer data between engines. Export, transform and validate records (including versions, UTC dates, token hashes, TOTP sidecars and audit records) before switching an existing deployment. Quiesce writers and revoke sessions during migration.

## SQL migrations

`src/main/resources/db/migration/{vendor}` contains V1 initial schemas and V2 security changes for all four SQL engines. Empty databases migrate automatically. Hibernate defaults to `validate`; do not use `update` for production. `FLYWAY_ENABLED=false` is for explicitly managed schemas/tests only.

For an existing database created by earlier versions:

1. Take and restore-test a backup, stop application writers, and compare its schema with V1. Include optional-module tables. Resolve missing tables, legacy LOB types, invalid data and collation differences explicitly.
2. Baseline that verified schema at Flyway version **1** using your migration tooling. Automatic baseline-on-migrate remains disabled so an unrelated database cannot be adopted silently.
3. Apply V2 and start with `validate`. V2 adds row versions and binds refresh/email/TOTP challenges to the account's session version. Old refresh tokens and outstanding email-change/TOTP challenges intentionally require a fresh login or request.
4. Store timestamps as UTC. Earlier application versions used Europe/Istanbul local timestamps; convert historical date columns to UTC during the maintenance window before starting this version. Do not convert already-UTC data again.

On rollback, restore the tested snapshot and matching application version together. Do not deploy the old application against the changed schema or remove columns while writers run. V2 retains existing account data; it does not copy or delete production records automatically.

## Verification

The regular suite runs `DatabaseContractTest` on H2 using Flyway and Hibernate validation. The same test runs against isolated real servers:

```sh
./mvnw -Ddb=mysql -Dtest=DatabaseContractTest -Dcontract.provider=mysql test
./mvnw -Ddb=mongodb -Dtest=DatabaseContractTest -Dcontract.provider=mongodb test
```

Defaults: MySQL at `127.0.0.1:13306` with an isolated empty-password test root; MongoDB at `127.0.0.1:27028` with replica set `rs0`. Override `contract.mysql.url` / `contract.mongo.address` for your **test** infrastructure. The harness creates uniquely named `mf_contract_*` databases and initializes only an uninitialized test replica set. Never point it at production. CI also runs these real-provider contracts.

Tests cover schema startup, queries/aggregations, unique indexes, optimistic locking, transaction rollback, and atomic refresh consumption with replay-history retention. These checks do not constitute a SQL-to-Mongo data migration or production load test.

## Optional backup adapter

The built-in encrypted dump/email module supports MySQL and MariaDB. Keep it disabled for MongoDB, PostgreSQL and H2; use the database platform's backup service or provide a replacement adapter. See [backup module](modules/database-backup.md). TLS verification is mandatory for its dump subprocess independently of JDBC settings.
