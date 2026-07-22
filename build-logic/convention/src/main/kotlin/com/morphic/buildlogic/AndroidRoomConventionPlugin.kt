package com.morphic.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

class AndroidRoomConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.google.devtools.ksp")

            afterEvaluate {
                val kspExt = extensions.findByName("ksp") ?: return@afterEvaluate
                val argMethod = kspExt.javaClass.methods.firstOrNull {
                    it.name == "arg"
                        && it.parameterCount == 2
                        && it.parameterTypes.all { p ->
                            p == String::class.java
                        }
                } ?: return@afterEvaluate
                argMethod.invoke(kspExt, "room.schemaLocation", "$projectDir/schemas")
                argMethod.invoke(kspExt, "room.incremental", "true")
                argMethod.invoke(kspExt, "room.generateKotlin", "true")
            }

            dependencies {
                add("implementation", libs.findLibrary("room-runtime").get())
                add("implementation", libs.findLibrary("room-ktx").get())
                add("ksp", libs.findLibrary("room-compiler").get())
            }
        }
    }
}
