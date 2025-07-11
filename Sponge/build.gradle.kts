import org.spongepowered.gradle.plugin.config.PluginLoaders
import org.spongepowered.plugin.metadata.model.PluginDependency

plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.spongepowered.gradle.plugin") version "2.2.0"
}

group = "lol.mcplugs.minimessagechatplugin"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/") {
        name = "spongepowered-repo"
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

    implementation(project(":app"))
    implementation(project(":utils"))
}

sponge {
    apiVersion("13.1.0-SNAPSHOT")
    license("All-Rights-Reserved")
    loader {
        name(PluginLoaders.JAVA_PLAIN)
        version("1.0")
    }
    plugin("sponge") {
        displayName("Sponge")
        entrypoint("lol.mcplugs.minimessagechatplugin.sponge.Sponge")
        description("My plugin description")
        links {
            // homepage("https://spongepowered.org")
            // source("https://spongepowered.org/source")
            // issues("https://spongepowered.org/issues")
        }
        dependency("spongeapi") {
            loadOrder(PluginDependency.LoadOrder.AFTER)
            optional(false)
        }
    }
}

val javaTarget = 21 // Sponge targets a minimum of Java 21
kotlin {
    jvmToolchain(javaTarget)
}

tasks.build {
    dependsOn("shadowJar")
}

// Make sure all tasks which produce archives (jar, sources jar, javadoc jar, etc) produce more consistent output
tasks.withType<AbstractArchiveTask>().configureEach {
    isReproducibleFileOrder = true
    isPreserveFileTimestamps = false
}
