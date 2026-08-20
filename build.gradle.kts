import io.gitlab.arturbosch.detekt.Detekt

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Static analysis, applied to every module from here rather than through the convention plugins: it is one rule for
// the whole build, and a module cannot opt out of it by picking a different convention.
subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jlleitschuh.gradle.ktlint")

    extensions.configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        // Per module, so a module's debt sits with the module rather than in one file nobody owns. The baseline
        // records what was already there the day detekt was switched on: the build goes green, *new* code is
        // checked from the start, and the backlog is a list that can be worked down a line at a time.
        baseline = file("detekt-baseline.xml")
        buildUponDefaultConfig = true
        parallel = true
        basePath = rootProject.projectDir.absolutePath
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "17"
        reports {
            html.required.set(true)
            xml.required.set(false)
            sarif.required.set(false)
            md.required.set(false)
        }
    }
}
