package io.lauren.renvault.lib

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

private val cache = HashMap<String, DateTimeFormatter>()

private fun formatter(style: RvDateStyle): DateTimeFormatter {
    val locale = Locale.getDefault()
    val key = "$locale:$style"
    return cache.getOrPut(key) {
        val shape = when (style) {
            RvDateStyle.Short -> FormatStyle.SHORT
            RvDateStyle.Medium -> FormatStyle.MEDIUM
            RvDateStyle.Long -> FormatStyle.LONG
        }
        DateTimeFormatter.ofLocalizedDate(shape).withLocale(locale).withZone(ZoneId.systemDefault())
    }
}

actual fun localizedDate(millis: Long, style: RvDateStyle): String =
    formatter(style).format(Instant.ofEpochMilli(millis))
