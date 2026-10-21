package io.lauren.renvault.lib

internal fun unescape(raw: String): String {
    if (!raw.contains('\\')) return raw

    val out = StringBuilder(raw.length)
    var index = 0

    while (index < raw.length) {
        val char = raw[index]
        if (char != '\\' || index == raw.lastIndex) {
            out.append(char)
            index++
            continue
        }

        when (val escaped = raw[index + 1]) {
            'n' -> { out.append('\n'); index += 2 }
            't' -> { out.append('\t'); index += 2 }
            'r' -> { out.append('\r'); index += 2 }
            'b' -> { out.append('\b'); index += 2 }
            'f' -> { out.append('\u000C'); index += 2 }
            'u' -> {
                val code = raw.drop(index + 2).take(4)
                val point = code.takeIf { it.length == 4 }?.toIntOrNull(16)
                if (point == null) {
                    out.append(escaped)
                    index += 2
                } else {
                    out.append(point.toChar())
                    index += 6
                }
            }
            else -> { out.append(escaped); index += 2 }
        }
    }

    return out.toString()
}
