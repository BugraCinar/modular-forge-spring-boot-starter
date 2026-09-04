# Admin management module

Id `admin-management`; package `dev.modularforge.admin`; default on; toggle `app.modules.admin-management.enabled`.

It owns admin/profile and user-management endpoints and DTOs. Admin identity remains in the required identity package because authentication and authorization need it.

## Remove

1. Set the toggle to `false` and confirm `/api/v1/admin/admins/**`, admin profile, and admin user-management routes return 404.
2. Delete the `admin` package and corresponding controller/service tests.
3. Remove image-storage admin endpoints if the R2 adapter is retained; its condition already prevents startup coupling.
4. Review security matchers for obsolete admin-management paths and remove them.
5. Keep the `Admin` entity/repository and `AdminLevelAuthorizationService` while admin login or protected operational endpoints exist.
6. Remove the catalog entry and run `./mvnw clean verify`.
