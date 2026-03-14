package bruh.zchat.utils.menuapi

/**
 * Internal [SimpleMenu] subclass used by [MenuAPI.simple] builder.
 * Captures items set during the builder block and replays them
 * via [populateItems] on every open/refresh.
 */
@PublishedApi
internal class BuilderSimpleMenu : SimpleMenu() {
    @PublishedApi
    internal val builderItems: MutableMap<Int, VItem> = mutableMapOf()

    override fun populateItems() {
        items.clear()
        items.putAll(builderItems)
    }
}

/**
 * Internal [PaginatedMenu] subclass used by [MenuAPI.paginated] builder.
 * Captures chrome items set during the builder block and replays them
 * via [populateItems] on every open/refresh.
 */
@PublishedApi
internal class BuilderPaginatedMenu<T> : PaginatedMenu<T>() {
    @PublishedApi
    internal val builderItems: MutableMap<Int, VItem> = mutableMapOf()

    override fun populateItems() {
        items.clear()
        items.putAll(builderItems)
    }
}
