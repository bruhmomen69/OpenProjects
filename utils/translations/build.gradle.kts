plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.11.6")
    compileOnly("io.github.miniplaceholders:miniplaceholders-api:3.0.1")
    
    // Coroutines
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // Adventure
    api("net.kyori:adventure-api:4.23.0")
    api("net.kyori:adventure-text-minimessage:4.23.0")
    
    // Cache
    implementation("com.mayakapps.kache:kache:2.1.1")
    
    // Testing
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("net.kyori:adventure-text-serializer-plain:4.23.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
}
