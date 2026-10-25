package io.lauren.renvault.lib

import androidx.compose.runtime.Composable

class RvAutofillSetup(val enabled: Boolean, val inlineSupported: Boolean, val set: (Boolean) -> Unit)

private const val InlineKey = "autofill_inline"

fun autofillInline(): Boolean = readPreference(InlineKey) != "off"

fun setAutofillInline(on: Boolean) = writePreference(InlineKey, if (on) "on" else "off")

@Composable
expect fun rememberAutofillSetup(): RvAutofillSetup?
