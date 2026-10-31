package io.lauren.renvault.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lauren.renvault.bridge.Vault

private const val LocalKeyPreference = "local_key"

object RvLocalKey {
    var supported by mutableStateOf(false)
        private set

    var permitted by mutableStateOf(false)
        private set

    var enabled by mutableStateOf(readPreference(LocalKeyPreference) != "off")
        private set

    fun choose(on: Boolean) {
        enabled = on
        writePreference(LocalKeyPreference, if (on) "on" else "off")
    }

    suspend fun grant(): Boolean {
        runCatching { Vault.localKeyGrant() }
        val status = runCatching { Vault.localKeyStatus() }.getOrNull()
        update(status?.supported == true, status?.permitted == true)
        return permitted
    }

    internal fun update(supported: Boolean, permitted: Boolean) {
        this.supported = supported
        this.permitted = permitted
    }
}
