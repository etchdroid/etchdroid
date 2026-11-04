@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

// Read by the build scripts' plugins {} blocks, which Gradle runs before any other code in those
// scripts, so it must be set here. Setting it on every run also overwrites the daemon's stale value.
val isGPlayFlavor = gradle.startParameter.taskNames.any { it.lowercase().contains("gplay") }
System.setProperty("etchdroid.isGPlayFlavor", isGPlayFlavor.toString())

rootProject.name = "EtchDroid"
include(":app")
