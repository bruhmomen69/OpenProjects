package bruh.regionrestore.cmd

import bruh.regionrestore.cloner.*
import bruh.regionrestore.config.RegionRestoreConfig
import bruh.regionrestore.nms.PaperNmsAdapter
import bruh.regionrestore.notification.AudienceScope
import bruh.regionrestore.template.TemplateRepository
import bruh.regionrestore.timer.RestoreJob
import bruh.regionrestore.timer.SchedulerService
import bruh.regionrestore.translations.CommandMessages
import bruh.regionrestore.translations.GuiMessages
import bruh.zchat.utils.menuapi.*
import bruh.zchat.utils.translations.TranslationAPI
import com.cryptomorin.xseries.XMaterial
import com.github.shynixn.mccoroutine.folia.entityDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.annotation.Command
import revxrsal.commands.annotation.Default
import revxrsal.commands.annotation.Optional
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.floor

/**
 * Target for timer operations - either an instance ID or a template name.
 */
sealed class TimerTarget {
    data class ByInstanceId(val instanceId: java.util.UUID) : TimerTarget()
    data class ByTemplateName(val templateName: String) : TimerTarget()
}

@Command("regionrestore", "rr", "arena")
class RegionRestoreCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val schedulerService: SchedulerService,
    private val config: RegionRestoreConfig,
    private val massClonerService: MassClonerService,
    private val menuAPI: MenuAPI,
    private val plugin: JavaPlugin,
    private val translations: TranslationAPI
) {
    
    /** Gets a GUI string (for menu titles, lore, etc. that need strings) */
    private fun tGui(key: GuiMessages) = translations.getString(key)
    
    @Subcommand("gui")
    @CommandPermission("regionrestore.gui")
    suspend fun openGui(actor: BukkitCommandActor) {
        val player = actor.requirePlayer()

        // Snapshot template names once when opening the GUI to avoid doing
        // blocking IO on the main thread inside menu builders.
        val templateNames = templateRepository.listTemplates()

        // Snapshot template metadata for GUI display
        val templateVersionsByName = templateNames.associateWith {
            templateRepository.getTemplateVersions(it) ?: emptyList()
        }
        val activeVersionIdsByName = templateNames.associateWith {
            templateRepository.loadActiveTemplateVersion(it)?.versionId ?: 0
        }

        val ui = menuAPI.menuTree {
            title(tGui(GuiMessages.MAIN_TITLE))

            // ========================= TEMPLATES =========================
            submenu("templates", tGui(GuiMessages.TEMPLATES_TITLE), XMaterial.BOOK) {
                paginated(28)

                // Create new template action
                action(
                    "create_template",
                    tGui(GuiMessages.CREATE_TEMPLATE_TITLE),
                    XMaterial.ANVIL,
                    listOf(tGui(GuiMessages.CREATE_TEMPLATE_DESC)),
                    returnLevels = 1
                ) { p ->
                    // Prompt for template name
                    val nameResult = menuAPI.promptText(
                        p,
                        tGui(GuiMessages.CREATE_TEMPLATE_NAME_PROMPT),
                        initialText = "",
                        validator = { text ->
                            if (text.isNotBlank()) InputValidation.Valid
                            else InputValidation.Invalid(tGui(GuiMessages.INPUT_EMPTY))
                        }
                    )
                    val name = nameResult.getOrNull()
                    if (!nameResult.isSuccess || name == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    // Prompt for min X
                    val minXResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MIN_X_PROMPT))
                    val minX = minXResult.getOrNull()
                    if (!minXResult.isSuccess || minX == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    // Prompt for min Z
                    val minZResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MIN_Z_PROMPT))
                    val minZ = minZResult.getOrNull()
                    if (!minZResult.isSuccess || minZ == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    // Prompt for max X
                    val maxXResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MAX_X_PROMPT))
                    val maxX = maxXResult.getOrNull()
                    if (!maxXResult.isSuccess || maxX == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    // Prompt for max Z
                    val maxZResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MAX_Z_PROMPT))
                    val maxZ = maxZResult.getOrNull()
                    if (!maxZResult.isSuccess || maxZ == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    // Create template using the same logic as the command
                    createTemplate(p, name, minX, minZ, maxX, maxZ, p.world.name)
                    null
                }

                dynamicItems { _ ->
                    templateNames.map { templateName ->
                        val versions = templateVersionsByName[templateName] ?: emptyList()
                        val activeVersionId = activeVersionIdsByName[templateName] ?: 0
                        val activeVersion = versions.firstOrNull { it.versionId == activeVersionId }
                            ?: versions.maxByOrNull { it.versionId }
                        val description = activeVersion?.description?.takeIf { it.isNotBlank() }
                            ?: tGui(GuiMessages.TEMPLATE_NO_DESCRIPTION)
                        val infoLore = listOf(
                            tGui(GuiMessages.TEMPLATE_INFO_LINE).replace("<name>", templateName),
                            tGui(GuiMessages.TEMPLATE_DESC_LINE).replace("<description>", description)
                        )

                        submenuNode("template_$templateName", tGui(GuiMessages.TEMPLATE_ITEM_TITLE).replace("<name>", templateName), XMaterial.PAPER) {
                            display(
                                "info",
                                tGui(GuiMessages.TEMPLATE_INFO_TITLE),
                                XMaterial.BOOK,
                                infoLore
                            )

                            submenu("versions", tGui(GuiMessages.VERSIONS_TITLE), XMaterial.BOOKSHELF) {
                                paginated(28)

                                dynamicItems { _ ->
                                    versions.map { version ->
                                        val isActive = version.versionId == activeVersionId
                                        val title = if (isActive) {
                                            tGui(GuiMessages.VERSION_ACTIVE).replace("<version>", version.versionId.toString())
                                        } else {
                                            tGui(GuiMessages.VERSION_NORMAL).replace("<version>", version.versionId.toString())
                                        }
                                        val lore = listOf(
                                            tGui(GuiMessages.VERSION_CREATED).replace("<timestamp>", java.time.Instant.ofEpochMilli(version.createdAt).toString()),
                                            tGui(GuiMessages.VERSION_MINECRAFT).replace("<version>", version.minecraftVersion),
                                            tGui(GuiMessages.VERSION_DESCRIPTION).replace("<description>", version.description)
                                        )

                                        displayNode(
                                            id = "version_${templateName}_${version.versionId}",
                                            title = title,
                                            material = if (isActive) XMaterial.LIME_DYE else XMaterial.PAPER,
                                            description = lore
                                        )
                                    }
                                }
                            }

                            action(
                                "restore_original",
                                tGui(GuiMessages.RESTORE_ORIGINAL_TITLE),
                                XMaterial.EMERALD_BLOCK,
                                listOf(tGui(GuiMessages.RESTORE_ORIGINAL_DESC))
                            ) { p ->
                                restoreTemplate(p, templateName)
                            }

                            action(
                                "restore_here",
                                tGui(GuiMessages.RESTORE_HERE_TITLE),
                                XMaterial.EMERALD,
                                listOf(tGui(GuiMessages.RESTORE_HERE_DESC))
                            ) { p ->
                                val worldName = p.world.name
                                val chunk = p.location.chunk
                                restoreAt(p, templateName, worldName, chunk.x * 16, chunk.z * 16, "active")
                                null
                            }

                            action(
                                "create_instance_original",
                                tGui(GuiMessages.CREATE_INSTANCE_TITLE),
                                XMaterial.ANVIL,
                                listOf(tGui(GuiMessages.CREATE_INSTANCE_DESC)),
                                returnLevels = 1
                            ) { p ->
                                val templateVersion = templateRepository.loadActiveTemplateVersion(templateName)
                                if (templateVersion == null) {
                                    p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_NOT_FOUND) {
                                        unparsed("name", templateName)
                                    })
                                    return@action null
                                }

                                if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
                                    p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_VERSION_MISMATCH_WARNING) {
                                        unparsed("template_version", templateVersion.minecraftVersion)
                                        unparsed("server_version", nmsAdapter.minecraftVersion)
                                    })
                                }

                                val originChunkX = templateVersion.data.minChunkX
                                val originChunkZ = templateVersion.data.minChunkZ

                                val instanceConfig = InstanceConfig(
                                    restoreOnBoot = false,
                                    restoreOnVacate = false,
                                    restoreIntervalSeconds = null,
                                    restoreAudienceScope = config.notifications.defaultAudienceScope,
                                    updateLight = null
                                )

                                val instance = RegionInstance.create(
                                    instanceId = java.util.UUID.randomUUID(),
                                    worldName = p.world.name,
                                    templateName = templateName,
                                    versionId = templateVersion.versionId,
                                    originChunkX = originChunkX,
                                    originChunkZ = originChunkZ,
                                    sizeXChunks = templateVersion.data.sizeXChunks,
                                    sizeZChunks = templateVersion.data.sizeZChunks,
                                    instanceType = InstanceType.MANUAL,
                                    config = instanceConfig
                                )

                                massClonerService.addManualInstance(instance)
                                massClonerService.triggerInstanceRestore(instance)

                                p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_CREATED_ORIGINAL) {
                                    unparsed("id", instance.instanceId.toString())
                                    unparsed("name", templateName)
                                    unparsed("chunk_x", originChunkX.toString())
                                    unparsed("chunk_z", originChunkZ.toString())
                                })
                                null
                            }

                            action(
                                "set_active",
                                tGui(GuiMessages.SET_ACTIVE_TITLE),
                                XMaterial.LIME_DYE,
                                listOf(tGui(GuiMessages.SET_ACTIVE_DESC)),
                                returnLevels = 1
                            ) { p ->
                                val versionResult = menuAPI.promptInt(
                                    p,
                                    tGui(GuiMessages.SET_ACTIVE_PROMPT).replace("<name>", templateName),
                                    min = 1
                                )
                                if (versionResult.isSuccess) {
                                    val versionId = versionResult.getOrNull() ?: return@action null
                                    val success = templateRepository.setActiveVersion(templateName, versionId)
                                    if (success) {
                                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_ACTIVE_SET) {
                                            unparsed("name", templateName)
                                            unparsed("version", versionId.toString())
                                        })
                                    } else {
                                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_ACTIVE_SET_FAILED))
                                    }
                                } else {
                                    p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_ACTIVE_CHANGE_CANCELLED))
                                }
                            }

                            action(
                                "delete",
                                tGui(GuiMessages.DELETE_TEMPLATE_TITLE),
                                XMaterial.BARRIER,
                                listOf(tGui(GuiMessages.DELETE_TEMPLATE_DESC))
                            ) { p ->
                                val textResult = menuAPI.promptText(
                                    p,
                                    tGui(GuiMessages.DELETE_TEMPLATE_PROMPT).replace("<name>", templateName),
                                    initialText = "",
                                    validator = { text ->
                                        if (text.isNotBlank()) InputValidation.Valid
                                        else InputValidation.Invalid(tGui(GuiMessages.INPUT_EMPTY))
                                    }
                                )
                                if (textResult.isSuccess && textResult.getOrNull() == templateName) {
                                    val deleted = templateRepository.deleteTemplate(templateName)
                                    if (deleted) {
                                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETED) {
                                            unparsed("name", templateName)
                                        })
                                    } else {
                                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_NOT_FOUND) {
                                            unparsed("name", templateName)
                                        })
                                    }
                                } else {
                                    p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                                }
                            }
                        }
                    }
                }
            }

            // ========================= CLONER / POOLS =========================
            submenu("cloner", tGui(GuiMessages.CLONER_TITLE), XMaterial.COMPARATOR) {
                // Root-level pool creation for the player's current world
                action(
                    "create_pool_here",
                    tGui(GuiMessages.CREATE_POOL_HERE_TITLE),
                    XMaterial.ANVIL,
                    listOf(tGui(GuiMessages.CREATE_POOL_HERE_DESC))
                ) { p ->
                    val worldCfg = config.massCloner.worlds.firstOrNull { it.name == p.world.name }
                    if (worldCfg == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.CLONER_WORLD_NOT_MANAGED_ERROR) {
                            unparsed("world", p.world.name)
                        })
                        return@action null
                    }

                    val templates = templateNames
                    if (templates.isEmpty()) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_NO_TEMPLATES))
                        return@action null
                    }

                    menuAPI.menuTree {
                        title(tGui(GuiMessages.SELECT_TEMPLATE_TITLE).replace("<world>", worldCfg.name))

                        submenu("templates_pool_here_${worldCfg.name}", "Templates", XMaterial.BOOK) {
                            paginated(28)

                            dynamicItems { _ ->
                                templates.map { tmplName ->
                                    submenuNode(
                                        "pool_create_here_${worldCfg.name}_$tmplName",
                                        tmplName,
                                        XMaterial.PAPER
                                    ) {
                                        action(
                                            "configure",
                                            tGui(GuiMessages.CONFIGURE_CREATE_TITLE),
                                            XMaterial.ANVIL,
                                            listOf(tGui(GuiMessages.CONFIGURE_CREATE_DESC))
                                        ) { pp ->
                                            val countResult = menuAPI.promptInt(
                                                pp,
                                                tGui(GuiMessages.INSTANCE_COUNT_PROMPT),
                                                min = 1
                                            )
                                            val count = countResult.getOrNull()
                                            if (!countResult.isSuccess || count == null) {
                                                pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                                return@action null
                                            }

                                            val sepResult = menuAPI.promptInt(
                                                pp,
                                                tGui(GuiMessages.SEPARATION_PROMPT),
                                                min = 1
                                            )
                                            val separation = sepResult.getOrNull()
                                            if (!sepResult.isSuccess || separation == null) {
                                                pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                                return@action null
                                            }

                                            val allocated = massClonerService.createPool(
                                                worldName = worldCfg.name,
                                                templateName = tmplName,
                                                versionMode = VersionMode.ACTIVE,
                                                pinnedVersionId = null,
                                                count = count,
                                                separationChunks = separation,
                                                restoreOnBoot = false,
                                                restoreOnVacate = false,
                                                restoreIntervalSeconds = null,
                                                restoreAudienceScope = config.notifications.defaultAudienceScope,
                                                updateLight = null
                                            )

                                            pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATED) {
                                                unparsed("name", tmplName)
                                                unparsed("world", worldCfg.name)
                                                unparsed("count", allocated.toString())
                                                unparsed("target", count.toString())
                                            })
                                            null
                                        }
                                    }
                                }
                            }
                        }
                    }.open(p)

                    null
                }

                for (worldCfg in config.massCloner.worlds) {
                    submenu("world_${worldCfg.name}", tGui(GuiMessages.WORLD_TITLE).replace("<world>", worldCfg.name), XMaterial.GRASS_BLOCK) {
                        // Basic pool creation (runtime only, non-persistent)
                        action(
                            "create_pool",
                            tGui(GuiMessages.CREATE_POOL_TITLE),
                            XMaterial.ANVIL,
                            listOf(tGui(GuiMessages.CREATE_POOL_DESC))
                        ) { p ->
                            val templates = templateNames
                            if (templates.isEmpty()) {
                                p.sendMessage(translations.getComponentSync(CommandMessages.POOL_NO_TEMPLATES))
                                return@action null
                            }

                            // Open a template selection menu tree for pool creation
                            menuAPI.menuTree {
                                title(tGui(GuiMessages.SELECT_TEMPLATE_TITLE).replace("<world>", worldCfg.name))

                                submenu("templates_pool_${worldCfg.name}", tGui(GuiMessages.TEMPLATES_TITLE), XMaterial.BOOK) {
                                    paginated(28)

                                    dynamicItems { _ ->
                                        templates.map { tmplName ->
                                            submenuNode(
                                                "pool_create_${worldCfg.name}_$tmplName",
                                                tmplName,
                                                XMaterial.PAPER
                                            ) {
                                                action(
                                                    "configure",
                                                    tGui(GuiMessages.CONFIGURE_CREATE_TITLE),
                                                    XMaterial.ANVIL,
                                                    listOf(tGui(GuiMessages.CONFIGURE_CREATE_DESC))
                                                ) { pp ->
                                                    val countResult = menuAPI.promptInt(
                                                        pp,
                                                        tGui(GuiMessages.INSTANCE_COUNT_PROMPT),
                                                        min = 1
                                                    )
                                                    val count = countResult.getOrNull()
                                                    if (!countResult.isSuccess || count == null) {
                                                        pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                                        return@action null
                                                    }

                                                    val sepResult = menuAPI.promptInt(
                                                        pp,
                                                        tGui(GuiMessages.SEPARATION_PROMPT),
                                                        min = 1
                                                    )
                                                    val separation = sepResult.getOrNull()
                                                    if (!sepResult.isSuccess || separation == null) {
                                                        pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                                        return@action null
                                                    }

                                                    val allocated = massClonerService.createPool(
                                                        worldName = worldCfg.name,
                                                        templateName = tmplName,
                                                        versionMode = VersionMode.ACTIVE,
                                                        pinnedVersionId = null,
                                                        count = count,
                                                        separationChunks = separation,
                                                        restoreOnBoot = false,
                                                        restoreOnVacate = false,
                                                        restoreIntervalSeconds = null,
                                                        restoreAudienceScope = config.notifications.defaultAudienceScope,
                                                        updateLight = null
                                                    )

                                                    pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATED) {
                                                        unparsed("name", tmplName)
                                                        unparsed("world", worldCfg.name)
                                                        unparsed("count", allocated.toString())
                                                        unparsed("target", count.toString())
                                                    })
                                                    null
                                                }
                                            }
                                        }
                                    }
                                }
                            }.open(p)

                            null
                        }

                        dynamicItems { _ ->
                            worldCfg.pools.map { poolCfg ->
                                submenuNode(
                                    "pool_${worldCfg.name}_${poolCfg.templateName}",
                                    tGui(GuiMessages.POOL_TITLE).replace("<name>", poolCfg.templateName),
                                    XMaterial.CHEST
                                ) {
                                    action("status", tGui(GuiMessages.STATUS_TITLE), XMaterial.PAPER) { p ->
                                        val instances =
                                            massClonerService.getInstancesForPool(worldCfg.name, poolCfg.templateName)
                                        val activeCount = instances.size
                                        val targetCount = poolCfg.count
                                        val status = if (activeCount == targetCount) "<green>OK" else "<yellow>Mismatch"

                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_STATUS_HEADER) {
                                            unparsed("name", poolCfg.templateName)
                                            unparsed("world", worldCfg.name)
                                        })
                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_INSTANCES_LINE) {
                                            unparsed("active", activeCount.toString())
                                            unparsed("target", targetCount.toString())
                                            placeholder("status", status)
                                        })
                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_VERSION_LINE) {
                                            unparsed("version", poolCfg.versionMode.toString() +
                                                    (if (poolCfg.versionMode == VersionMode.PINNED) " #${poolCfg.pinnedVersionId}" else " (active)"))
                                        })
                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_SETTINGS_LINE) {
                                            unparsed("separation", poolCfg.separationChunks.toString())
                                            unparsed("boot", poolCfg.restoreOnBoot.toString())
                                            unparsed("vacate", poolCfg.restoreOnVacate.toString())
                                        })
                                        if (poolCfg.restoreIntervalSeconds != null) {
                                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_REPEAT_LINE) {
                                                unparsed("interval", poolCfg.restoreIntervalSeconds.toString())
                                            })
                                        }
                                    }

                                    action(
                                        "restore_pool",
                                        tGui(GuiMessages.RESTORE_ALL_TITLE),
                                        XMaterial.EMERALD_BLOCK,
                                        listOf(tGui(GuiMessages.RESTORE_ALL_DESC)),
                                        returnLevels = 1
                                    ) { p ->
                                        val instances =
                                            massClonerService.getInstancesForPool(worldCfg.name, poolCfg.templateName)
                                        var restored = 0
                                        for (instance in instances) {
                                            massClonerService.triggerInstanceRestore(instance)
                                            restored++
                                        }
                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_RESTORE_TRIGGERED) {
                                            unparsed("count", restored.toString())
                                            unparsed("name", poolCfg.templateName)
                                        })
                                    }

                                    action(
                                        "regen_pool_world",
                                        tGui(GuiMessages.REGEN_POOL_TITLE),
                                        XMaterial.ANVIL,
                                        listOf(tGui(GuiMessages.REGEN_POOL_DESC)),
                                        returnLevels = 1
                                    ) { p ->
                                        val (removed, allocated) = massClonerService.regeneratePools(listOf(worldCfg.name))
                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_REGEN_HEADER) {
                                            unparsed("world", worldCfg.name)
                                        })
                                        p.sendMessage(translations.getComponentSync(CommandMessages.POOL_REGEN_STATS) {
                                            unparsed("removed", removed.toString())
                                            unparsed("allocated", allocated.toString())
                                        })
                                        p.sendMessage(translations.getComponentSync(CommandMessages.CLONER_REGEN_MANUAL_PRESERVED))
                                    }

                                    action(
                                        "show_pool_instances",
                                        tGui(GuiMessages.SHOW_INSTANCES_TITLE),
                                        XMaterial.CHEST,
                                        listOf(tGui(GuiMessages.SHOW_INSTANCES_DESC))
                                    ) { p ->
                                        val instances =
                                            massClonerService.getInstancesForPool(worldCfg.name, poolCfg.templateName)
                                        if (instances.isEmpty()) {
                                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_NO_INSTANCES))
                                        } else {
                                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_INSTANCES_HEADER) {
                                                unparsed("name", poolCfg.templateName)
                                                unparsed("world", worldCfg.name)
                                            })
                                            for (instance in instances) {
                                                val typeMark =
                                                    if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
                                                p.sendMessage(translations.getComponentSync(CommandMessages.POOL_INSTANCE_LINE) {
                                                    unparsed("type_mark", typeMark)
                                                    unparsed("id", instance.instanceId.toString())
                                                    unparsed("template", instance.templateName)
                                                    unparsed("chunk_x", instance.originChunkX.toString())
                                                    unparsed("chunk_z", instance.originChunkZ.toString())
                                                })
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ========================= INSTANCES & TIMERS =========================
            submenu("instances", tGui(GuiMessages.INSTANCES_TITLE), XMaterial.CHEST) {
                submenu("create_manual", tGui(GuiMessages.CREATE_MANUAL_TITLE), XMaterial.ANVIL) {
                    paginated(28)

                    dynamicItems { p ->
                        val templates = templateNames
                        if (templates.isEmpty()) {
                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_NO_TEMPLATES))
                            return@dynamicItems emptyList()
                        }

                        templates.map { templateName ->
                            actionNode(
                                id = "create_manual_${templateName}",
                                title = templateName,
                                material = XMaterial.PAPER,
                                description = listOf(tGui(GuiMessages.CREATE_MANUAL_DESC).replace("<name>", templateName)),
                                returnLevels = 1
                            ) { pp ->
                                val chunk = pp.location.chunk
                                createInstance(
                                    pp,
                                    templateName,
                                    chunk.x,
                                    chunk.z,
                                    pp.world.name,
                                    null,
                                    false,
                                    false,
                                    null
                                )
                            }
                        }
                    }
                }

                // Create instance at original template position
                submenu("create_original", tGui(GuiMessages.CREATE_INSTANCE_ORIGINAL_TITLE), XMaterial.ENDER_PEARL) {
                    paginated(28)

                    dynamicItems { p ->
                        val templates = templateNames
                        if (templates.isEmpty()) {
                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_NO_TEMPLATES))
                            return@dynamicItems emptyList()
                        }

                        templates.map { templateName ->
                            actionNode(
                                id = "create_original_${templateName}",
                                title = templateName,
                                material = XMaterial.PAPER,
                                description = listOf(tGui(GuiMessages.CREATE_INSTANCE_ORIGINAL_DESC)),
                                returnLevels = 1
                            ) { pp ->
                                val templateVersion = templateRepository.loadActiveTemplateVersion(templateName)
                                if (templateVersion == null) {
                                    pp.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_NOT_FOUND) {
                                        unparsed("name", templateName)
                                    })
                                    return@actionNode null
                                }

                                if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
                                    pp.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_VERSION_MISMATCH_WARNING) {
                                        unparsed("template_version", templateVersion.minecraftVersion)
                                        unparsed("server_version", nmsAdapter.minecraftVersion)
                                    })
                                }

                                val originChunkX = templateVersion.data.minChunkX
                                val originChunkZ = templateVersion.data.minChunkZ

                                val instanceConfig = InstanceConfig(
                                    restoreOnBoot = false,
                                    restoreOnVacate = false,
                                    restoreIntervalSeconds = null,
                                    restoreAudienceScope = config.notifications.defaultAudienceScope,
                                    updateLight = null
                                )

                                val instance = RegionInstance.create(
                                    instanceId = java.util.UUID.randomUUID(),
                                    worldName = pp.world.name,
                                    templateName = templateName,
                                    versionId = templateVersion.versionId,
                                    originChunkX = originChunkX,
                                    originChunkZ = originChunkZ,
                                    sizeXChunks = templateVersion.data.sizeXChunks,
                                    sizeZChunks = templateVersion.data.sizeZChunks,
                                    instanceType = InstanceType.MANUAL,
                                    config = instanceConfig
                                )

                                massClonerService.addManualInstance(instance)
                                massClonerService.triggerInstanceRestore(instance)

                                pp.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_CREATED_ORIGINAL) {
                                    unparsed("id", instance.instanceId.toString())
                                    unparsed("name", templateName)
                                    unparsed("chunk_x", originChunkX.toString())
                                    unparsed("chunk_z", originChunkZ.toString())
                                })
                                null
                            }
                        }
                    }
                }

                // Create instance at custom position
                submenu("create_custom", tGui(GuiMessages.CREATE_INSTANCE_CUSTOM_TITLE), XMaterial.COMPASS) {
                    paginated(28)

                    dynamicItems { p ->
                        val templates = templateNames
                        if (templates.isEmpty()) {
                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_NO_TEMPLATES))
                            return@dynamicItems emptyList()
                        }

                        templates.map { templateName ->
                            actionNode(
                                id = "create_custom_${templateName}",
                                title = templateName,
                                material = XMaterial.PAPER,
                                description = listOf(tGui(GuiMessages.CREATE_INSTANCE_CUSTOM_DESC)),
                                returnLevels = 1
                            ) { pp ->
                                // Prompt for world name
                                val worldResult = menuAPI.promptText(
                                    pp,
                                    tGui(GuiMessages.CREATE_INSTANCE_WORLD_PROMPT),
                                    initialText = pp.world.name,
                                    validator = { text ->
                                        if (text.isNotBlank() && Bukkit.getWorld(text) != null) InputValidation.Valid
                                        else InputValidation.Invalid(tGui(GuiMessages.INPUT_EMPTY))
                                    }
                                )
                                val worldName = worldResult.getOrNull()
                                if (!worldResult.isSuccess || worldName == null) {
                                    pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                    return@actionNode null
                                }

                                // Prompt for chunk X
                                val chunkXResult = menuAPI.promptInt(
                                    pp,
                                    tGui(GuiMessages.CREATE_INSTANCE_CHUNK_X_PROMPT),
                                    initialValue = pp.location.chunk.x
                                )
                                val chunkX = chunkXResult.getOrNull()
                                if (!chunkXResult.isSuccess || chunkX == null) {
                                    pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                    return@actionNode null
                                }

                                // Prompt for chunk Z
                                val chunkZResult = menuAPI.promptInt(
                                    pp,
                                    tGui(GuiMessages.CREATE_INSTANCE_CHUNK_Z_PROMPT),
                                    initialValue = pp.location.chunk.z
                                )
                                val chunkZ = chunkZResult.getOrNull()
                                if (!chunkZResult.isSuccess || chunkZ == null) {
                                    pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_CANCELLED))
                                    return@actionNode null
                                }

                                // Create the instance at the specified location
                                createInstance(
                                    pp,
                                    templateName,
                                    chunkX,
                                    chunkZ,
                                    worldName,
                                    null,
                                    false,
                                    false,
                                    null
                                )
                            }
                        }
                    }
                }

                submenu("instances_all", tGui(GuiMessages.ALL_INSTANCES_TITLE), XMaterial.MAP) {
                    paginated(28)

                    dynamicItems { _ ->
                        val instances = massClonerService.listInstances(null, null)
                        instances.map { instance ->
                            instanceNode(instance)
                        }
                    }
                }

                submenu("instances_world", tGui(GuiMessages.WORLD_INSTANCES_TITLE), XMaterial.GRASS_BLOCK) {
                    paginated(28)

                    dynamicItems { p ->
                        val worldName = p.world.name
                        val instances = massClonerService.listInstances(worldName, null)
                        instances.map { instance ->
                            instanceNode(instance)
                        }
                    }
                }
            }

        }

        val result = withContext(plugin.entityDispatcher(player)) {
            ui.open(player)
        }

        when (result) {
            is MenuTreeResult.ActionCompleted -> {
                player.sendMessage(translations.getComponent(CommandMessages.GUI_CLOSED_ACTION) {
                    unparsed("action", result.actionId)
                })
            }

            is MenuTreeResult.Cancelled -> {
                player.sendMessage(translations.getComponent(CommandMessages.GUI_CANCELLED))
            }

            is MenuTreeResult.ClosedAtRoot -> {
                player.sendMessage(translations.getComponent(CommandMessages.GUI_CLOSED))
            }
        }
    }

    @Subcommand("template create")
    @CommandPermission("regionrestore.template.create")
    suspend fun createTemplate(
        actor: CommandSender,
        name: String,
        minX: Int,
        minZ: Int,
        maxX: Int,
        maxZ: Int,
        world: String? = null
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = if (world != null) {
            Bukkit.getWorld(world)
        } else {
            player.world
        } ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", world ?: "unknown")
            })
            return
        }

        val minChunkX = floor(minX / 16.0).toInt()
        val maxChunkX = floor(maxX / 16.0).toInt()
        val minChunkZ = floor(minZ / 16.0).toInt()
        val maxChunkZ = floor(maxZ / 16.0).toInt()

        val template = nmsAdapter.serializeArea(targetWorld, minChunkX, minChunkZ, maxChunkX, maxChunkZ)

        val descriptionFormat = config.templates.defaultDescriptionFormat
        val description = descriptionFormat.replace("<player>", player.name)
        templateRepository.saveTemplate(name, description, template, nmsAdapter.minecraftVersion)

        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_CREATED) {
            unparsed("name", name)
        })
    }

    @Subcommand("template list")
    @CommandPermission("regionrestore.template.list")
    suspend fun listTemplates(actor: CommandSender) {
        val templates = templateRepository.listTemplates()
        if (templates.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_LIST_EMPTY))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_LIST_HEADER))
        templates.forEach { templateName ->
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_LIST_ITEM) {
                unparsed("name", templateName)
            })
        }
    }

    @Subcommand("template info")
    @CommandPermission("regionrestore.template.info")
    suspend fun templateInfo(actor: CommandSender, @SuggestTemplateName name: String) {
        val versions = templateRepository.getTemplateVersions(name)
        if (versions == null || versions.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", name)
            })
            return
        }

        val activeVersionId = templateRepository.loadActiveTemplateVersion(name)?.versionId ?: 0

        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_INFO_HEADER) {
            unparsed("name", name)
        })
        actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_INFO_VERSIONS) {
            unparsed("count", versions.size.toString())
        })
        versions.forEach { version ->
            val activeMark = if (version.versionId == activeVersionId) translations.getString(CommandMessages.TEMPLATE_INFO_VERSION_ACTIVE_MARK) else ""
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_INFO_VERSION_LINE) {
                unparsed("version", version.versionId.toString())
                unparsed("active_mark", activeMark)
                unparsed("created", java.time.Instant.ofEpochMilli(version.createdAt).toString())
                unparsed("description", version.description)
            })
        }
    }

    @Subcommand("template setactive")
    @CommandPermission("regionrestore.template.setactive")
    suspend fun setActiveVersion(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        @SuggestVersionNumber versionId: Int
    ) {
        val success = templateRepository.setActiveVersion(name, versionId)
        if (success) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_ACTIVE_SET) {
                unparsed("name", name)
                unparsed("version", versionId.toString())
            })
        } else {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_ACTIVE_SET_FAILED))
        }
    }

    @Subcommand("template delete")
    @CommandPermission("regionrestore.template.delete")
    suspend fun deleteTemplate(actor: CommandSender, @SuggestTemplateName name: String) {
        val deleted = templateRepository.deleteTemplate(name)
        if (deleted) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_DELETED) {
                unparsed("name", name)
            })
        } else {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", name)
            })
        }
    }

    @Subcommand("restore version")
    @CommandPermission("regionrestore.restore")
    suspend fun restore(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        @SuggestVersionId @Optional @Default("active") versionOrActive: String = "active",
        @Optional scope: AudienceScope? = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = player.world

        val scope = scope ?: config.notifications.defaultAudienceScope

        val templateVersion = if (versionOrActive.equals("active", true)) {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionOrActive)
                })
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", name)
                unparsed("version", versionOrActive)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    templateVersion.data.minChunkX,
                    templateVersion.data.minChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), scope)

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING) {
            unparsed("name", name)
            unparsed("version", templateVersion.versionId.toString())
        })
    }

    @Subcommand("restore in")
    @CommandPermission("regionrestore.restorein")
    suspend fun restoreIn(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        seconds: Int,
        @SuggestVersionId versionOrActive: String = "active",
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = player.world

        val templateVersion = if (versionOrActive == "active") {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionOrActive)
                })
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", name)
                unparsed("version", versionOrActive)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    templateVersion.data.minChunkX,
                    templateVersion.data.minChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(
            job,
            countdownSeconds = seconds,
            announcePoints = config.restore.defaultAnnounceTimes,
            audienceScope = scope
        )

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_SCHEDULED) {
            unparsed("name", name)
            unparsed("seconds", seconds.toString())
        })
    }

    @Subcommand("restore template")
    @CommandPermission("regionrestore.restore")
    suspend fun restoreTemplate(
        actor: CommandSender,
        @SuggestTemplateName name: String
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = player.world

        val templateVersion = templateRepository.loadActiveTemplateVersion(name)

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", name)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    templateVersion.data.minChunkX,
                    templateVersion.data.minChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING) {
            unparsed("name", name)
            unparsed("version", templateVersion.versionId.toString())
        })
    }

    @Subcommand("restore at")
    @CommandPermission("regionrestore.restore")
    suspend fun restoreAt(
        actor: CommandSender,
        @SuggestTemplateName name: String,
        world: String,
        x: Int,
        z: Int,
        @SuggestVersionId @Optional versionOrActive: String? = null
    ) {
        val targetWorld = Bukkit.getWorld(world) ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", world)
            })
            return
        }

        val versionToUse = versionOrActive ?: "active"

        val templateVersion = if (versionToUse == "active") {
            templateRepository.loadActiveTemplateVersion(name)
        } else {
            try {
                templateRepository.loadTemplateVersion(name, versionToUse.toInt())
            } catch (e: NumberFormatException) {
                actor.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionToUse)
                })
                return
            }
        }

        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", name)
                unparsed("version", versionToUse)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val targetChunkX = floor(x / 16.0).toInt()
        val targetChunkZ = floor(z / 16.0).toInt()

        val job = RestoreJob(
            id = java.util.UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = targetChunkX,
            targetChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            restoreAction = {
                nmsAdapter.restoreTemplate(
                    targetWorld,
                    templateVersion.data,
                    targetChunkX,
                    targetChunkZ,
                    plugin,
                    config.restore.updateLight
                )
            }
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        actor.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING_AT) {
            unparsed("name", name)
            unparsed("version", templateVersion.versionId.toString())
            unparsed("chunk_x", targetChunkX.toString())
            unparsed("chunk_z", targetChunkZ.toString())
        })
    }

    @Subcommand("cloner status")
    @CommandPermission("regionrestore.cloner.status")
    suspend fun clonerStatus(actor: CommandSender, world: String? = null) {
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_STATUS_HEADER))
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_STATUS_SEPARATOR))

        val worldsToShow = if (world != null) {
            val targetWorld = Bukkit.getWorld(world)
            if (targetWorld == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                    unparsed("world", world)
                })
                return
            }
            listOf(targetWorld)
        } else {
            Bukkit.getWorlds().filter { w ->
                config.massCloner.worlds.any { it.name == w.name }
            }
        }

        for (targetWorld in worldsToShow) {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == targetWorld.name }
            if (worldConfig == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_NOT_MANAGED) {
                    unparsed("world", targetWorld.name)
                })
                continue
            }

            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_HEADER) {
                unparsed("world", targetWorld.name)
            })

            for (pool in worldConfig.pools) {
                val instances = massClonerService.getInstancesForPool(targetWorld.name, pool.templateName)
                val activeCount = instances.size
                val targetCount = pool.count
                val status = if (activeCount == targetCount) "<green>✓" else "<yellow>⚠"

                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_STATUS) {
                    placeholder("status", status)  // Contains MiniMessage formatting
                    unparsed("name", pool.templateName)
                    unparsed("active", activeCount.toString())
                    unparsed("target", targetCount.toString())
                })
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_VERSION) {
                    unparsed("version", pool.versionMode.toString() +
                            (if (pool.versionMode == VersionMode.PINNED) " #${pool.pinnedVersionId}" else " (active)"))
                })
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_SETTINGS) {
                    unparsed("separation", pool.separationChunks.toString())
                    unparsed("boot", pool.restoreOnBoot.toString())
                    unparsed("vacate", pool.restoreOnVacate.toString())
                })

                if (pool.restoreIntervalSeconds != null) {
                    actor.sendMessage(translations.getComponent(CommandMessages.CLONER_POOL_REPEAT) {
                        unparsed("interval", pool.restoreIntervalSeconds.toString())
                    })
                }
            }
        }
    }

    @Subcommand("cloner restore")
    @CommandPermission("regionrestore.cloner.restore")
    suspend fun clonerRestore(actor: CommandSender, world: String?, template: String?) {
        val worldName = world ?: (actor as? Player)?.world?.name
        if (worldName == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_SPECIFIED))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_RESTORE_STARTING))

        val worldsToRestore = if (template != null) {
            // Restore specific template in specific world
            listOf(worldName to listOf(template))
        } else {
            // Restore all templates in world
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == worldName }
            if (worldConfig == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_NOT_MANAGED_ERROR) {
                    unparsed("world", worldName)
                })
                return
            }
            listOf(worldName to worldConfig.pools.map { it.templateName })
        }

        var restoredCount = 0

        for ((w, templates) in worldsToRestore) {
            for (templateName in templates) {
                val instances = massClonerService.getInstancesForPool(w, templateName)
                for (instance in instances) {
                    massClonerService.triggerInstanceRestore(instance)
                    restoredCount++
                }
            }
        }

        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_RESTORE_TRIGGERED) {
            unparsed("count", restoredCount.toString())
        })
    }

    @Subcommand("cloner regen")
    @CommandPermission("regionrestore.cloner.regen")
    suspend fun clonerRegen(
        actor: CommandSender,
        world: String? = null,
        force: Boolean = false
    ) {
        // Require --force flag to prevent accidental data loss
        if (!force) {
            actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_CONFIRM))
            return
        }

        // Validate world if specified
        if (world != null) {
            val worldConfig = config.massCloner.worlds.firstOrNull { it.name == world }
            if (worldConfig == null) {
                actor.sendMessage(translations.getComponent(CommandMessages.CLONER_WORLD_NOT_CONFIGURED) {
                    unparsed("world", world)
                })
                return
            }
        }

        // Call service to regenerate pools
        val worldsToRegen = if (world != null) listOf(world) else emptyList()
        val (removed, allocated) = massClonerService.regeneratePools(worldsToRegen)

        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_COMPLETE))
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_REMOVED) {
            unparsed("count", removed.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_ALLOCATED) {
            unparsed("count", allocated.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.CLONER_REGEN_MANUAL_PRESERVED))
    }

    @Subcommand("instance create")
    @CommandPermission("regionrestore.instance.create")
    suspend fun createInstance(
        actor: CommandSender,
        @SuggestTemplateName templateName: String,
        chunkX: Int,
        chunkZ: Int,
        world: String? = null,
        versionId: Int? = null,
        restoreOnBoot: Boolean = false,
        restoreOnVacate: Boolean = false,
        restoreIntervalSeconds: Int? = null
    ) {
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val targetWorld = if (world != null) {
            Bukkit.getWorld(world)
        } else {
            player.world
        } ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", world ?: "unknown")
            })
            return
        }

        // Load template to get dimensions
        val templateVersion = if (versionId != null) {
            templateRepository.loadTemplateVersion(templateName, versionId)
        } else {
            templateRepository.loadActiveTemplateVersion(templateName)
        } ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", templateName)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH_SIMPLE))
        }

        // Create instance config
        val config = InstanceConfig(
            restoreOnBoot = restoreOnBoot,
            restoreOnVacate = restoreOnVacate,
            restoreIntervalSeconds = restoreIntervalSeconds,
            restoreAudienceScope = config.notifications.defaultAudienceScope,
            updateLight = null // allow fallback to pool/global updateLight at restore time
        )

        // Create instance
        val instance = RegionInstance.create(
            instanceId = java.util.UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = versionId ?: templateVersion.versionId,
            originChunkX = chunkX,
            originChunkZ = chunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = config
        )

        // Add to registry
        massClonerService.addManualInstance(instance)

        // Start triggers
        if (config.restoreOnBoot || config.restoreIntervalSeconds != null) {
            massClonerService.startInstanceTriggers(instance)
        }

        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_CREATED) {
            unparsed("id", instance.instanceId.toString())
            unparsed("name", templateName)
            unparsed("chunk_x", chunkX.toString())
            unparsed("chunk_z", chunkZ.toString())
        })
    }

    @Subcommand("instance list all")
    @CommandPermission("regionrestore.instance.list")
    suspend fun listAllInstances(actor: CommandSender) {
        val instances = massClonerService.listInstances(null, null)

        if (instances.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_EMPTY))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_HEADER) {
            unparsed("count", instances.size.toString())
        })
        for (instance in instances) {
            val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_ITEM) {
                unparsed("type_mark", typeMark)
                unparsed("id", instance.instanceId.toString())
                unparsed("name", instance.templateName)
                unparsed("chunk_x", instance.originChunkX.toString())
                unparsed("chunk_z", instance.originChunkZ.toString())
                unparsed("world", instance.worldName)
            })
        }
    }

    @Subcommand("instance list world")
    @CommandPermission("regionrestore.instance.list")
    suspend fun listInstancesByWorld(
        actor: CommandSender,
        world: String,
        @Optional type: String? = null
    ) {
        val instanceType = when (type?.uppercase()) {
            "POOLED" -> InstanceType.POOLED
            "MANUAL" -> InstanceType.MANUAL
            else -> null
        }

        val instances = massClonerService.listInstances(world, instanceType)

        if (instances.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_EMPTY))
            return
        }

        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_HEADER) {
            unparsed("count", instances.size.toString())
        })
        for (instance in instances) {
            val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_LIST_ITEM) {
                unparsed("type_mark", typeMark)
                unparsed("id", instance.instanceId.toString())
                unparsed("name", instance.templateName)
                unparsed("chunk_x", instance.originChunkX.toString())
                unparsed("chunk_z", instance.originChunkZ.toString())
                unparsed("world", instance.worldName)
            })
        }
    }

    @Subcommand("instance info")
    @CommandPermission("regionrestore.instance.info")
    suspend fun instanceInfo(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val cfg = instance.config
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_HEADER) {
            unparsed("id", instance.instanceId.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_TEMPLATE) {
            unparsed("name", instance.templateName)
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_LOCATION) {
            unparsed("world", instance.worldName)
            unparsed("chunk_x", instance.originChunkX.toString())
            unparsed("chunk_z", instance.originChunkZ.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_SIZE) {
            unparsed("size_x", instance.sizeXChunks.toString())
            unparsed("size_z", instance.sizeZChunks.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_TYPE) {
            unparsed("type", instance.instanceType.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_CONFIG))
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_BOOT_RESTORE) {
            unparsed("value", (cfg?.restoreOnBoot ?: false).toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_VACATE_RESTORE) {
            unparsed("value", (cfg?.restoreOnVacate ?: false).toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_REPEAT) {
            unparsed("value", (cfg?.restoreIntervalSeconds?.toString() ?: "none"))
        })
    }

    @Subcommand("instance delete")
    @CommandPermission("regionrestore.instance.delete")
    suspend fun deleteInstance(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val confirmed = confirmInstanceDeletion(player, instance)
        if (!confirmed) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_DELETE_CANCELLED))
            return
        }

        val deleted = massClonerService.removeInstance(id)

        if (deleted) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_DELETED) {
                unparsed("id", instanceId)
            })
        } else {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
        }
    }

    @Subcommand("instance restore")
    @CommandPermission("regionrestore.instance.restore")
    suspend fun restoreInstance(actor: CommandSender, @SuggestInstanceId instanceId: String) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        massClonerService.triggerInstanceRestore(instance)
        actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_RESTORE_TRIGGERED) {
            unparsed("id", instanceId)
        })
    }

    @Subcommand("timer set instance")
    @CommandPermission("regionrestore.timer.set")
    suspend fun setTimerByInstanceId(
        actor: CommandSender,
        @SuggestInstanceId instanceId: String,
        intervalSeconds: Int,
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        // Update instance config with new interval
        val updatedConfig = InstanceConfig(
            restoreOnBoot = instance.config?.restoreOnBoot ?: false,
            restoreOnVacate = instance.config?.restoreOnVacate ?: false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null // preserve fallback to pool/global updateLight
        )

        // Create updated instance with new config
        val updatedInstance = instance.copy(
            config = updatedConfig
        )

        // Stop existing timers
        massClonerService.stopInstanceTriggers(instance)

        // Update the instance in the registry (remove old, add updated)
        massClonerService.removeInstance(instance.instanceId)
        massClonerService.addManualInstance(updatedInstance)

        // Start new timers
        massClonerService.startInstanceTriggers(updatedInstance)

        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_SET) {
            unparsed("id", instanceId)
            unparsed("interval", intervalSeconds.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_SET_HELP) {
            unparsed("id", instanceId)
        })
    }

    @Subcommand("timer set template")
    @CommandPermission("regionrestore.timer.set")
    suspend fun setTimerByTemplateName(
        actor: CommandSender,
        @SuggestTemplateName templateName: String,
        intervalSeconds: Int,
        scope: AudienceScope = config.notifications.defaultAudienceScope
    ) {
        val player = actor as? Player
        val targetWorld = player?.world ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", "unknown")
            })
            return
        }

        // Load template
        val templateVersion = templateRepository.loadActiveTemplateVersion(templateName)
        if (templateVersion == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", templateName)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            actor.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH_SIMPLE))
        }

        // Use template's original coordinates
        val targetChunkX = templateVersion.data.minChunkX
        val targetChunkZ = templateVersion.data.minChunkZ

        // Create hidden manual instance for this timer
        val instanceConfig = InstanceConfig(
            restoreOnBoot = false,
            restoreOnVacate = false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null // allow fallback to pool/global updateLight at restore time
        )

        val instance = RegionInstance.create(
            instanceId = java.util.UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = templateVersion.versionId,
            originChunkX = targetChunkX,
            originChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = instanceConfig
        )

        massClonerService.addManualInstance(instance)
        massClonerService.startInstanceTriggers(instance)

        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_SET_TEMPLATE) {
            unparsed("name", templateName)
            unparsed("interval", intervalSeconds.toString())
        })
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_INSTANCE_ID_HELP) {
            unparsed("id", instance.instanceId.toString())
        })
    }

    @Subcommand("timer cancel id")
    @CommandPermission("regionrestore.timer.cancel")
    suspend fun cancelTimerById(
        actor: CommandSender,
        @SuggestInstanceId instanceId: String,
    ) {
        val id = java.util.UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            actor.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        massClonerService.removeInstance(id)
        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_CANCELLED) {
            unparsed("id", instanceId)
        })
    }

    @Subcommand("timer cancel template-all")
    @CommandPermission("regionrestore.timer.cancel")
    suspend fun cancelTimerByTemplate(
        actor: CommandSender,
        @SuggestTemplateName templateName: String
    ) {
        // Cancel all timers for instances of this template in the current world
        val player = actor as? Player ?: run {
            actor.sendMessage(translations.getComponent(CommandMessages.PLAYER_ONLY))
            return
        }

        val worldName = player.world.name
        val instances = massClonerService.listInstances(worldName, null)
        val templateInstances = instances.filter { it.templateName == templateName }

        if (templateInstances.isEmpty()) {
            actor.sendMessage(translations.getComponent(CommandMessages.TIMER_NO_INSTANCES) {
                unparsed("name", templateName)
            })
            return
        }

        var cancelledCount = 0
        for (instance in templateInstances) {
            if (instance.config?.restoreIntervalSeconds != null) {
                massClonerService.removeInstance(instance.instanceId)
                cancelledCount++
            }
        }

        actor.sendMessage(translations.getComponent(CommandMessages.TIMER_CANCELLED_TEMPLATE) {
            unparsed("count", cancelledCount.toString())
            unparsed("name", templateName)
        })
    }

    private fun instanceNode(instance: RegionInstance): SubmenuNode {
        val typeMark = if (instance.instanceType == InstanceType.POOLED) "[P]" else "[M]"
        val title =
            "$typeMark ${instance.templateName} @ ${instance.worldName} (${instance.originChunkX}, ${instance.originChunkZ})"
        return submenuNode("instance_${instance.instanceId}", title, XMaterial.CHEST) {
            action("info", tGui(GuiMessages.INSTANCE_INFO_TITLE), XMaterial.BOOK) { p ->
                instanceInfo(p, instance.instanceId.toString())
            }

            action("restore", tGui(GuiMessages.INSTANCE_RESTORE_TITLE), XMaterial.EMERALD) { p ->
                restoreInstance(p, instance.instanceId.toString())
            }

            action("delete", tGui(GuiMessages.INSTANCE_DELETE_TITLE), XMaterial.BARRIER) { p ->
                val confirmed = confirmInstanceDeletion(p, instance)
                if (!confirmed) {
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_DELETE_CANCELLED))
                    return@action null
                }

                val deleted = massClonerService.removeInstance(instance.instanceId)
                if (deleted) {
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_DELETED) {
                        unparsed("id", instance.instanceId.toString())
                    })
                } else {
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_NOT_FOUND) {
                        unparsed("id", instance.instanceId.toString())
                    })
                }
            }

            // Timers submenu for this instance
            submenu("timers", tGui(GuiMessages.TIMERS_TITLE), XMaterial.CLOCK) {
                val timerLoreBase = mutableListOf<String>()
                val cfg = instance.config
                if (cfg == null || cfg.restoreIntervalSeconds == null) {
                    timerLoreBase += tGui(GuiMessages.TIMER_NO_CONFIG)
                } else {
                    timerLoreBase += tGui(GuiMessages.TIMER_INTERVAL).replace("<interval>", cfg.restoreIntervalSeconds.toString())
                    timerLoreBase += tGui(GuiMessages.TIMER_AUDIENCE).replace("<scope>", cfg.restoreAudienceScope.toString())
                }

                display(
                    "timer_info",
                    tGui(GuiMessages.VIEW_TIMER_TITLE),
                    XMaterial.CLOCK,
                    timerLoreBase
                )

                action("timer_set", tGui(GuiMessages.SET_TIMER_TITLE), XMaterial.LIME_DYE, emptyList(), returnLevels = 1) { p ->
                    val intervalResult = menuAPI.promptInt(
                        p,
                        tGui(GuiMessages.TIMER_INTERVAL_PROMPT),
                        min = 1
                    )
                    val interval = intervalResult.getOrNull()
                    if (!intervalResult.isSuccess || interval == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TIMER_CONFIG_CANCELLED))
                        return@action null
                    }

                    setTimerByInstanceId(
                        p,
                        instance.instanceId.toString(),
                        interval,
                        config.notifications.defaultAudienceScope
                    )
                }

                action("timer_clear", tGui(GuiMessages.DELETE_TIMER_TITLE), XMaterial.RED_DYE, emptyList(), returnLevels = 1) { p ->
                    cancelTimerById(p, instance.instanceId.toString())
                }
            }
        }
    }

    private suspend fun confirmInstanceDeletion(player: Player, instance: RegionInstance): Boolean {
        return suspendCoroutine { continuation ->
            var completed = false
            val menu = ConfirmationMenu {
                title = Component.text(tGui(GuiMessages.CONFIRM_DELETE_TITLE))
                infoItem = VItem(XMaterial.PAPER) {
                    name = Component.text(tGui(GuiMessages.CONFIRM_DELETE_INFO))
                    loreStrings(
                        listOf(
                            tGui(GuiMessages.CONFIRM_INSTANCE_LINE).replace("<id>", instance.instanceId.toString()),
                            tGui(GuiMessages.CONFIRM_TEMPLATE_LINE).replace("<name>", instance.templateName),
                            tGui(GuiMessages.CONFIRM_WORLD_LINE).replace("<world>", instance.worldName),
                            tGui(GuiMessages.CONFIRM_WARNING)
                        )
                    )
                }
                onConfirm = {
                    if (!completed) {
                        completed = true
                        continuation.resume(true)
                    }
                }
                onCancel = {
                    if (!completed) {
                        completed = true
                        continuation.resume(false)
                    }
                }
            }

            plugin.launch(plugin.entityDispatcher(player)) {
                menuAPI.open(menu, player)
            }
        }
    }
}
