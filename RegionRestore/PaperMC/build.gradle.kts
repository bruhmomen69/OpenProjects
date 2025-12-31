plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.netty:netty-buffer:4.1.115.Final")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    api("org.jetbrains.kotlinx:kotlinx-serialization-cbor:1.8.0")
    api("com.github.shynixn.mccoroutine:mccoroutine-folia-api:2.21.0")
    api("com.github.shynixn.mccoroutine:mccoroutine-folia-core:2.21.0")
    api("io.github.revxrsal:lamp.common:4.0.0-rc.10")
    api("io.github.revxrsal:lamp.bukkit:4.0.0-rc.10")
    api("net.kyori:adventure-api:4.23.0")
    api("net.kyori:adventure-text-serializer-legacy:4.23.0")
    api("net.kyori:adventure-text-serializer-gson:4.23.0")
    api("net.kyori:adventure-text-minimessage:4.23.0")
    api("org.spongepowered:configurate-yaml:4.2.0")
    api("org.spongepowered:configurate-hocon:4.2.0")
    api("org.spongepowered:configurate-extra-kotlin:4.2.0")
    api("com.github.luben:zstd-jni:1.5.7-2")
    implementation(kotlin("reflect"))
    implementation("com.mayakapps.kache:kache:2.1.1")
}
