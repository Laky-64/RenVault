package io.lauren.renvault.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.bridge.WinPasskeyStatus

object RvWinPasskeys {
    var status by mutableStateOf(WinPasskeyStatus())
        private set

    var restartNeeded by mutableStateOf(false)
        private set

    var onQuit: () -> Unit = {}

    suspend fun refresh() {
        status = runCatching { Vault.winPasskeyStatus() }.getOrDefault(WinPasskeyStatus())
    }

    suspend fun enable(): Result<Unit> {
        val result = runCatching { Vault.winPasskeyEnable() }
        result.getOrNull()?.let { restartNeeded = it.restart }
        refresh()
        return result.map { }
    }

    suspend fun openSettings() {
        runCatching { Vault.winPasskeySettings() }
    }

    suspend fun restart() {
        if (runCatching { Vault.winPasskeyRestart() }.isSuccess) onQuit()
    }

    fun dismissRestart() {
        restartNeeded = false
    }
}
