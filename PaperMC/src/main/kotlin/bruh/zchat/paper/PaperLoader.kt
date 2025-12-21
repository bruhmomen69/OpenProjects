package bruh.zchat.paper

import io.papermc.paper.plugin.loader.PluginClasspathBuilder
import io.papermc.paper.plugin.loader.PluginLoader
import io.papermc.paper.plugin.loader.library.impl.MavenLibraryResolver
import org.eclipse.aether.artifact.DefaultArtifact
import org.eclipse.aether.graph.Dependency
import org.eclipse.aether.repository.RemoteRepository

class PaperMCLoader : PluginLoader {
    override fun classloader(classpathBuilder: PluginClasspathBuilder) {
        val resolver = MavenLibraryResolver()

        resolver.addRepository(
            RemoteRepository.Builder(
                "paper",
                "default",
                "https://repo.papermc.io/repository/maven-public/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "sonatype-mirror",
                "default",
                "https://oss.sonatype.org/content/groups/public/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "atlassian-3rdp-mirror",
                "default",
                "https://packages.atlassian.com/maven-3rdparty/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "wso2-mirror",
                "default",
                "https://maven.wso2.org/nexus/content/repositories/releases/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "spigot",
                "default",
                "https://hub.spigotmc.org/nexus/content/repositories/snapshots/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "gcs-central-mirror",
                "default",
                "https://maven-central.storage-download.googleapis.com/maven2/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository
                .Builder(
                    "jcenter",
                    "default",
                    "https://jcenter.bintray.com/"
                ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "atlassian-external-mirror",
                "default",
                "https://packages.atlassian.com/mvn/maven-atlassian-external/"
            ).build()
        )
        resolver.addRepository(
            RemoteRepository.Builder(
                "mulesoft",
                "default",
                "https://repository.mulesoft.org/nexus/content/repositories/public/"
            ).build()
        )

        for (dependencyString in dependencies) {
            resolver.addDependency(Dependency(DefaultArtifact(dependencyString), null))
        }

        classpathBuilder.addLibrary(resolver)
    }

    companion object {
        val dependencies: Array<String> = arrayOf(
            "it.unimi.dsi:fastutil:8.5.16",
            "org.jetbrains.kotlin:kotlin-stdlib-jdk8:2.0.0",
            "org.spongepowered:configurate-hocon:4.1.2",
            "io.github.revxrsal:lamp.common:4.0.0-rc.12",
            "io.github.revxrsal:lamp.bukkit:4.0.0-rc.12",
            "io.github.revxrsal:lamp.brigadier:4.0.0-rc.12",
            "com.mysql:mysql-connector-j:9.5.0",
            "org.xerial:sqlite-jdbc:3.47.1.0",
            "com.zaxxer:HikariCP:7.0.2",
            "io.projectreactor:reactor-core:3.8.1",
            "com.github.ben-manes.caffeine:caffeine:3.1.8"
        )
    }
}