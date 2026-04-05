package bruh.regionrestore.translations

import bruh.zchat.utils.translations.MessageKey

/**
 * Message keys for RegionRestore command responses.
 */
enum class CommandMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // General errors
    PLAYER_ONLY("player_only", "This command can only be run by a player"),
    WORLD_NOT_FOUND("world_not_found", "<red>World '<world>' not found"),
    WORLD_NOT_SPECIFIED("world_not_specified", "<red>Please specify a world or run as a player"),

    // Template messages
    TEMPLATE_CREATED("template_created", "<green>Template '<name>' created successfully!"),
    TEMPLATE_NOT_FOUND("template_not_found", "<red>Template '<name>' not found"),
    TEMPLATE_VERSION_NOT_FOUND("template_version_not_found", "<red>Template '<name>' version '<version>' not found"),
    TEMPLATE_DELETED("template_deleted", "<green>Template '<name>' deleted successfully!"),
    TEMPLATE_DELETE_CANCELLED("template_delete_cancelled", "<gray>Template deletion cancelled"),
    TEMPLATE_LIST_EMPTY("template_list_empty", "<gray>No templates found"),
    TEMPLATE_LIST_HEADER("template_list_header", "<green>Templates:"),
    TEMPLATE_LIST_ITEM("template_list_item", "<white>- <name>"),
    TEMPLATE_INFO_HEADER("template_info_header", "<green>Template: <white><name>"),
    TEMPLATE_INFO_VERSIONS("template_info_versions", "<gray>Versions: <count>"),
    TEMPLATE_INFO_VERSION_LINE(
        "template_info_version_line",
        "<white>  v<version>:<active_mark> <gray><created> - <description>"
    ),
    TEMPLATE_INFO_VERSION_ACTIVE_MARK("template_info_version_active_mark", " <green>(active)"),
    TEMPLATE_ACTIVE_SET("template_active_set", "<green>Template '<name>' active version set to v<version>"),
    TEMPLATE_ACTIVE_SET_FAILED(
        "template_active_set_failed",
        "<red>Failed to set active version. Template or version not found."
    ),
    TEMPLATE_ACTIVE_CHANGE_CANCELLED("template_active_change_cancelled", "<gray>Active version change cancelled"),
    TEMPLATE_VERSION_MISMATCH(
        "template_version_mismatch",
        "<red>Template was created on Minecraft version <template_version>, attempting restore on version <server_version>. Please update the template to avoid restore errors."
    ),
    TEMPLATE_VERSION_MISMATCH_WARNING(
        "template_version_mismatch_warning",
        "<yellow>Template version <template_version> differs from server <server_version>. Proceeding anyway."
    ),
    TEMPLATE_VERSION_MISMATCH_SIMPLE(
        "template_version_mismatch_simple",
        "<red>Template version mismatch. Please update the template to avoid errors."
    ),
    INVALID_VERSION_ID("invalid_version_id", "<red>Invalid version ID: '<version>'"),

    // Restore messages
    RESTORE_STARTING("restore_starting", "<green>Restoring template '<name>' (v<version>)..."),
    RESTORE_STARTING_AT(
        "restore_starting_at",
        "<green>Restoring template '<name>' (v<version>) at chunk (<chunk_x>, <chunk_z>)..."
    ),
    RESTORE_SCHEDULED("restore_scheduled", "<green>Scheduled restore of '<name>' in <seconds> seconds"),
    RESTORE_TRIGGERED("restore_triggered", "<green>Triggered restore for <count> instance(s)"),

    // Instance messages
    INSTANCE_CREATED(
        "instance_created",
        "<green>Created manual instance '<id>' for template '<name>' at chunk (<chunk_x>, <chunk_z>)"
    ),
    INSTANCE_CREATED_ORIGINAL(
        "instance_created_original",
        "<green>Created manual instance '<id>' for template '<name>' at original chunk (<chunk_x>, <chunk_z>) and triggered restore."
    ),
    INSTANCE_NOT_FOUND("instance_not_found", "<red>Instance '<id>' not found"),
    INSTANCE_DELETED("instance_deleted", "<green>Deleted instance '<id>'"),
    INSTANCE_DELETE_CANCELLED("instance_delete_cancelled", "<gray>Instance deletion cancelled"),
    INSTANCE_RESTORE_TRIGGERED("instance_restore_triggered", "<green>Triggered restore for instance '<id>'"),
    INSTANCE_SAVE_FAILED("instance_save_failed", "<red>Failed to save instance state. Check server logs for details."),
    INSTANCE_LIST_EMPTY("instance_list_empty", "<gray>No instances found"),
    INSTANCE_LIST_HEADER("instance_list_header", "<green>Instances (<count>):"),
    INSTANCE_LIST_ITEM("instance_list_item", "  <type_mark> <id> - <name> at (<chunk_x>, <chunk_z>) in <world>"),
    INSTANCE_INFO_HEADER("instance_info_header", "<green>Instance: <id>"),
    INSTANCE_INFO_TEMPLATE("instance_info_template", "  Template: <name>"),
    INSTANCE_INFO_LOCATION("instance_info_location", "  Location: <world> chunk (<chunk_x>, <chunk_z>)"),
    INSTANCE_INFO_SIZE("instance_info_size", "  Size: <size_x>x<size_z> chunks"),
    INSTANCE_INFO_TYPE("instance_info_type", "  Type: <type>"),
    INSTANCE_INFO_CONFIG("instance_info_config", "  Config:"),
    INSTANCE_INFO_BOOT_RESTORE("instance_info_boot_restore", "    Boot restore: <value>"),
    INSTANCE_INFO_VACATE_RESTORE("instance_info_vacate_restore", "    Vacate restore: <value>"),
    INSTANCE_INFO_REPEAT("instance_info_repeat", "    Repeat: <value>"),

    // Timer messages
    TIMER_SET("timer_set", "<green>Set repeating timer for instance '<id>' every <interval> seconds"),
    TIMER_SET_TEMPLATE(
        "timer_set_template",
        "<green>Set repeating timer for template '<name>' at original location every <interval> seconds"
    ),
    TIMER_SET_HELP("timer_set_help", "<gray>Use '/regionrestore timer cancel id <id>' to stop"),
    TIMER_INSTANCE_ID_HELP(
        "timer_instance_id_help",
        "<gray>Instance ID: <id> (use '/regionrestore timer cancel id <id>' to stop)"
    ),
    TIMER_CANCELLED("timer_cancelled", "<green>Cancelled timer for instance '<id>'"),
    TIMER_CANCELLED_TEMPLATE(
        "timer_cancelled_template",
        "<green>Cancelled timers for <count> instance(s) of template '<name>'"
    ),
    TIMER_CONFIG_CANCELLED("timer_config_cancelled", "<gray>Timer configuration cancelled"),
    TIMER_NO_INSTANCES("timer_no_instances", "<yellow>No instances found for template '<name>' in this world"),

    // Cloner/pool messages
    CLONER_STATUS_HEADER("cloner_status_header", "<green>Mass Cloner Status:"),
    CLONER_STATUS_SEPARATOR("cloner_status_separator", "<gray>─────────────────────────────────"),
    CLONER_WORLD_NOT_MANAGED("cloner_world_not_managed", "<gray><world>: <yellow>Not managed"),
    CLONER_WORLD_HEADER("cloner_world_header", "<white><world>:"),
    CLONER_POOL_STATUS("cloner_pool_status", "  <status> <white><name>: <active>/<target> instances"),
    CLONER_POOL_VERSION("cloner_pool_version", "     <gray>Version: <version>"),
    CLONER_POOL_SETTINGS(
        "cloner_pool_settings",
        "     <gray>Separation: <separation> chunks | Boot restore: <boot> | Vacate restore: <vacate>"
    ),
    CLONER_POOL_REPEAT("cloner_pool_repeat", "     <gray>Repeat: Every <interval>s"),
    CLONER_RESTORE_STARTING("cloner_restore_starting", "<green>Triggering manual restore for cloner instances..."),
    CLONER_WORLD_NOT_MANAGED_ERROR(
        "cloner_world_not_managed_error",
        "<red>World '<world>' is not managed by Mass Cloner"
    ),
    CLONER_RESTORE_TRIGGERED("cloner_restore_triggered", "<green>Triggered restore for <count> instances"),
    CLONER_REGEN_CONFIRM(
        "cloner_regen_confirm",
        "<red>This command will destroy and reallocate all pooled instances. Use --force to confirm: /regionrestore cloner regen [world] --force"
    ),
    CLONER_WORLD_NOT_CONFIGURED("cloner_world_not_configured", "<red>World '<world>' is not configured in Mass Cloner"),
    CLONER_REGEN_COMPLETE("cloner_regen_complete", "<green>Regeneration complete:"),
    CLONER_REGEN_REMOVED("cloner_regen_removed", "  Removed: <count> pooled instances"),
    CLONER_REGEN_ALLOCATED("cloner_regen_allocated", "  Allocated: <count> pooled instances"),
    CLONER_REGEN_MANUAL_PRESERVED("cloner_regen_manual_preserved", "<yellow>Manual instances were preserved."),
    CLONER_SAVE_FAILED("cloner_save_failed", "<red>Failed to persist state. Check server logs for details."),
    CLONER_REGEN_FAILED(
        "cloner_regen_failed",
        "<red>Regeneration completed but failed to persist state. Check server logs."
    ),

    // Pool creation messages
    POOL_CREATED(
        "pool_created",
        "<green>Created/updated pool '<name>' in '<world>' with <count> new instance(s) (target=<target>)."
    ),
    POOL_CREATION_CANCELLED("pool_creation_cancelled", "<gray>Pool creation cancelled"),
    POOL_CREATION_FAILED("pool_creation_failed", "<red>Failed to create pool '<name>'. Check server logs for details."),
    POOL_SAVE_FAILED("pool_save_failed", "<red>Pool created but failed to persist state. Check server logs."),
    POOL_NO_TEMPLATES("pool_no_templates", "<gray>No templates available for pool creation"),
    POOL_STATUS_HEADER("pool_status_header", "<green>Pool status for '<name>' in '<world>':"),
    POOL_INSTANCES_LINE("pool_instances_line", "  <gray>Instances: <active>/<target> (<status>)"),
    POOL_VERSION_LINE("pool_version_line", "  <gray>Version: <version>"),
    POOL_SETTINGS_LINE(
        "pool_settings_line",
        "  <gray>Separation: <separation> chunks | Boot restore: <boot> | Vacate restore: <vacate>"
    ),
    POOL_REPEAT_LINE("pool_repeat_line", "  <gray>Repeat: Every <interval>s"),
    POOL_RESTORE_TRIGGERED(
        "pool_restore_triggered",
        "<green>Triggered restore for <count> instance(s) in pool '<name>'"
    ),
    POOL_REGEN_HEADER("pool_regen_header", "<green>Regenerated pooled instances for '<world>':"),
    POOL_REGEN_STATS("pool_regen_stats", "  Removed: <removed>, Allocated: <allocated>"),
    POOL_NO_INSTANCES("pool_no_instances", "<gray>No instances for this pool"),
    POOL_INSTANCES_HEADER("pool_instances_header", "<green>Instances for pool '<name>' in '<world>':"),
    POOL_INSTANCE_LINE("pool_instance_line", "  <type_mark> <id> - <template> at (<chunk_x>, <chunk_z>)"),

    // GUI result messages
    GUI_CLOSED_ACTION("gui_closed_action", "<gray>GUI closed after action: <action>"),
    GUI_CANCELLED("gui_cancelled", "<gray>GUI cancelled"),
    GUI_CLOSED("gui_closed", "<gray>GUI closed"),

    // Selection wand messages
    WAND_GIVEN("wand_given", "<green>Selection wand given. Right-click to set corners."),
    WAND_ALREADY_HAVE("wand_already_have", "<yellow>You already have a selection wand."),
    WAND_POS1_SET("wand_pos1_set", "<green>Position 1 set to (<x>, <y>, <z>)"),
    WAND_POS2_SET("wand_pos2_set", "<green>Position 2 set to (<x>, <y>, <z>)"),
    WAND_SELECTION_COMPLETE(
        "wand_selection_complete",
        "<green>Selection complete! <width>x<length> blocks (<chunk_width>x<chunk_length> chunks)"
    ),
    WAND_WORLD_CHANGED("wand_world_changed", "<yellow>World changed, previous position cleared."),
    SELECTION_CLEARED("selection_cleared", "<gray>Selection cleared."),
    SELECTION_NONE("selection_none", "<red>No selection made. Use the wand to select two corners first."),
    SELECTION_INCOMPLETE("selection_incomplete", "<red>Selection incomplete. Set both corners with the wand."),
    SELECTION_INFO(
        "selection_info",
        "<green>Selection: <world> from (<min_x>, <min_z>) to (<max_x>, <max_z>) - <chunk_width>x<chunk_length> chunks"
    ),
    TEMPLATE_FROM_SELECTION("template_from_selection", "<green>Creating template '<name>' from selection...")
}

/**
 * Message keys for RegionRestore GUI elements.
 */
enum class GuiMessages(
    override val key: String,
    override val default: String
) : MessageKey {
    // Main GUI
    MAIN_TITLE("main_title", "RegionRestore"),

    // Templates section
    TEMPLATES_TITLE("templates_title", "Templates"),
    TEMPLATE_ITEM_TITLE("template_item_title", "Template: <name>"),
    TEMPLATE_INFO_TITLE("template_info_title", "Info"),
    TEMPLATE_INFO_LINE("template_info_line", "Template: <name>"),
    TEMPLATE_DESC_LINE("template_desc_line", "Description: <description>"),
    TEMPLATE_NO_DESCRIPTION("template_no_description", "No description"),

    // Versions section
    VERSIONS_TITLE("versions_title", "Versions"),
    VERSION_ACTIVE("version_active", "v<version> (active)"),
    VERSION_NORMAL("version_normal", "v<version>"),
    VERSION_CREATED("version_created", "Created: <timestamp>"),
    VERSION_MINECRAFT("version_minecraft", "Minecraft: <version>"),
    VERSION_DESCRIPTION("version_description", "Description: <description>"),

    // Template actions
    RESTORE_ORIGINAL_TITLE("restore_original_title", "Restore (original position)"),
    RESTORE_ORIGINAL_DESC("restore_original_desc", "Restore at the template's saved position"),
    RESTORE_HERE_TITLE("restore_here_title", "Restore here"),
    RESTORE_HERE_DESC("restore_here_desc", "Restore at your current location"),
    CREATE_INSTANCE_TITLE("create_instance_title", "Create instance at template location"),
    CREATE_INSTANCE_DESC(
        "create_instance_desc",
        "Create a manual instance at the template's saved location and restore it"
    ),
    SET_ACTIVE_TITLE("set_active_title", "Set active version"),
    SET_ACTIVE_DESC("set_active_desc", "Set the active version for this template"),
    SET_ACTIVE_PROMPT("set_active_prompt", "Active version for <name>"),
    DELETE_TEMPLATE_TITLE("delete_template_title", "Delete template"),
    DELETE_TEMPLATE_DESC("delete_template_desc", "Delete this template and all its versions"),
    DELETE_TEMPLATE_PROMPT("delete_template_prompt", "Type '<name>' to confirm delete"),

    // Cloner section
    CLONER_TITLE("cloner_title", "Cloner / Pools"),
    CREATE_POOL_HERE_TITLE("create_pool_here_title", "Create pool in this world (runtime)"),
    CREATE_POOL_HERE_DESC("create_pool_here_desc", "Create a basic pool in your current world using a template"),
    SELECT_TEMPLATE_TITLE("select_template_title", "Select template for pool in <world>"),
    WORLD_TITLE("world_title", "World: <world>"),
    CREATE_POOL_TITLE("create_pool_title", "Create pool (runtime)"),
    CREATE_POOL_DESC("create_pool_desc", "Create a basic pool in this world using a template"),
    CONFIGURE_CREATE_TITLE("configure_create_title", "Configure & create pool"),
    CONFIGURE_CREATE_DESC("configure_create_desc", "Set count and separation for this pool"),
    INSTANCE_COUNT_PROMPT("instance_count_prompt", "Instance count"),
    SEPARATION_PROMPT("separation_prompt", "Separation (chunks)"),
    POOL_TITLE("pool_title", "Pool: <name>"),

    // Pool actions
    STATUS_TITLE("status_title", "Status"),
    RESTORE_ALL_TITLE("restore_all_title", "Restore all instances"),
    RESTORE_ALL_DESC("restore_all_desc", "Trigger restore for all instances in this pool"),
    REGEN_POOL_TITLE("regen_pool_title", "Regenerate pooled instances"),
    REGEN_POOL_DESC("regen_pool_desc", "Reallocate pooled instances for this world"),
    SHOW_INSTANCES_TITLE("show_instances_title", "Show instances"),
    SHOW_INSTANCES_DESC("show_instances_desc", "Open instance list for this pool"),

    // Instances section
    INSTANCES_TITLE("instances_title", "Instances & Timers"),
    CREATE_MANUAL_TITLE("create_manual_title", "Create manual instance here"),
    CREATE_MANUAL_DESC("create_manual_desc", "Create instance of '<name>' at your current chunk"),
    ALL_INSTANCES_TITLE("all_instances_title", "All instances"),
    WORLD_INSTANCES_TITLE("world_instances_title", "Instances in this world"),

    // Instance node
    INSTANCE_NODE_TITLE("instance_node_title", "<type_mark> <name> @ <world> (<chunk_x>, <chunk_z>)"),
    INSTANCE_INFO_TITLE("instance_info_title", "Info"),
    INSTANCE_RESTORE_TITLE("instance_restore_title", "Restore"),
    INSTANCE_DELETE_TITLE("instance_delete_title", "Delete"),

    // Timer section
    TIMERS_TITLE("timers_title", "Timers"),
    VIEW_TIMER_TITLE("view_timer_title", "View timer"),
    TIMER_NO_CONFIG("timer_no_config", "No timer configured"),
    TIMER_INTERVAL("timer_interval", "Interval: <interval> seconds"),
    TIMER_AUDIENCE("timer_audience", "Audience: <scope>"),
    SET_TIMER_TITLE("set_timer_title", "Set / update timer"),
    DELETE_TIMER_TITLE("delete_timer_title", "Delete timer"),
    TIMER_INTERVAL_PROMPT("timer_interval_prompt", "Interval seconds (>= 1)"),

    // Confirmation dialog
    CONFIRM_DELETE_TITLE("confirm_delete_title", "Delete instance?"),
    CONFIRM_DELETE_INFO("confirm_delete_info", "Confirm deletion"),
    CONFIRM_INSTANCE_LINE("confirm_instance_line", "Instance: <id>"),
    CONFIRM_TEMPLATE_LINE("confirm_template_line", "Template: <name>"),
    CONFIRM_WORLD_LINE("confirm_world_line", "World: <world>"),
    CONFIRM_WARNING("confirm_warning", "This cannot be undone."),

    // Validation
    INPUT_EMPTY("input_empty", "Input cannot be empty"),

    // Create template from GUI
    CREATE_TEMPLATE_TITLE("create_template_title", "Create new template"),
    CREATE_TEMPLATE_DESC("create_template_desc", "Create a new template from a region selection"),
    CREATE_TEMPLATE_NAME_PROMPT("create_template_name_prompt", "Template name"),
    CREATE_TEMPLATE_MIN_X_PROMPT("create_template_min_x_prompt", "Min X coordinate"),
    CREATE_TEMPLATE_MIN_Z_PROMPT("create_template_min_z_prompt", "Min Z coordinate"),
    CREATE_TEMPLATE_MAX_X_PROMPT("create_template_max_x_prompt", "Max X coordinate"),
    CREATE_TEMPLATE_MAX_Z_PROMPT("create_template_max_z_prompt", "Max Z coordinate"),

    // Instance creation - original position
    CREATE_INSTANCE_ORIGINAL_TITLE("create_instance_original_title", "Create instance at original position"),
    CREATE_INSTANCE_ORIGINAL_DESC("create_instance_original_desc", "Create instance at the template's saved location"),

    // Instance creation - custom position
    CREATE_INSTANCE_CUSTOM_TITLE("create_instance_custom_title", "Create instance at custom position"),
    CREATE_INSTANCE_CUSTOM_DESC("create_instance_custom_desc", "Create instance at a specified world and coordinates"),
    CREATE_INSTANCE_WORLD_PROMPT("create_instance_world_prompt", "World name"),
    CREATE_INSTANCE_CHUNK_X_PROMPT("create_instance_chunk_x_prompt", "Chunk X coordinate"),
    CREATE_INSTANCE_CHUNK_Z_PROMPT("create_instance_chunk_z_prompt", "Chunk Z coordinate")
}
