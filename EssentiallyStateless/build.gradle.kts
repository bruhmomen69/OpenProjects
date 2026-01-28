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
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // MCCoroutine for Folia
    compileOnly(libs.bundles.mccoroutine)
    
    // Lamp command framework
    compileOnly(libs.bundles.lamp)
    
    // Kyori Adventure
    compileOnly(libs.bundles.adventure)
    
    // Configurate
    compileOnly(libs.bundles.configurate)
    
    compileOnly(libs.kotlinReflect)
    compileOnly(libs.kache)
}

tasks.shadowJar {
    archiveClassifier.set("")
    
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

tasks.build {
    dependsOn("shadowJar")
}
