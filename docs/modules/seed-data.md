# Development seed-data module

Id `seed-data`; package `dev.modularforge.bootstrap`; default off; toggle `app.data.init-users`.

It creates explicitly configured local user/admin accounts. It rejects short or missing passwords and does not print them.

## Remove

1. Keep the toggle false and delete the bootstrap package and tests.
2. Remove `INIT_USERS` and all `SEED_*`/`app.data.*` settings.
3. Delete seeded accounts from non-production databases if no longer needed.
4. Remove the catalog entry and run `./mvnw clean verify`.

Never enable seed data in production or give seed credentials a default value.
