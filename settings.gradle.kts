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

rootProject.name = "Picfort"
include(":app")
include(":benchmark")
include(":core:model")
include(":core:ui")
include(":core:database")
include(":core:network")
include(":feature:shopping")
include(":feature:events")
include(":feature:family")
include(":feature:wall")
include(":feature:frame")
include(":feature:timeline")
