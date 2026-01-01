plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.kotlinPluginSerialization)
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.bundles.kotlinxEcosystem)
    compileOnly(libs.kotlinxCoroutinesCore)
    
    testImplementation(libs.bundles.kotlinxEcosystem)
    testImplementation(libs.kotlinxCoroutinesTest)
    testImplementation(libs.kotlinTest)
    testImplementation(libs.junit)
}
