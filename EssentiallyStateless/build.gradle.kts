plugins {
    id("buildsrc.convention.kotlin-jvm")
    id("com.gradleup.shadow") version "9.3.0"
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":utils"))
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-buffer:4.1.115.Final")
    
    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // MCCoroutine for Folia
    api("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.21.0")
    api("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.21.0")
    
    // Lamp command framework
    api("io.github.revxrsal:lamp.common:4.0.0-rc.10")
    api("io.github.revxrsal:lamp.bukkit:4.0.0-rc.10")
    
    // Kyori Adventure
    api("net.kyori:adventure-api:4.23.0")
    api("net.kyori:adventure-text-serializer-legacy:4.23.0")
    api("net.kyori:adventure-text-serializer-gson:4.23.0")
    api("net.kyori:adventure-text-minimessage:4.23.0")
    
    // Configurate
    api("org.spongepowered:configurate-yaml:4.2.0")
    api("org.spongepowered:configurate-hocon:4.2.0")
    api("org.spongepowered:configurate-extra-kotlin:4.2.0")
    
    implementation(kotlin("reflect"))
    implementation("com.mayakapps.kache:kache:2.1.1")
}

tasks.shadowJar {
    archiveClassifier.set("")
    
    relocate("kotlinx.", "bruh.essentiallystateless.lib.kotlinx.")
    relocate("revxrsal.commands", "bruh.essentiallystateless.lib.cmd.api")
    relocate("com.github.shynixn.mccoroutine", "bruh.essentiallystateless.lib.mccoroutine")
    relocate("org.intellij.", "bruh.essentiallystateless.lib.intellij.")
    relocate("org.jetbrains.", "bruh.essentiallystateless.lib.jetbrains.")
    relocate("_COROUTINE.", "bruh.essentiallystateless.cdata.")
    relocate("com.mayakapps.kache.", "bruh.essentiallystateless.lib.com.mayakapps.kache.")
    relocate("org.spongepowered.configurate", "bruh.essentiallystateless.lib.configurate")
    relocate("io.leangen.geantyref", "bruh.essentiallystateless.lib.geantyref")
    relocate("org.yaml.snakeyaml", "bruh.essentiallystateless.lib.snakeyaml")
    relocate("com.google.gson", "bruh.essentiallystateless.lib.gson")
    relocate("com.google.gson.stream", "bruh.essentiallystateless.lib.gson.stream")
    
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

tasks.build {
    dependsOn("shadowJar")
}
