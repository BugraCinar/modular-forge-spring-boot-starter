# Security module

Id `security`; package `dev.modularforge.security`; required.

It owns the Spring Security chain, JWT filter/signing support, access-denied/authentication handlers, CORS/CAPTCHA configuration, CSRF matcher, authorization helper, and `SecurityFilterExtension`.

It is not a removable feature. A replacement must preserve authentication entry points, method authorization, account-state checks, CSRF/CORS behavior, security headers, and optional filter extension semantics before this package is deleted.
