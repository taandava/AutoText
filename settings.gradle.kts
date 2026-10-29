import org.gradle.api.initialization.resolve.RepositoriesMode

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Scoped suppression: `repositoriesMode` / `RepositoriesMode` are still annotated @Incubating
// in Gradle 9.8.2. FAIL_ON_PROJECT_REPOS is the recommended hygiene setting (it prevents
// sub-projects from sneakily adding their own repositories), so we keep it and scope the
// suppression to this declaration only instead of suppressing the whole build script.
@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AutoText"
include(":app")
