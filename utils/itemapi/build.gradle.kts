plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":utils:menuapi"))
    api(project(":utils:database"))
    api(project(":utils:configapi"))
    
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // Caffeine cache
    api(libs.caffeine)
}
