package dev.modularforge.persistence;

import jakarta.persistence.Entity;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;

class SchemaGenerationTest {
    @Test
    void mappingsGenerateSchemasForEverySqlProvider() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var entry : Map.of("h2", "H2Dialect", "mysql", "MySQLDialect", "mariadb", "MariaDBDialect", "postgresql", "PostgreSQLDialect").entrySet()) {
            Path target = Path.of("target", "schemas", entry.getKey() + ".sql");
            Files.createDirectories(target.getParent());
            Files.deleteIfExists(target);
            var registry = new StandardServiceRegistryBuilder()
                    .applySetting("hibernate.dialect", "org.hibernate.dialect." + entry.getValue())
                    .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                    .applySetting("jakarta.persistence.schema-generation.database.action", "none")
                    .applySetting("jakarta.persistence.schema-generation.scripts.action", "create")
                    .applySetting("jakarta.persistence.schema-generation.scripts.create-target", target.toString())
                    .applySetting("hibernate.hbm2ddl.delimiter", ";")
                    .build();
            try {
                var sources = new MetadataSources(registry);
                for (var candidate : scanner.findCandidateComponents("dev.modularforge")) {
                    sources.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
                }
                try (var factory = sources.buildMetadata().buildSessionFactory()) {
                    assertThat(factory.isOpen()).isTrue();
                }
                assertThat(Files.readString(target)).containsIgnoringCase("create table users");
            } finally {
                StandardServiceRegistryBuilder.destroy(registry);
            }
        }
    }
}
