import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.nettyBuffer)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholders)
    api(project(":utils"))
    api(libs.kotlinxCoroutinesCore)
    api(libs.kotlinxSerializationCbor)
    api(libs.bundles.mccoroutine)
    api(libs.bundles.lamp)
    api(libs.bundles.adventure)
    api(libs.bundles.configurate)
    api(libs.zstd)
    compileOnly(libs.oshiCore)
    implementation(libs.kotlinReflect)
    implementation(libs.kache)
}
val compileKotlin: KotlinCompile by tasks
compileKotlin.compilerOptions {
    freeCompilerArgs.set(listOf("-Xnon-local-break-continue"))
}