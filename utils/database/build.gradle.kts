plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":utils:configapi"))
    
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // Database
    api(libs.hikaricp)
    compileOnly(libs.mysql)
    compileOnly(libs.sqlite)
    compileOnly(libs.postgresql)
}
