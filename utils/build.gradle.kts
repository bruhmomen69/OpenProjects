plugins {
    // Apply the shared build logic from a convention plugin.
    // The shared code is located in `buildSrc/src/main/kotlin/kotlin-jvm.gradle.kts`.
    id("buildsrc.convention.kotlin-jvm")
    // Apply Kotlin Serialization plugin from `gradle/libs.versions.toml`.
    alias(libs.plugins.kotlinPluginSerialization)
}

repositories {
    maven("https://repo.spaceio.xyz/repository/maven-public/")
}

dependencies {
    // Apply the kotlinx bundle of dependencies from the version catalog (`gradle/libs.versions.toml`).
    api("com.github.cryptomorin:XSeries:13.6.0")
    implementation("net.wesjd:anvilgui:2.0.4-20251228.114051-1")
    api(kotlin("reflect"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    compileOnly(libs.bundles.kotlinxEcosystem)
    testImplementation(libs.bundles.kotlinxEcosystem)
    testImplementation(kotlin("test"))
}