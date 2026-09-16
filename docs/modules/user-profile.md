# User profile module

Id `user-profile`; package `dev.modularforge.profile`; default on; toggle `app.modules.user-profile.enabled`.

It owns self-service profile, password/email change, deactivation, anonymization, profile DTOs, and the `ProfileImageStorage` port. User identity remains required by authentication.

## Remove

1. Set the toggle to `false` and verify profile routes and the anonymization job are absent.
2. Delete the profile package and its tests.
3. Remove R2 user image endpoints or remove the whole image-storage adapter.
4. Decide what replaces account erasure/anonymization obligations before deleting the scheduled cleanup behavior.
5. Remove profile-only configuration and the catalog entry.
6. Keep identity fields used by auth/admin. Remove profile-only columns through an explicit migration only after searching all consumers.
7. Run `./mvnw clean verify`.

## Persistence and security behavior

Email changes delegate to `auth.EmailChangeService`: request with current password, confirm at the new address, then log in again. Profile PUT rejects a different email. Shared email/password request DTOs live in `shared.dto` so removing this feature does not break admin routes.
