plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    implementation(project(":utils:translations"))
    implementation(project(":utils:menuapi"))
    
    compileOnly(libs.paperApi)
    
    // Coroutines
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // MCCoroutine for Folia
    compileOnly(libs.bundles.mccoroutine)
    
    // Lamp command framework
    compileOnly(libs.bundles.lamp)
    
    // Kyori Adventure
    compileOnly(libs.bundles.adventure)
    
    // Configurate
    compileOnly(libs.bundles.configurate)
    
    compileOnly(libs.kotlinReflect)
}
