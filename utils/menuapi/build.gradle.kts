plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    maven("https://repo.spaceio.xyz/repository/maven-public/")
}

dependencies {
    api(project(":utils:translations"))
    api(project(":utils:configapi"))
    
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // Kotlin reflect for FormInput
    api(kotlin("reflect"))
    
    // XSeries and AnvilGUI
    api("com.github.cryptomorin:XSeries:13.6.0")
    implementation("net.wesjd:anvilgui:2.0.4-20251228.114051-1")
    
    // Adventure
    api("net.kyori:adventure-api:4.23.0")
    api("net.kyori:adventure-text-minimessage:4.23.0")
}
