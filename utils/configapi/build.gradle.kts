plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // Configurate
    api(libs.bundles.configurateHocon)
}
