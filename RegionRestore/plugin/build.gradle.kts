plugins {
    id("buildsrc.convention.kotlin-jvm")
    alias(libs.plugins.shadow)
    alias(libs.plugins.runPaper)
}

version = "1.0.0"

dependencies {
    implementation(project(":RegionRestore:api"))
    implementation(project(":RegionRestore:PaperMC"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_4"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_5"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_6"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_7"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_8"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_9"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_10"))
    implementation(project(":RegionRestore:nms:PaperMC-1_21_11"))
    implementation(project(":RegionRestore:nms:PaperMC-26_1"))
    compileOnly(libs.paperApi)
    compileOnly(libs.placeholderapi)
    compileOnly(libs.miniplaceholders)
    compileOnly(libs.oshiCore)
    implementation(libs.mccoroutineFoliaCore)
}

tasks.shadowJar {
    relocate("kotlinx.", "bruh.regionrestore.lib.kotlinx.")
    relocate("revxrsal.commands", "bruh.regionrestore.lib.cmd.api")
    relocate("com.github.shynixn.mccoroutine", "bruh.regionrestore.lib.mccoroutine")
    relocate("org.intellij.", "bruh.regionrestore.lib.intellij.")
    relocate("org.jetbrains.", "bruh.regionrestore.lib.jetbrains.")
    relocate("_COROUTINE.", "bruh.regionrestore.cdata.")
    relocate("com.mayakapps.kache.", "bruh.regionrestore.lib.com.mayakapps.kache.")
    relocate("org.spongepowered.configurate", "bruh.regionrestore.lib.configurate")
    relocate("io.leangen.geantyref", "bruh.regionrestore.lib.geantyref")
    relocate("org.yaml.snakeyaml", "bruh.regionrestore.lib.snakeyaml")
    relocate("com.google.gson", "bruh.regionrestore.lib.gson")
    relocate("com.google.gson.stream", "bruh.regionrestore.lib.gson.stream")
    
    manifest {
        attributes("paperweight-mappings-namespace" to "mojang")
    }
}

tasks.runServer {
    // Configure the Minecraft version for our task.
    // This is the only required configuration besides applying the plugin.
    // Your plugin's jar (or shadowJar if present) will be used automatically.
    minecraftVersion("1.21.11")

    downloadPlugins {
        url("https://ci.lucko.me/job/spark/524/artifact/spark-bukkit/build/libs/spark-1.10.172-bukkit.jar")
    }
}

runPaper.folia.registerTask()

tasks.build {
    dependsOn("shadowJar")
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
    filesMatching("paper-plugin.yml") {
        expand(props)
    }
}

