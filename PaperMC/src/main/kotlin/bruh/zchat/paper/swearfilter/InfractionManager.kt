package bruh.zchat.paper.swearfilter

import bruh.zchat.paper.PaperMC
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID

@Serializable
data class InfractionData(
    val infractions: MutableMap<String, Int> = mutableMapOf()
)

class InfractionManager(private val plugin: PaperMC) {
    private val infractionsFolder = File(plugin.dataFolder, "infractions")
    private val json = Json { prettyPrint = true }

    init {
        if (!infractionsFolder.exists()) {
            infractionsFolder.mkdirs()
        }
    }

    private fun getInfractionFile(uuid: UUID): File {
        return File(infractionsFolder, "$uuid.json")
    }

    private fun loadInfractions(uuid: UUID): InfractionData {
        val file = getInfractionFile(uuid)
        return if (file.exists()) {
            json.decodeFromString<InfractionData>(file.readText())
        } else {
            InfractionData()
        }
    }

    private fun saveInfractions(uuid: UUID, data: InfractionData) {
        val file = getInfractionFile(uuid)
        file.writeText(json.encodeToString(InfractionData.serializer(), data))
    }

    fun getInfractions(player: Player, groupName: String): Int {
        val data = loadInfractions(player.uniqueId)
        return data.infractions.getOrDefault(groupName, 0)
    }

    fun addInfraction(player: Player, groupName: String): Int {
        val data = loadInfractions(player.uniqueId)
        val newCount = data.infractions.getOrDefault(groupName, 0) + 1
        data.infractions[groupName] = newCount
        saveInfractions(player.uniqueId, data)
        return newCount
    }
}
