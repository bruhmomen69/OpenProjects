plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":utils:menuapi"))
    api(project(":utils:database"))
    
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // Caffeine cache
    api(libs.caffeine)
}
