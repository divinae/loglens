rootProject.name = "loglens"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
            maven("https://www.jetbrains.com/intellij-repository/releases")
        }
        // Jewel 0.37 targets the IntelliJ 262 (2026.2 EAP) platform, whose `icons` artifact
        // (262.x) is published only to the snapshots repo, not releases. Scoped to the IntelliJ
        // group so we don't pull unrelated snapshot builds.
        maven("https://www.jetbrains.com/intellij-repository/snapshots") {
            mavenContent { includeGroupAndSubgroups("com.jetbrains.intellij") }
        }
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":composeApp")