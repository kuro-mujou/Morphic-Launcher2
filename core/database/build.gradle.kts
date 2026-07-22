plugins {
    alias(libs.plugins.launcher.android.library)
    alias(libs.plugins.launcher.android.room)
}

android {
    namespace = "inkspire.morphic.core.database"
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.common)
}
