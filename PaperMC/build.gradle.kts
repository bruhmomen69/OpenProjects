import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.gradleup.shadow") version "8.3.0"
    id("xyz.jpenilla.run-paper") version "2.3.1"
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "lol.mcplugs.minimessagechatplugin"
version = "1.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc-repo"
    }
    maven("https://oss.sonatype.org/content/groups/public/") {
        name = "sonatype"
    }
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    // Kyori Adventure
    compileOnly("net.kyori:adventure-api:4.23.0")
    compileOnly("net.kyori:adventure-text-serializer-legacy:4.23.0")
    compileOnly("net.kyori:adventure-text-serializer-gson:4.23.0")
    compileOnly("net.kyori:adventure-text-minimessage:4.23.0")

    // Configurate
    compileOnly("org.spongepowered:configurate-hocon:4.1.2")

    // Lamp
    compileOnly("io.github.revxrsal:lamp.common:4.0.0-rc.12")
    compileOnly("io.github.revxrsal:lamp.bukkit:4.0.0-rc.12")

    // Logging (provided by the server)
    compileOnly("org.slf4j:slf4j-api:2.0.7")
    compileOnly("org.slf4j:slf4j-simple:2.0.7")

    // PlaceholderAPI integration (optional)
    compileOnly("me.clip:placeholderapi:2.11.6")

    // Database dependencies
    implementation("org.flywaydb:flyway-core:11.19.0")
    implementation("org.flywaydb:flyway-mysql:11.19.0")
    compileOnly("com.mysql:mysql-connector-j:9.5.0")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("com.zaxxer:HikariCP:7.0.2")

    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.22.0")
    implementation("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.22.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation(project(":utils"))
    implementation(libs.kotlinxSerialization)
}

tasks {
  runServer {
    // Configure the Minecraft version for our task.
    // This is the only required configuration besides applying the plugin.
    // Your plugin's jar (or shadowJar if present) will be used automatically.
    minecraftVersion("1.21")
  }
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    relocate("com.github.shynixn", "bruh.zchat.paper.dependencies.com.github.shynixn")
    relocate("com.fasterxml.jackson", "bruh.zchat.paper.dependencies.com.fasterxml.jackson")
    relocate("org.flywaydb", "bruh.zchat.paper.dependencies.org.flywaydb")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}
