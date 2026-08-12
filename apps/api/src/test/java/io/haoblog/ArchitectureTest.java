package io.haoblog;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "io.haoblog")
class ArchitectureTest {
    @ArchTest
    static final ArchRule shared_must_not_depend_on_business = noClasses()
            .that().resideInAnyPackage("io.haoblog.shared..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "io.haoblog.identity..", "io.haoblog.content..", "io.haoblog.comment..",
                    "io.haoblog.toolbox..", "io.haoblog.ai..", "io.haoblog.media..", "io.haoblog.site..");
}
