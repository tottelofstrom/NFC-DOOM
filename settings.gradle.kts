// Top-level Gradle settings for the ImplantDoom project.
//
// ImplantDoom is a single-module Android application. The NFC implant is used
// only as passive cartridge storage; the phone runs the game engine.
pluginManagement {
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
    // Fail if a module declares its own repositories: keep all dependency
    // resolution centralised here so the build stays reproducible and offline-friendly.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ImplantDoom"
include(":app")
