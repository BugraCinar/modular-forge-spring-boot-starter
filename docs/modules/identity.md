# Identity module

Id `identity`; package `dev.modularforge.identity`; required.

It owns user/admin entities and repositories. Authentication, authorization, admin management, profile, tokens, and optional sidecar modules refer to identity ids.

This is not independently removable. To replace it, introduce application-facing account/query ports, migrate every consumer and database relationship, then delete the JPA implementation. Keep optional feature state out of `User` and `Admin`; sidecar tables make those features removable.

## Persistence and security behavior

Repository interfaces are provider-independent ports. SQL and MongoDB adapters live in `identity.persistence.jpa` / `identity.persistence.mongo`; each account has a row version for optimistic locking. SQL tables and Mongo collections retain numeric account IDs. Provider selection and migration instructions: [Databases](../DATABASES.md).
