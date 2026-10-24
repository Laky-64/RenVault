package io.lauren.renvault.lib

val ClipboardClearChoices = listOf(0, 10, 20, 30, 60, 120, 300)

private const val ClipboardClearKey = "clipboard_clear_seconds"
private const val DefaultClipboardClear = 30

fun clipboardClearSeconds(): Int =
    readPreference(ClipboardClearKey)?.toIntOrNull()?.takeIf { it in ClipboardClearChoices } ?: DefaultClipboardClear

fun setClipboardClearSeconds(seconds: Int) = writePreference(ClipboardClearKey, seconds.toString())
