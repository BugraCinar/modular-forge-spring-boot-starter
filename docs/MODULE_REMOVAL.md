# Removing a module

Use the module-specific guide in `docs/modules/` together with this checklist.

## Runtime disable

1. Set the module property to `false` in every deployed environment.
2. Start the application with production-like configuration.
3. Check `GET /api/v1/modules` and confirm the module is disabled.
4. Confirm its routes return 404, scheduled work is absent, and startup does not require its secrets.

This is reversible and is the recommended first release of a removal.

## Physical removal

1. Read the module's owned package, routes, tables, resources, dependencies, and consumers.
2. Search all references with `rg`; do not rely on directory names alone.
3. Delete the implementation package and its tests.
4. Remove only dependencies exclusive to that implementation.
5. Remove configuration and environment variables. Keep a shared port if a consumer or replacement remains.
6. Apply an explicit database migration. Do not let Hibernate mutate a production schema.
7. Remove the module catalog entry in the same commit.
8. Run `./mvnw clean verify` and compile the supported database profiles.

If another module imports the implementation, the boundary is not ready. Move the required behavior behind a small port first, then repeat the removal.

## Data decision

Choose one before dropping tables or object storage:

- export and retain under the product's retention policy;
- migrate into the replacement module;
- permanently delete after legal/operational approval.

The template cannot choose retention policy for an application, so module guides identify data but do not silently destroy it.
