package io.lauren.renvault.lib

import androidx.compose.runtime.Composable

class RvAutofillSetup(val enabled: Boolean, val set: (Boolean) -> Unit)

@Composable
expect fun rememberAutofillSetup(): RvAutofillSetup?
