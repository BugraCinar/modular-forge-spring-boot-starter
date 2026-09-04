# Authentication module

Id `authentication`; package `dev.modularforge.auth`; required.

It owns registration, login, password reset/verification, refresh-token storage and rotation, browser cookie handling, and the `SecondFactorGateway` extension point.

Removing it changes the product from an authenticated API and is an architectural fork. Replace its public routes and principals first, migrate or revoke all token records, update the security chain, then remove the package. Optional TOTP can be removed independently by following `two-factor.md`.
