pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "Launcher2"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")

include(":core:model")
include(":core:common")
include(":core:database")
include(":core:designsystem")
include(":core:graphics")
include(":core:icon")
include(":core:navigation")

include(":data:apps")
include(":data:icons")
include(":data:layout")
include(":data:settings")
include(":data:wallpaper")
include(":data:widgets")

include(":feature:home")
include(":feature:apps")
include(":feature:settings")
include(":feature:shell")
