plugins {
    kotlin("jvm")
    alias(libs.plugins.paperweightUserdev)
}

dependencies {
    implementation(project(":RegionRestore:PaperMC"))
    paperweight.paperDevBundle("26.1.1.build.10-alpha")
    implementation(libs.kache)
    compileOnly(libs.nettyBuffer)
}

kotlin {
    jvmToolchain(25)
}