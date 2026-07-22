package inkspire.morphic.buildlogic

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

/** The `libs` version catalog, for resolving versions and libraries from within convention plugins. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/**
 * Shared Android + Kotlin configuration applied by both the library and application plugins: compile/min SDK
 * from the catalog, Java 17, `META-INF` packaging excludes, and the Kotlin compiler setup in [configureKotlin].
 */
internal fun Project.configureKotlinAndroid(
    commonExtension: CommonExtension,
) {
    with(commonExtension) {
        compileSdk = libs.findVersion("compileSdk").get().toString().toInt()

        defaultConfig.minSdk = libs.findVersion("minSdk").get().toString().toInt()

        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17

        packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        packaging.resources.excludes += "/META-INF/LICENSE*"
        packaging.resources.excludes += "/META-INF/NOTICE*"
    }

    configureKotlin()
}

/** JVM target 17 plus the project-wide compiler opt-ins (RequiresOptIn, experimental coroutines/material3/foundation). */
private fun Project.configureKotlin() {
    extensions.configure<KotlinAndroidProjectExtension> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            freeCompilerArgs.addAll(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
                "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            )
        }
    }
}
