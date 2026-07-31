plugins {
    alias(libs.plugins.launcher.android.feature)
}

android {
    namespace = "inkspire.morphic.feature.shell"
}

dependencies {
    // The composition root for the launcher's surfaces, so it depends on the surfaces it composes. This is the one
    // feature module that legitimately depends on other features: its whole job is to put them on screen together.
    // Everything else stays one-way — home and apps know nothing about the shell, or about each other.
    implementation(projects.feature.home)
    implementation(projects.feature.apps)

    // The surface register: which surface is bound to which HOME edge, and in which layout.
    implementation(projects.data.settings)

    // `BackHandler`, so back closes an open side surface instead of leaving the launcher.
    implementation(libs.androidx.activity.compose)
}
