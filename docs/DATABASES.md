# Database choices

Maven profiles keep one JDBC driver in the normal runtime artifact. Spring profiles provide the matching URL and driver settings.

| Database | Maven option | Spring profile | Default port | Production TLS default |
|---|---|---|---:|---|
| MySQL | no option or `-Ddb=mysql` | `mysql` | 3306 | `VERIFY_IDENTITY` |
| PostgreSQL | `-Ddb=postgresql` | `postgresql` | 5432 | `verify-full` |
| MariaDB | `-Ddb=mariadb` | `mariadb` | 3306 | `verify-full` |
| H2 | `-Ddb=h2` | `h2` | — | in-memory development only |

Examples:

```bash
./mvnw -Ddb=postgresql spring-boot:run -Dspring-boot.run.profiles=postgresql,dev
./mvnw -Ddb=mariadb clean package
./mvnw -Ddb=h2 test
```

Use `DB_URL` to replace the generated JDBC URL when a cloud provider requires extra parameters. Do not disable certificate or hostname validation in production. The `.env.template` uses an explicitly insecure local MySQL value so a developer must make the production choice consciously.

## Schema changes

This starter does not guess an application's migration tool. Before production:

1. add Flyway or Liquibase;
2. create an initial schema from the current entities;
3. set local/test environments to migrations too;
4. retain `spring.jpa.hibernate.ddl-auto=validate` in production;
5. run database-specific integration tests, ideally with Testcontainers.

The backup module shells out to `mysqldump`, so it supports MySQL and MariaDB only. PostgreSQL projects should replace it with a `pg_dump` adapter or remove the module.
