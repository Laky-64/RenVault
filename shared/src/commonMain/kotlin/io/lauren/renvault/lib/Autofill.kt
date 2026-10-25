package io.lauren.renvault.lib

import androidx.compose.runtime.Composable

class RvAutofillSetup(val enabled: Boolean, val inlineSupported: Boolean, val set: (Boolean) -> Unit)

private const val InlineKey = "autofill_inline"

fun autofillInline(): Boolean = readPreference(InlineKey) != "off"

fun setAutofillInline(on: Boolean) = writePreference(InlineKey, if (on) "on" else "off")

private const val CopyTotpKey = "autofill_copy_totp"

fun autofillCopyTotp(): Boolean = readPreference(CopyTotpKey) == "on"

fun setAutofillCopyTotp(on: Boolean) = writePreference(CopyTotpKey, if (on) "on" else "off")

@Composable
expect fun rememberAutofillSetup(): RvAutofillSetup?
