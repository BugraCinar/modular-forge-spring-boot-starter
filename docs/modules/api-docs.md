# OpenAPI module

Id `api-docs`; implementation `dev.modularforge.observability.OpenApiConfig`; default off outside `dev`; toggle `app.swagger.enabled`.

## Remove

1. Set `APP_SWAGGER_ENABLED=false` in every environment.
2. Delete `OpenApiConfig` and Swagger profile tests.
3. Remove `springdoc-openapi-starter-webmvc-ui`, the pinned Swagger UI WebJar, and `swagger-ui.version`.
4. Remove `app.swagger.*`, `springdoc.*`, Swagger security matchers, and the catalog entry.
5. Run `./mvnw clean verify`; `/v3/api-docs` and `/swagger-ui.html` must return 404.
