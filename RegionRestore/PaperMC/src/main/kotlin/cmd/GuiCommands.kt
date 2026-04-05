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
import org.slf4j.LoggerFactory
import com.github.shynixn.mccoroutine.folia.globalRegionDispatcher
import com.github.shynixn.mccoroutine.folia.launch
import kotlinx.coroutines.withContext
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import revxrsal.commands.annotation.Command
import java.time.Instant
import java.util.UUID
import revxrsal.commands.annotation.Subcommand
import revxrsal.commands.bukkit.actor.BukkitCommandActor
import revxrsal.commands.bukkit.annotation.CommandPermission
import kotlin.math.floor

@Command("regionrestore", "rr", "arena")
class GuiCommands(
    private val nmsAdapter: PaperNmsAdapter,
    private val templateRepository: TemplateRepository,
    private val schedulerService: SchedulerService,
    private val config: RegionRestoreConfig,
    private val massClonerService: MassClonerService,
    private val menuAPI: MenuAPI,
    private val plugin: JavaPlugin,
    private val translations: TranslationAPI
) {
    private val log = LoggerFactory.getLogger(GuiCommands::class.java)

    private fun tGui(key: GuiMessages) = translations.getString(key)

    @Subcommand("gui")
    @CommandPermission("regionrestore.gui")
    suspend fun openGui(actor: BukkitCommandActor) {
        val player = actor.requirePlayer()

        val templateNames = templateRepository.listTemplates()

        val templateVersionsByName = templateNames.associateWith {
            templateRepository.getTemplateVersions(it) ?: emptyList()
        }
        val activeVersionIdsByName = templateNames.associateWith {
            templateRepository.loadActiveTemplateVersion(it)?.versionId ?: 0
        }

        val ui = menuAPI.menuTree {
            title(tGui(GuiMessages.MAIN_TITLE))

            submenu("templates", tGui(GuiMessages.TEMPLATES_TITLE), XMaterial.BOOK) {
                paginated(28)

                action(
                    "create_template",
                    tGui(GuiMessages.CREATE_TEMPLATE_TITLE),
                    XMaterial.ANVIL,
                    listOf(tGui(GuiMessages.CREATE_TEMPLATE_DESC)),
                    returnLevels = 1
                ) { p ->
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

                    val minXResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MIN_X_PROMPT))
                    val minX = minXResult.getOrNull()
                    if (!minXResult.isSuccess || minX == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    val minZResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MIN_Z_PROMPT))
                    val minZ = minZResult.getOrNull()
                    if (!minZResult.isSuccess || minZ == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    val maxXResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MAX_X_PROMPT))
                    val maxX = maxXResult.getOrNull()
                    if (!maxXResult.isSuccess || maxX == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    val maxZResult = menuAPI.promptInt(p, tGui(GuiMessages.CREATE_TEMPLATE_MAX_Z_PROMPT))
                    val maxZ = maxZResult.getOrNull()
                    if (!maxZResult.isSuccess || maxZ == null) {
                        p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_DELETE_CANCELLED))
                        return@action null
                    }

                    val minChunkX = floor(minX / 16.0).toInt()
                    val maxChunkX = floor(maxX / 16.0).toInt()
                    val minChunkZ = floor(minZ / 16.0).toInt()
                    val maxChunkZ = floor(maxZ / 16.0).toInt()

                    val template = nmsAdapter.serializeArea(p.world, minChunkX, minChunkZ, maxChunkX, maxChunkZ)
                    val descriptionFormat = config.templates.defaultDescriptionFormat
                    val description = descriptionFormat.replace("<player>", p.name)
                    templateRepository.saveTemplate(name, description, template, nmsAdapter.minecraftVersion)

                    p.sendMessage(translations.getComponentSync(CommandMessages.TEMPLATE_CREATED) {
                        unparsed("name", name)
                    })
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

                        submenuNode(
                            "template_$templateName",
                            tGui(GuiMessages.TEMPLATE_ITEM_TITLE).replace("<name>", templateName),
                            XMaterial.PAPER
                        ) {
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
                                            tGui(GuiMessages.VERSION_ACTIVE).replace(
                                                "<version>",
                                                version.versionId.toString()
                                            )
                                        } else {
                                            tGui(GuiMessages.VERSION_NORMAL).replace(
                                                "<version>",
                                                version.versionId.toString()
                                            )
                                        }
                                        val lore = listOf(
                                            tGui(GuiMessages.VERSION_CREATED).replace(
                                                "<timestamp>",
                                                Instant.ofEpochMilli(version.createdAt).toString()
                                            ),
                                            tGui(GuiMessages.VERSION_MINECRAFT).replace(
                                                "<version>",
                                                version.minecraftVersion
                                            ),
                                            tGui(GuiMessages.VERSION_DESCRIPTION).replace(
                                                "<description>",
                                                version.description
                                            )
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
                                    instanceId = UUID.randomUUID(),
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
                                    .onFailure {
                                        log.error("Failed to persist instance ${instance.instanceId}", it)
                                        p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_SAVE_FAILED))
                                    }
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

            submenu("cloner", tGui(GuiMessages.CLONER_TITLE), XMaterial.COMPARATOR) {
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

                                            allocated.onFailure {
                                                log.error(
                                                    "Failed to create pool '$tmplName' in world '${worldCfg.name}'",
                                                    it
                                                )
                                                pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_FAILED) {
                                                    unparsed("name", tmplName)
                                                })
                                                return@action null
                                            }

                                            pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATED) {
                                                unparsed("name", tmplName)
                                                unparsed("world", worldCfg.name)
                                                unparsed("count", allocated.getOrNull().toString())
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
                    submenu(
                        "world_${worldCfg.name}",
                        tGui(GuiMessages.WORLD_TITLE).replace("<world>", worldCfg.name),
                        XMaterial.GRASS_BLOCK
                    ) {
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

                            menuAPI.menuTree {
                                title(tGui(GuiMessages.SELECT_TEMPLATE_TITLE).replace("<world>", worldCfg.name))

                                submenu(
                                    "templates_pool_${worldCfg.name}",
                                    tGui(GuiMessages.TEMPLATES_TITLE),
                                    XMaterial.BOOK
                                ) {
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

                                                    allocated.onFailure {
                                                        log.error(
                                                            "Failed to create pool '$tmplName' in world '${worldCfg.name}'",
                                                            it
                                                        )
                                                        pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATION_FAILED) {
                                                            unparsed("name", tmplName)
                                                        })
                                                        return@action null
                                                    }

                                                    pp.sendMessage(translations.getComponentSync(CommandMessages.POOL_CREATED) {
                                                        unparsed("name", tmplName)
                                                        unparsed("world", worldCfg.name)
                                                        unparsed("count", allocated.getOrNull().toString())
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
                                            unparsed(
                                                "version", poolCfg.versionMode.toString() +
                                                        (if (poolCfg.versionMode == VersionMode.PINNED) " #${poolCfg.pinnedVersionId}" else " (active)")
                                            )
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
                                        val result = massClonerService.regeneratePools(listOf(worldCfg.name))
                                        result.onFailure {
                                            log.error(
                                                "Failed to persist state after regenerating pools for world '${worldCfg.name}'",
                                                it
                                            )
                                            p.sendMessage(translations.getComponentSync(CommandMessages.CLONER_SAVE_FAILED))
                                        }
                                        result.onSuccess { (removed, allocated) ->
                                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_REGEN_HEADER) {
                                                unparsed("world", worldCfg.name)
                                            })
                                            p.sendMessage(translations.getComponentSync(CommandMessages.POOL_REGEN_STATS) {
                                                unparsed("removed", removed.toString())
                                                unparsed("allocated", allocated.toString())
                                            })
                                            p.sendMessage(translations.getComponentSync(CommandMessages.CLONER_REGEN_MANUAL_PRESERVED))
                                        }
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
                                description = listOf(
                                    tGui(GuiMessages.CREATE_MANUAL_DESC).replace(
                                        "<name>",
                                        templateName
                                    )
                                ),
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
                                    instanceId = UUID.randomUUID(),
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
                                    .onFailure {
                                        log.error("Failed to persist instance ${instance.instanceId}", it)
                                        pp.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_SAVE_FAILED))
                                    }
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

    private suspend fun restoreTemplate(player: Player, templateName: String) {
        val targetWorld = player.world

        val templateVersion = templateRepository.loadActiveTemplateVersion(templateName)

        if (templateVersion == null) {
            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", templateName)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val job = RestoreJob(
            id = UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = templateVersion.data.minChunkX,
            targetChunkZ = templateVersion.data.minChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = config.restore.updateLight
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        player.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING) {
            unparsed("name", templateName)
            unparsed("version", templateVersion.versionId.toString())
        })
    }

    private suspend fun restoreAt(
        player: Player,
        templateName: String,
        world: String,
        x: Int,
        z: Int,
        versionOrActive: String
    ) {
        val targetWorld = Bukkit.getWorld(world) ?: run {
            player.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", world)
            })
            return
        }

        val templateVersion = if (versionOrActive == "active") {
            templateRepository.loadActiveTemplateVersion(templateName)
        } else {
            try {
                templateRepository.loadTemplateVersion(templateName, versionOrActive.toInt())
            } catch (e: NumberFormatException) {
                player.sendMessage(translations.getComponent(CommandMessages.INVALID_VERSION_ID) {
                    unparsed("version", versionOrActive)
                })
                return
            }
        }

        if (templateVersion == null) {
            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_NOT_FOUND) {
                unparsed("name", templateName)
                unparsed("version", versionOrActive)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH) {
                unparsed("template_version", templateVersion.minecraftVersion)
                unparsed("server_version", nmsAdapter.minecraftVersion)
            })
        }

        val targetChunkX = floor(x / 16.0).toInt()
        val targetChunkZ = floor(z / 16.0).toInt()

        val job = RestoreJob(
            id = UUID.randomUUID(),
            world = targetWorld,
            targetChunkX = targetChunkX,
            targetChunkZ = targetChunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            template = templateVersion.data,
            updateLight = config.restore.updateLight
        )

        schedulerService.scheduleRestore(job, null, emptyList(), config.notifications.defaultAudienceScope)

        player.sendMessage(translations.getComponent(CommandMessages.RESTORE_STARTING_AT) {
            unparsed("name", templateName)
            unparsed("version", templateVersion.versionId.toString())
            unparsed("chunk_x", targetChunkX.toString())
            unparsed("chunk_z", targetChunkZ.toString())
        })
    }

    private suspend fun createInstance(
        player: Player,
        templateName: String,
        chunkX: Int,
        chunkZ: Int,
        worldName: String,
        versionId: Int?,
        restoreOnBoot: Boolean,
        restoreOnVacate: Boolean,
        restoreIntervalSeconds: Int?
    ) {
        val targetWorld = Bukkit.getWorld(worldName) ?: run {
            player.sendMessage(translations.getComponent(CommandMessages.WORLD_NOT_FOUND) {
                unparsed("world", worldName)
            })
            return
        }

        val templateVersion = if (versionId != null) {
            templateRepository.loadTemplateVersion(templateName, versionId)
        } else {
            templateRepository.loadActiveTemplateVersion(templateName)
        } ?: run {
            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_NOT_FOUND) {
                unparsed("name", templateName)
            })
            return
        }

        if (templateVersion.minecraftVersion != nmsAdapter.minecraftVersion) {
            player.sendMessage(translations.getComponent(CommandMessages.TEMPLATE_VERSION_MISMATCH_SIMPLE))
        }

        val instanceConfig = InstanceConfig(
            restoreOnBoot = restoreOnBoot,
            restoreOnVacate = restoreOnVacate,
            restoreIntervalSeconds = restoreIntervalSeconds,
            restoreAudienceScope = config.notifications.defaultAudienceScope,
            updateLight = null
        )

        val instance = RegionInstance.create(
            instanceId = UUID.randomUUID(),
            worldName = targetWorld.name,
            templateName = templateName,
            versionId = versionId ?: templateVersion.versionId,
            originChunkX = chunkX,
            originChunkZ = chunkZ,
            sizeXChunks = templateVersion.data.sizeXChunks,
            sizeZChunks = templateVersion.data.sizeZChunks,
            instanceType = InstanceType.MANUAL,
            config = instanceConfig
        )

        val result = massClonerService.addManualInstance(instance)
        result.onFailure {
            log.error("Failed to persist instance ${instance.instanceId}", it)
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
        }

        if (instanceConfig.restoreOnBoot || instanceConfig.restoreIntervalSeconds != null) {
            massClonerService.startInstanceTriggers(instance)
        }

        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_CREATED) {
            unparsed("id", instance.instanceId.toString())
            unparsed("name", templateName)
            unparsed("chunk_x", chunkX.toString())
            unparsed("chunk_z", chunkZ.toString())
        })
    }

    private suspend fun instanceInfo(player: Player, instanceId: String) {
        val id = UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val cfg = instance.config
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_HEADER) {
            unparsed("id", instance.instanceId.toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_TEMPLATE) {
            unparsed("name", instance.templateName)
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_LOCATION) {
            unparsed("world", instance.worldName)
            unparsed("chunk_x", instance.originChunkX.toString())
            unparsed("chunk_z", instance.originChunkZ.toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_SIZE) {
            unparsed("size_x", instance.sizeXChunks.toString())
            unparsed("size_z", instance.sizeZChunks.toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_TYPE) {
            unparsed("type", instance.instanceType.toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_CONFIG))
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_BOOT_RESTORE) {
            unparsed("value", (cfg?.restoreOnBoot ?: false).toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_VACATE_RESTORE) {
            unparsed("value", (cfg?.restoreOnVacate ?: false).toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_INFO_REPEAT) {
            unparsed("value", (cfg?.restoreIntervalSeconds?.toString() ?: "none"))
        })
    }

    private suspend fun restoreInstance(player: Player, instanceId: String) {
        val id = UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        massClonerService.triggerInstanceRestore(instance)
        player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_RESTORE_TRIGGERED) {
            unparsed("id", instanceId)
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
                val confirmed = CommandHelpers.confirmInstanceDeletion(
                    player = p,
                    instance = instance,
                    translations = translations,
                    menuAPI = menuAPI,
                    plugin = plugin
                )
                if (!confirmed) {
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_DELETE_CANCELLED))
                    return@action null
                }

                val result = massClonerService.removeInstance(instance.instanceId)
                result.onFailure {
                    log.error("Failed to persist state after deleting instance ${instance.instanceId}", it)
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_SAVE_FAILED))
                }
                if (result.getOrNull() == true) {
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_DELETED) {
                        unparsed("id", instance.instanceId.toString())
                    })
                } else {
                    p.sendMessage(translations.getComponentSync(CommandMessages.INSTANCE_NOT_FOUND) {
                        unparsed("id", instance.instanceId.toString())
                    })
                }
            }

            submenu("timers", tGui(GuiMessages.TIMERS_TITLE), XMaterial.CLOCK) {
                val timerLoreBase = mutableListOf<String>()
                val cfg = instance.config
                if (cfg == null || cfg.restoreIntervalSeconds == null) {
                    timerLoreBase += tGui(GuiMessages.TIMER_NO_CONFIG)
                } else {
                    timerLoreBase += tGui(GuiMessages.TIMER_INTERVAL).replace(
                        "<interval>",
                        cfg.restoreIntervalSeconds.toString()
                    )
                    timerLoreBase += tGui(GuiMessages.TIMER_AUDIENCE).replace(
                        "<scope>",
                        cfg.restoreAudienceScope.toString()
                    )
                }

                display(
                    "timer_info",
                    tGui(GuiMessages.VIEW_TIMER_TITLE),
                    XMaterial.CLOCK,
                    timerLoreBase
                )

                action(
                    "timer_set",
                    tGui(GuiMessages.SET_TIMER_TITLE),
                    XMaterial.LIME_DYE,
                    emptyList(),
                    returnLevels = 1
                ) { p ->
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

                action(
                    "timer_clear",
                    tGui(GuiMessages.DELETE_TIMER_TITLE),
                    XMaterial.RED_DYE,
                    emptyList(),
                    returnLevels = 1
                ) { p ->
                    cancelTimerById(p, instance.instanceId.toString())
                }
            }
        }
    }

    private suspend fun setTimerByInstanceId(
        player: Player,
        instanceId: String,
        intervalSeconds: Int,
        scope: AudienceScope
    ) {
        val id = UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        val updatedConfig = InstanceConfig(
            restoreOnBoot = instance.config?.restoreOnBoot ?: false,
            restoreOnVacate = instance.config?.restoreOnVacate ?: false,
            restoreIntervalSeconds = intervalSeconds,
            restoreAudienceScope = scope,
            updateLight = null
        )

        val updatedInstance = instance.copy(config = updatedConfig)

        massClonerService.stopInstanceTriggers(instance)

        val removeResult = massClonerService.removeInstance(instance.instanceId)
        if (removeResult.isFailure) {
            log.error(
                "Failed to persist state after removing instance ${instance.instanceId} for timer update",
                removeResult.exceptionOrNull()
            )
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            return
        }

        val addResult = massClonerService.addManualInstance(updatedInstance)
        if (addResult.isFailure) {
            log.error(
                "Failed to persist state after adding updated instance ${updatedInstance.instanceId} for timer update",
                addResult.exceptionOrNull()
            )
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            return
        }

        massClonerService.startInstanceTriggers(updatedInstance)

        player.sendMessage(translations.getComponent(CommandMessages.TIMER_SET) {
            unparsed("id", instanceId)
            unparsed("interval", intervalSeconds.toString())
        })
        player.sendMessage(translations.getComponent(CommandMessages.TIMER_SET_HELP) {
            unparsed("id", instanceId)
        })
    }

    private suspend fun cancelTimerById(player: Player, instanceId: String) {
        val id = UUID.fromString(instanceId)
        val instance = massClonerService.getInstance(id)

        if (instance == null) {
            player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_NOT_FOUND) {
                unparsed("id", instanceId)
            })
            return
        }

        massClonerService.removeInstance(id)
            .onFailure {
                log.error("Failed to persist state after cancelling timer for instance $id", it)
                player.sendMessage(translations.getComponent(CommandMessages.INSTANCE_SAVE_FAILED))
            }
        player.sendMessage(translations.getComponent(CommandMessages.TIMER_CANCELLED) {
            unparsed("id", instanceId)
        })
    }
}
