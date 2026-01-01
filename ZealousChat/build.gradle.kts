import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.kotlinPluginSerialization)
}

group = "bruh.zchat.minimessagechatplugin"
version = "1.2.7"

repositories {
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinStdlib)

    // Kyori Adventure
    compileOnly(libs.bundles.adventure)

    // Configurate
    compileOnly(libs.configurateHocon)

    // Lamp
    compileOnly(libs.bundles.lamp)

    // Logging (provided by the server)
    compileOnly(libs.bundles.slf4j)

    // PlaceholderAPI integration (optional)
    compileOnly(libs.placeholderapi)

    // Database dependencies (JDBC drivers - HikariCP is provided by utils)
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    compileOnly(libs.hikaricp)
    compileOnly(libs.caffeine)

    implementation(libs.lettuce)

    implementation(libs.bundles.mccoroutine)
    implementation(libs.kotlinxCoroutinesCore)

    implementation(project(":utils"))
    implementation(libs.kotlinxSerializationJson)
}

tasks.named<ShadowJar>("shadowJar") {
    mergeServiceFiles()

    relocate("com.github.shynixn", "bruh.zchat.paper.dependencies.com.github.shynixn")
    relocate("com.fasterxml.jackson", "bruh.zchat.paper.dependencies.com.fasterxml.jackson")
    relocate("io.lettuce", "bruh.zchat.paper.dependencies.io.lettuce")
    relocate("io.netty", "bruh.zchat.paper.dependencies.io.netty")

    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("org/reactivestreams/*")

    dependencies {
        exclude(dependency("org.reactivestreams:reactive-streams:.*"))
        exclude(dependency("io.projectreactor:reactor-core:.*"))

    }
}

val targetJavaVersion = 21
kotlin {
    jvmToolchain(targetJavaVersion)
}

tasks.build {
    dependsOn("shadowJar")
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
