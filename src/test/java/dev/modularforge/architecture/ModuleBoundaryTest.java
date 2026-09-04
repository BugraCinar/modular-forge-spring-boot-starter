package dev.modularforge.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {

    private final com.tngtech.archunit.core.domain.JavaClasses productionClasses =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("dev.modularforge");

    @Test
    void twoFactorCanBeRemovedWithoutChangingOtherModules() {
        noClasses().that().resideOutsideOfPackage("dev.modularforge.twofactor..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modularforge.twofactor..")
                .check(productionClasses);
    }

    @Test
    void r2AdapterCanBeRemovedWithoutChangingOtherModules() {
        noClasses().that().resideOutsideOfPackage("dev.modularforge.storage.r2..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modularforge.storage.r2..")
                .check(productionClasses);
    }

    @Test
    void auditImplementationStaysBehindItsPorts() {
        noClasses().that().resideOutsideOfPackage("dev.modularforge.audit..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modularforge.audit..")
                .check(productionClasses);
    }

    @Test
    void emailImplementationStaysBehindItsPort() {
        noClasses().that().resideOutsideOfPackage("dev.modularforge.notification..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modularforge.notification..")
                .check(productionClasses);
    }
}
