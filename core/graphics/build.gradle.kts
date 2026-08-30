plugins {
    alias(libs.plugins.launcher.android.library)
}

android {
    namespace = "inkspire.morphic.core.graphics"

    // A generator's *look* is the one thing an `IntArray` test cannot judge, and `android.graphics.Canvas` only
    // really draws on a device — so the render harness (`GeneratorRenderHarness`) is an instrumentation test that
    // paints each generator to a PNG on the emulator for a human to look at.
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    // The wallpaper generator seam paints from a `Palette` and a `DesignParams`, and renders a `WallpaperDesign`.
    implementation(projects.core.model)

    // `createBitmap`, for the one entry point that hands back a bitmap rather than filling an array.
    implementation(libs.androidx.core.ktx)

    // Everything here is arithmetic over an `IntArray`, which is the whole reason the module exists: a blur that is
    // wrong is wrong in a way no screenshot explains, and this is where it can be checked without an emulator.
    testImplementation(libs.junit)

    // The generator render harness — real `Bitmap`/`Canvas` on the emulator, for the look a unit test cannot see.
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.junit)
}
