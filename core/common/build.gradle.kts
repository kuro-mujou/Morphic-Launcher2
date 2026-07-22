plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.coroutines.core)
    api(libs.koin.core)

    implementation(libs.timber)
}