# Security module

Id `security`; package `dev.modularforge.security`; required.

It owns the Spring Security chain, JWT filter/signing support, access-denied/authentication handlers, CORS/CAPTCHA configuration, CSRF matcher, authorization helper, and `SecurityFilterExtension`.

It is not a removable feature. A replacement must preserve authentication entry points, method authorization, account-state checks, CSRF/CORS behavior, security headers, and optional filter extension semantics before this package is deleted.

## Persistence and security behavior

Browser responses use a fresh CSP nonce. Server-rendered script/style blocks carry that nonce; arbitrary inline code remains blocked. Admin-capable login requests with an omitted role also require CAPTCHA when CAPTCHA is enabled.
