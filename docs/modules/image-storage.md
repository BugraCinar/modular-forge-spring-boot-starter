# R2 image-storage adapter

Id `image-storage`; package `dev.modularforge.storage.r2`; default off; toggle `app.modules.image-storage.enabled`.

It owns user/admin image endpoints, `CloudflareR2Config`, and `ImageUploadService`. Profile cleanup sees only `profile.ProfileImageStorage` through an optional provider.

## Remove

1. Set the toggle to `false`; image routes should return 404 while profile routes keep working.
2. Decide whether to export or delete existing bucket objects. Disabling the module does not touch remote data.
3. Delete `src/main/java/dev/modularforge/storage/r2` and its tests.
4. Remove AWS SDK `s3` plus `aws.sdk.version` if unused.
5. Remove `CLOUDFLARE_R2_*` and `app.modules.image-storage.r2.*` configuration.
6. Remove the catalog entry. Keep `ProfileImageStorage` if another adapter will replace R2; otherwise remove the port after confirming it has no consumers.
7. Run `./mvnw clean verify`.

A replacement implements `ProfileImageStorage` and owns its own upload endpoints/configuration. URL ownership checks must parse and compare origin/path exactly.

## Persistence and security behavior

Delete and replacement cleanup also validate the object key against `profiles/{role}/profile_{role}_{accountId}_...`. A matching stored URL alone cannot authorize deleting another account's object.
