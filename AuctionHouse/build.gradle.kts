plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
    alias(libs.plugins.runPaper)
}

group = "bruh.auctionhouse"
version = "1.0.0"

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinStdlib)
    
    // Kyori Adventure
    compileOnly(libs.bundles.adventure)
    
    // Configurate
    compileOnly(libs.bundles.configurateHocon)
    
    // Lamp
    implementation(libs.bundles.lamp)
    
    // Logging
    compileOnly(libs.bundles.slf4j)
    
    // Vault
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
    
    // PlaceholderAPI
    compileOnly("me.clip:placeholderapi:2.11.6")
    
    // Database
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    compileOnly(libs.hikaricp)
    compileOnly(libs.caffeine)

    // Coroutines
    implementation(libs.bundles.mccoroutine)
    implementation(libs.kotlinxCoroutinesCore)
    // NEW
    implementation(libs.configurateExtraKotlin)
    
    // Project utils
    implementation(project(":utils"))
    implementation(libs.kotlinxSerializationJson)
}

tasks.shadowJar {
    mergeServiceFiles()
    
    relocate("com.github.shynixn", "bruh.auctionhouse.dependencies.com.github.shynixn")
    relocate("kotlinx.", "bruh.auctionhouse.lib.kotlinx.")
    relocate("revxrsal.commands", "bruh.auctionhouse.lib.cmd.api")
    relocate("org.intellij.", "bruh.auctionhouse.lib.intellij.")
    relocate("org.jetbrains.", "bruh.auctionhouse.lib.jetbrains.")
    relocate("_COROUTINE.", "bruh.auctionhouse.cdata.")
    relocate("org.spongepowered.configurate", "bruh.auctionhouse.lib.configurate")
    relocate("io.leangen.geantyref", "bruh.auctionhouse.lib.geantyref")
    relocate("org.yaml.snakeyaml", "bruh.auctionhouse.lib.snakeyaml")
    relocate("com.google.gson", "bruh.auctionhouse.lib.gson")
    
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
}

tasks.runServer {
    minecraftVersion("1.21.11")
    
    downloadPlugins {
        // Vault for economy support
        url("https://github.com/MilkBowl/Vault/releases/download/1.7.3/Vault.jar")
        url("https://github.com/EssentialsX/Essentials/releases/download/2.21.2/EssentialsX-2.21.2.jar")
    }
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