@file:Suppress("UnstableApiUsage")

val jitpackUrl = "https://jitpack.io"

pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven(url = jitpackUrl)
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = jitpackUrl)
    }
}
rootProject.name = "EtchDroid"
include(":app")
