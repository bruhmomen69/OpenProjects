plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    // Include utils modules but exclude transitive dependencies
    // that are provided by Paper's library loader
    implementation(project(":utils:core")) {
        isTransitive = false
    }
    implementation(project(":utils:translations")) {
        isTransitive = false
    }
    implementation(project(":utils:common-server-commands")) {
        isTransitive = false
    }
    implementation(project(":utils:configapi")) {
        isTransitive = false
    }
    implementation(project(":utils:menuapi")) {
        isTransitive = false
    }
    
    // Paper API
    compileOnly(libs.paperApi)
    
    // Netty for buffer operations
    compileOnly(libs.nettyBuffer)
    
    // Coroutines - provided by Paper library loader
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // MCCoroutine for Folia - provided by Paper library loader
    compileOnly(libs.bundles.mccoroutine)
    
    // Lamp command framework - provided by Paper library loader
    compileOnly(libs.bundles.lamp)
    
    // Kyori Adventure - provided by Paper library loader
    compileOnly(libs.bundles.adventure)
    
    // Configurate - provided by Paper library loader
    compileOnly(libs.bundles.configurate)
    
    // Kotlin reflect - provided by Paper library loader
    compileOnly(libs.kotlinReflect)
}

tasks.shadowJar {
    archiveClassifier.set("")
    
    // Exclude all dependencies that Paper library loader provides
    dependencies {
        exclude(dependency("org.jetbrains.kotlin:.*"))
        exclude(dependency("org.jetbrains.kotlinx:.*"))
        exclude(dependency("io.github.revxrsal:.*"))
        exclude(dependency("net.kyori:.*"))
        exclude(dependency("org.spongepowered:.*"))
        exclude(dependency("com.github.shynixn.mccoroutine:.*"))
        exclude(dependency("io.leangen.geantyref:.*"))
        exclude(dependency("org.yaml:snakeyaml"))
        exclude(dependency("com.google.code.gson:gson"))
        exclude(dependency("com.google.guava:.*"))
        exclude(dependency("com.google.errorprone:.*"))
        exclude(dependency("org.checkerframework:.*"))
        exclude(dependency("io.netty:.*"))
        exclude(dependency("org.slf4j:.*"))
        exclude(dependency("com.mayakapps.kache:.*"))
    }
    
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

tasks.build {
    dependsOn("shadowJar")
}
