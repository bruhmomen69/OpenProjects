plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    implementation(project(":utils"))
    compileOnly(libs.paperApi)
    compileOnly(libs.nettyBuffer)
    
    // Coroutines
    api(libs.kotlinxCoroutinesCore)
    
    // MCCoroutine for Folia
    api(libs.bundles.mccoroutine)
    
    // Lamp command framework
    api(libs.bundles.lamp)
    
    // Kyori Adventure
    api(libs.bundles.adventure)
    
    // Configurate
    api(libs.bundles.configurate)
    
    implementation(libs.kotlinReflect)
    implementation(libs.kache)
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
