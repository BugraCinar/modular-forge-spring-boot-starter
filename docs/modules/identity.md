# Identity module

Id `identity`; package `dev.modularforge.identity`; required.

It owns user/admin entities and repositories. Authentication, authorization, admin management, profile, tokens, and optional sidecar modules refer to identity ids.

This is not independently removable. To replace it, introduce application-facing account/query ports, migrate every consumer and database relationship, then delete the JPA implementation. Keep optional feature state out of `User` and `Admin`; sidecar tables make those features removable.
