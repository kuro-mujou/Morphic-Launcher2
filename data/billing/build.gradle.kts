plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.data.billing"
}

dependencies {
    implementation(projects.core.common) // ApplicationScope + Koin + coroutines (api-exposed)

    // Play Billing. Its manifest brings the `com.android.vending.BILLING` permission with it.
    implementation(libs.play.billing.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
}
