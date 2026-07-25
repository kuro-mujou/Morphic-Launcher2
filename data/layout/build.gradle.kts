plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.layout"
}

dependencies {
    implementation(projects.core.model)

    testImplementation(libs.junit)
}
