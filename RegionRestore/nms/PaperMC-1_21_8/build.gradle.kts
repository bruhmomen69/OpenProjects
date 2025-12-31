plugins {
    kotlin("jvm")
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.19"
}

dependencies {
    implementation(project(":RegionRestore:PaperMC"))
    paperweight.paperDevBundle("1.21.8-R0.1-SNAPSHOT")
    implementation("com.mayakapps.kache:kache:2.1.1")
    compileOnly("io.netty:netty-buffer:4.1.115.Final")
}
