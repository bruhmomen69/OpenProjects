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
    
    // Translation system dependencies
    api("net.kyori:adventure-api:4.23.0")
    api("net.kyori:adventure-text-minimessage:4.23.0")
    implementation("com.mayakapps.kache:kache:2.1.1")
    
    // Configurate for config API
    api("org.spongepowered:configurate-hocon:4.2.0")
    api("org.spongepowered:configurate-extra-kotlin:4.2.0")
    
    // Database API dependencies
    api("com.zaxxer:HikariCP:7.0.2")
    compileOnly("com.mysql:mysql-connector-j:9.5.0")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("org.postgresql:postgresql:42.7.4")
    
    testImplementation(libs.bundles.kotlinxEcosystem)
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.23.0")
}
