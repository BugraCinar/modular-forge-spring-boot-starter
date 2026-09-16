# TOTP two-factor module

Id `two-factor`; package `dev.modularforge.twofactor`; default off; toggle `app.modules.two-factor.enabled`.

It owns `/api/v1/admin/2fa/**`, `two_factor_credentials`, TOTP/QR dependencies, encrypted secrets, replay metadata, and hashed login challenges. Authentication sees only `auth.SecondFactorGateway`; `Admin` has no 2FA fields.

## Remove

1. Set the toggle to `false` and verify the module endpoint reports it disabled.
2. Delete `src/main/java/dev/modularforge/twofactor` and its tests.
3. Remove `googleauth` and ZXing core/javase only if no replacement uses them.
4. Remove `TWO_FACTOR_ENCRYPTION_KEY` and `app.modules.two-factor.*` properties.
5. Drop `two_factor_credentials` with a reviewed migration. This destroys enrolled authenticators, so announce forced re-enrollment if replacing the implementation.
6. Remove the catalog entry and the optional public matcher for `/api/v1/admin/2fa/verify-login`.
7. Keep `SecondFactorGateway`; auth deliberately works with no implementation. Delete it only after removing second-factor extensibility.
8. Run `./mvnw clean verify` and confirm normal admin login returns tokens directly.

To replace TOTP, implement `SecondFactorGateway` in another module and keep challenge verification inside that module.

## Persistence and security behavior

Enabled authenticators cannot be overwritten by setup; disable with a valid existing TOTP first. Challenges carry the account auth version and are rejected after password/session invalidation or account locking/deactivation. Login challenge consumption and account update execute in one transaction. Credential row versions enforce optimistic locking in MongoDB; SQL also uses its repository lock. Remove both `persistence.jpa` and `persistence.mongo` adapters when physically removing this module.
