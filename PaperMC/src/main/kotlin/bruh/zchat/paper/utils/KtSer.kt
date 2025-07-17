package bruh.zchat.paper.utils

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

fun String.legacySerial(): Component {
    return LegacyComponentSerializer.legacySection().deserialize(this)
}

fun String.legacyAmpersand(): Component {
    return LegacyComponentSerializer.legacyAmpersand().deserialize(this)
}

fun String.plainComponent(): Component {
    return PlainTextComponentSerializer.plainText().deserialize(this)
}