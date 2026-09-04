package dev.modulithforge.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {

    private final com.tngtech.archunit.core.domain.JavaClasses productionClasses =
            new ClassFileImporter()
                    .withImportOption(new ImportOption.DoNotIncludeTests())
                    .importPackages("dev.modulithforge");

    @Test
    void twoFactorCanBeRemovedWithoutChangingOtherModules() {
        noClasses().that().resideOutsideOfPackage("dev.modulithforge.twofactor..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modulithforge.twofactor..")
                .check(productionClasses);
    }

    @Test
    void r2AdapterCanBeRemovedWithoutChangingOtherModules() {
        noClasses().that().resideOutsideOfPackage("dev.modulithforge.storage.r2..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modulithforge.storage.r2..")
                .check(productionClasses);
    }

    @Test
    void auditImplementationStaysBehindItsPorts() {
        noClasses().that().resideOutsideOfPackage("dev.modulithforge.audit..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modulithforge.audit..")
                .check(productionClasses);
    }

    @Test
    void emailImplementationStaysBehindItsPort() {
        noClasses().that().resideOutsideOfPackage("dev.modulithforge.notification..")
                .should().dependOnClassesThat().resideInAnyPackage("dev.modulithforge.notification..")
                .check(productionClasses);
    }
}
