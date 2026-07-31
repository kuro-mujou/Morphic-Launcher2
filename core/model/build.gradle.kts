plugins {
    alias(libs.plugins.launcher.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.kotlinx.serialization.json)

    // The blueprint registry's invariants (slot -> blueprint is total and unique) are checked here rather than
    // asserted at class-init, so a mistake fails the build instead of the launcher.
    testImplementation(libs.junit)
}
