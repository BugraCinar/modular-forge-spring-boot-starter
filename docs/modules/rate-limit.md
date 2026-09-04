# Rate-limit module

Id `rate-limit`; package `dev.modulithforge.ratelimit`; required by the public authentication API.

It owns global and flow-specific limits backed by Redis. Production defaults to fail closed so loss of Redis cannot silently remove abuse controls.

To replace it, implement equivalent distributed limits, update auth and the security filter chain, preserve trusted client-IP handling, and test concurrency/multi-instance behavior before deleting the package and Redis dependency. A local in-memory limiter is suitable only for a single-instance development profile.
