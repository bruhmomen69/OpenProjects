// The settings file is the entry point of every Gradle build.
// Its primary purpose is to define the subprojects.
// It is also used for some aspects of project-wide configuration, like managing plugins, dependencies, etc.
// https://docs.gradle.org/current/userguide/settings_file_basics.html

dependencyResolutionManagement {
    // Use Maven Central as the default repository (where Gradle will download dependencies) in all subprojects.
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
    }
}

plugins {
    // Use the Foojay Toolchains plugin to automatically download JDKs required by subprojects.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

// Chat
include(":app")
// Utils submodules
include(":utils")
include(":utils:core")
include(":utils:configapi")
include(":utils:database")
include(":utils:translations")
include(":utils:menuapi")
include(":utils:itemapi")
include(":utils:common-server-commands")
include(":ZealousChat")
// EssentiallyStateless
include(":EssentiallyStateless")
// RegionRestore
include(":RegionRestore:PaperMC")
include(":RegionRestore:api")
include(":RegionRestore:nms:PaperMC-1_21_4")
include(":RegionRestore:nms:PaperMC-1_21_5")
include(":RegionRestore:nms:PaperMC-1_21_6")
include(":RegionRestore:nms:PaperMC-1_21_7")
include(":RegionRestore:nms:PaperMC-1_21_8")
include(":RegionRestore:nms:PaperMC-1_21_9")
include(":RegionRestore:nms:PaperMC-1_21_10")
include(":RegionRestore:nms:PaperMC-1_21_11")
include(":RegionRestore:nms:PaperMC-26_1")
include(":RegionRestore:plugin")
// AuctionHouse
include(":AuctionHouse")

rootProject.name = "OpenProjects"