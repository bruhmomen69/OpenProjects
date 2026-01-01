plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // Configurate
    api("org.spongepowered:configurate-hocon:4.2.0")
    api("org.spongepowered:configurate-extra-kotlin:4.2.0")
}
