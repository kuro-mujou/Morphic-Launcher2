import java.util.Properties

plugins {
    alias(libs.plugins.launcher.android.application)
    alias(libs.plugins.launcher.android.application.compose)
    // `app` declares its own `@Serializable` nav key (the dev harness), so it needs the plugin itself.
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing, read from an untracked `keystore.properties` at the repo root (`storeFile`, `storePassword`,
 * `keyAlias`, `keyPassword`). Both it and the `.jks` it points at are gitignored — a signing key must never reach a
 * commit — which is exactly why this has to tolerate their absence: a fresh clone has neither, and configuring the
 * build must not fail because of it.
 *
 * **It lives here rather than in the convention plugin**, unlike the minification it pairs with. A keystore is a
 * property of *this application* — one identity, one key — where "release builds are minified and shrunk" is a
 * project-wide rule. The convention plugin owns the second and says nothing about the first.
 */
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "inkspire.morphic.launcher"

    defaultConfig {
        applicationId = "inkspire.morphic.launcher"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // `findByName` rather than `getByName`: with no `keystore.properties` present there is no config to
            // find, and a null one leaves the APK unsigned rather than failing the build. `assembleRelease` still
            // works (you get an unsigned APK); only `installRelease` needs the key.
            //
            // Minification and resource shrinking are *not* set here — they come from the
            // `launcher.android.application` convention plugin, which is where a project-wide rule belongs.
            signingConfig = signingConfigs.findByName("release")
        }
    }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.database)
    implementation(projects.core.icon)
    implementation(projects.core.designsystem)
    implementation(projects.core.model)
    implementation(projects.data.apps)
    implementation(projects.data.icons)
    implementation(projects.data.layout)
    implementation(projects.core.navigation)
    implementation(projects.data.settings) // settingsModule, for startKoin
    implementation(projects.data.wallpaper) // wallpaperModule, likewise
    implementation(projects.data.widgets)   // widgetsModule, likewise
    implementation(projects.feature.shell)
    implementation(projects.feature.settings)
    // Still direct deps despite `feature:shell` composing them: the dev harness hosts both screens itself, and the
    // playgrounds reach into `core:designsystem` primitives the shell doesn't expose.
    implementation(projects.feature.home)
    implementation(projects.feature.apps)

    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.androidx.core.ktx)
    // `app` is where Timber's tree is planted — every other module logs through it and none of them could make it
    // write anywhere. See `LauncherApplication.plantLogging`.
    implementation(libs.timber)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui.tooling.preview)

    // Navigation 3: `core:navigation` exposes the runtime (NavKey/NavBackStack) via `api`; hosting the graph
    // additionally needs `NavDisplay`, which is the ui artifact. `app` is the only module that hosts one.
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
