# Adding a module

Start with a short design entry containing:

```text
id:
kind: OPTIONAL | REPLACEABLE
package:
toggle:
default:
depends on:
owned routes:
owned tables:
owned resources:
external secrets/services:
public port:
rollback/data plan:
```

Keep controller, service, DTO, persistence, configuration, and tests in the module package. If an existing feature needs the behavior, define the narrowest possible port in the consumer or `shared`; the implementation module points inward to the port.

An external integration should default off, validate all required settings only when enabled, set bounded timeouts, verify TLS/hostnames, expose no provider error detail to clients, and avoid logging payloads or credentials.

Before merging:

```bash
./mvnw clean verify
./mvnw -Ddb=postgresql -DskipTests package
./mvnw -Ddb=mariadb -DskipTests package
./mvnw -Ddb=h2 -DskipTests package
./mvnw -Psecurity-audit -DskipTests verify
```

Add a disabled-module context assertion and extend `ModuleBoundaryTest` for every implementation package meant to be physically removable.
