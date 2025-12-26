package bruh.zchat.paper.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.function.Consumer
import org.slf4j.LoggerFactory

/**
 * Modrinth update checker that fetches the latest compatible `version_number` for a project.
 */
class ModrinthUpdateChecker(
    private val projectId: String,
    private val loader: String,
    private val minecraftVersion: String? = null
) {

    private val logger = LoggerFactory.getLogger(ModrinthUpdateChecker::class.java)

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Fetches the latest compatible Modrinth version and calls `consumer` with the version string.
     */
    fun checkVersion(consumer: (String) -> Unit) {
        try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL.replace("{id}", projectId)))
                .header("Accept", "application/json")
                .header("User-Agent", "ZealousChat")
                .GET()
                .build()

            client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAcceptAsync { response ->
                    if (response.statusCode() != 200) {
                        logger.warn(
                            "Modrinth update check failed (statusCode={}, projectId={}, loader={}, minecraftVersion={})",
                            response.statusCode(),
                            projectId,
                            loader,
                            minecraftVersion
                        )
                        return@thenAcceptAsync
                    }

                    try {
                        val versionsArray = json.parseToJsonElement(response.body()).jsonArray
                        val latestVersion = getLatestVersion(versionsArray) ?: return@thenAcceptAsync
                        consumer(latestVersion)
                    } catch (e: Exception) {
                        logger.warn(
                            "Modrinth update check failed to parse response (projectId={}, loader={}, minecraftVersion={})",
                            projectId,
                            loader,
                            minecraftVersion,
                            e
                        )
                    }
                }
                .exceptionally {
                    logger.warn(
                        "Modrinth update check failed (projectId={}, loader={}, minecraftVersion={})",
                        projectId,
                        loader,
                        minecraftVersion,
                        it
                    )
                    null
                }
        } catch (e: Exception) {
            logger.warn(
                "Modrinth update check failed to start (projectId={}, loader={}, minecraftVersion={})",
                projectId,
                loader,
                minecraftVersion,
                e
            )
        }
    }

    /**
     * Java-friendly overload.
     */
    fun checkVersion(consumer: Consumer<String>) {
        checkVersion(consumer::accept)
    }

    protected fun getLatestVersion(versions: JsonArray): String? {
        return versions
            .asSequence()
            .mapNotNull { it.asJsonObjectOrNull() }
            .filter(::isVersionCompatible)
            .mapNotNull { obj -> obj["version_number"]?.jsonPrimitive?.content }
            .map(::getRawVersion)
            .maxWithOrNull(Comparator { a, b -> compareRawVersions(a, b) })
    }

    protected fun isVersionCompatible(version: JsonObject): Boolean {
        val versions = version["game_versions"]?.jsonArray ?: return false
        val loaders = version["loaders"]?.jsonArray ?: return false

        val minecraftOk = minecraftVersion == null || versions.any { it.jsonPrimitive.content == minecraftVersion }
        val loaderOk = loaders.any { it.jsonPrimitive.content == loader }
        return minecraftOk && loaderOk
    }

    private fun compareRawVersions(a: String, b: String): Int {
        val va = ParsedVersion.parse(a)
        val vb = ParsedVersion.parse(b)
        return va.compareTo(vb)
    }

    private data class ParsedVersion(
        val coreNumbers: List<Int>,
        val preRelease: String?
    ) : Comparable<ParsedVersion> {

        override fun compareTo(other: ParsedVersion): Int {
            val max = maxOf(coreNumbers.size, other.coreNumbers.size)
            for (i in 0 until max) {
                val ai = coreNumbers.getOrNull(i) ?: 0
                val bi = other.coreNumbers.getOrNull(i) ?: 0
                if (ai != bi) return ai.compareTo(bi)
            }

            val aPre = preRelease
            val bPre = other.preRelease

            if (aPre == null && bPre == null) return 0
            if (aPre == null) return 1
            if (bPre == null) return -1
            return aPre.compareTo(bPre)
        }

        companion object {
            fun parse(version: String): ParsedVersion {
                val (core, pre) = version.split("-", limit = 2).let {
                    it[0] to it.getOrNull(1)
                }

                val nums = core.split('.')
                    .mapNotNull { it.toIntOrNull() }

                return ParsedVersion(nums, pre)
            }
        }
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? {
        return try {
            this.jsonObject
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val API_URL = "https://api.modrinth.com/v2/project/{id}/version"

        /**
         * Extracts the core version from Modrinth version strings (e.g. "paper-1.2+1.21" -> "1.2").
         */
        fun getRawVersion(version: String): String {
            if (version.isEmpty()) return version

            val stripped = version.replace(Regex("^\\D+"), "")
            return stripped.split("+", limit = 2)[0]
        }

        /**
         * Returns `true` when `latestVersion` is newer than `currentVersion`.
         */
        fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
            val latest = ParsedVersion.parse(getRawVersion(latestVersion))
            val current = ParsedVersion.parse(getRawVersion(currentVersion))
            return latest > current
        }
    }
}
