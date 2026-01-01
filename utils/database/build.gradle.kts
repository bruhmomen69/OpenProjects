plugins {
    id("buildsrc.convention.kotlin-jvm")
}

dependencies {
    api(project(":utils:configapi"))
    
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    
    // Database
    api("com.zaxxer:HikariCP:7.0.2")
    compileOnly("com.mysql:mysql-connector-j:9.5.0")
    compileOnly("org.xerial:sqlite-jdbc:3.47.1.0")
    compileOnly("org.postgresql:postgresql:42.7.4")
}
