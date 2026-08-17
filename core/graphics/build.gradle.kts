plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.core.graphics"
}

dependencies {
    // `createBitmap`, for the one entry point that hands back a bitmap rather than filling an array.
    implementation(libs.androidx.core.ktx)

    // Everything here is arithmetic over an `IntArray`, which is the whole reason the module exists: a blur that is
    // wrong is wrong in a way no screenshot explains, and this is where it can be checked without an emulator.
    testImplementation(libs.junit)
}
