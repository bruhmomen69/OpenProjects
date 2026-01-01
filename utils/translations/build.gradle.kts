plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    compileOnly(libs.paperApi)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholders)
    
    // Coroutines
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // Adventure
    api(libs.bundles.adventureMinimal)
    
    // Cache
    implementation(libs.kache)
    
    // Testing
    testImplementation(libs.kotlinTest)
    testImplementation(libs.junit)
    testImplementation(libs.adventureTextSerializerPlain)
    testImplementation(libs.kotlinxCoroutinesTest)
}
