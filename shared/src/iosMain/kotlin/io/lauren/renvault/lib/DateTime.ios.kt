package io.lauren.renvault.lib

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterLongStyle
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970

private val cache = HashMap<RvDateStyle, NSDateFormatter>()

private fun formatter(style: RvDateStyle): NSDateFormatter = cache.getOrPut(style) {
    NSDateFormatter().apply {
        dateStyle = when (style) {
            RvDateStyle.Short -> NSDateFormatterShortStyle
            RvDateStyle.Medium -> NSDateFormatterMediumStyle
            RvDateStyle.Long -> NSDateFormatterLongStyle
        }
        timeStyle = NSDateFormatterNoStyle
    }
}

actual fun localizedDate(millis: Long, style: RvDateStyle): String =
    formatter(style).stringFromDate(NSDate.dateWithTimeIntervalSince1970(millis / 1000.0))
