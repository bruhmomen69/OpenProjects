plugins {
    id("buildsrc.convention.kotlin-jvm")
}

repositories {
    maven("https://repo.spaceio.xyz/repository/maven-public/")
}

dependencies {
    api(project(":utils:translations"))
    api(project(":utils:configapi"))
    
    compileOnly(libs.paperApi)
    compileOnly(libs.kotlinxCoroutinesCore)
    
    // Kotlin reflect for FormInput
    compileOnly(libs.kotlinReflect)
    
    // XSeries and AnvilGUI
    api(libs.xseries)
    implementation(libs.anvilgui)
    
    // Adventure
    api(libs.bundles.adventureMinimal)
}
