import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "inkspire.morphic.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    compileOnly(libs.android.gradle.plugin)
    compileOnly(libs.kotlin.gradle.plugin)
    compileOnly(libs.kotlin.compose.compiler.gradle.plugin)
    compileOnly(libs.ksp.gradle.plugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "launcher.android.application"
            implementationClass = "inkspire.morphic.buildlogic.AndroidApplicationConventionPlugin"
        }
        register("androidApplicationCompose") {
            id = "launcher.android.application.compose"
            implementationClass = "inkspire.morphic.buildlogic.AndroidApplicationComposeConventionPlugin"
        }
        register("androidLibrary") {
            id = "launcher.android.library"
            implementationClass = "inkspire.morphic.buildlogic.AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "launcher.android.library.compose"
            implementationClass = "inkspire.morphic.buildlogic.AndroidLibraryComposeConventionPlugin"
        }
        register("androidFeature") {
            id = "launcher.android.feature"
            implementationClass = "inkspire.morphic.buildlogic.AndroidFeatureConventionPlugin"
        }
        register("androidRoom") {
            id = "launcher.android.room"
            implementationClass = "inkspire.morphic.buildlogic.AndroidRoomConventionPlugin"
        }
        register("jvmLibrary") {
            id = "launcher.jvm.library"
            implementationClass = "inkspire.morphic.buildlogic.JvmLibraryConventionPlugin"
        }
    }
}
