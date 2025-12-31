package bruh.regionrestore.utils

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.minimessage.tag.Tag
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import java.util.function.Supplier

fun Audience.sendMiniMessage(
    template: String,
    placeholders: Map<String, Supplier<Component>> = mapOf()
) {
    val smartResolver = TagResolver.caching { requestedKey ->
        placeholders[requestedKey]?.get()?.let { Tag.inserting(it) }
    }

    this.sendMessage(MiniMessage.miniMessage().deserialize(template, smartResolver))
}