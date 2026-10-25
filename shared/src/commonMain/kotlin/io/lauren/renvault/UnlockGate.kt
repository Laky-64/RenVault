package io.lauren.renvault

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import io.lauren.renvault.bridge.FailureReason
import io.lauren.renvault.bridge.QuickState
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.bridge.VaultException
import io.lauren.renvault.lib.RvBiometricCancelled
import io.lauren.renvault.lib.RvBiometrics
import io.lauren.renvault.lib.RvLockGuard
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.currentLocaleTag
import io.lauren.renvault.lib.rememberBiometrics
import io.lauren.renvault.ui.LocalStrings
import io.lauren.renvault.ui.components.RvAuthFlow
import io.lauren.renvault.ui.components.RvQuickUnlock
import io.lauren.renvault.ui.rememberStrings

@Composable
fun RvUnlockGate(onUnlocked: () -> Unit, modifier: Modifier = Modifier) {
    val strings = rememberStrings(currentLocaleTag())
    val quick = rememberQuickUnlock(strings, rememberBiometrics())
    CompositionLocalProvider(LocalStrings provides strings) {
        RvAuthFlow(
            modifier = modifier,
            strings = strings,
            describe = { strings.failureText(it) },
            unlocked = { Vault.state().unlocked },
            configured = { Vault.state().configured },
            unlock = { secret -> Vault.unlock(secret) },
            quick = quick,
            onDone = onUnlocked,
        )
    }
}

@Composable
internal fun rememberQuickUnlock(strings: RvStrings, biometrics: RvBiometrics?): RvQuickUnlock =
    remember(strings, biometrics) {
        object : RvQuickUnlock {
            private var state = QuickState()

            override val label: String get() = quickLabel(state.method, strings)

            override suspend fun ready(): Boolean {
                state = Vault.quickState()
                if (!state.ready) return false
                val local = biometrics
                if (local != null && state.method == local.method) return local.available() && local.hasKey()
                return state.method in state.methods
            }

            override suspend fun unlock(onVerified: () -> Unit) {
                val current = Vault.quickState()
                val local = biometrics
                if (local == null || current.method != local.method) {
                    RvLockGuard.expectLeave()
                    onVerified()
                    nativeQuick { Vault.unlockQuick(current.method) }
                    return
                }
                val key = local.open(current.sealed, strings.quickPromptTitle, strings.cancel)
                onVerified()
                try {
                    Vault.unlockQuick(current.method, key)
                } finally {
                    key.fill(0)
                }
            }
        }
    }

internal const val TouchIdMethod = "touch-id"

internal fun quickLabel(method: String, strings: RvStrings): String = when (method) {
    "android-biometric" -> strings.quickBiometrics
    "windows-hello" -> "Windows Hello"
    TouchIdMethod -> "Touch ID"
    else -> ""
}

internal suspend fun nativeQuick(block: suspend () -> Unit) {
    try {
        block()
    } catch (failure: VaultException) {
        if (failure.failure.reason == FailureReason.Cancelled) throw RvBiometricCancelled()
        throw failure
    }
}
