package io.lauren.renvault.lib

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.CancellationException

const val MinPassword = 8

enum class RvPasswordStrength { None, Weak, Fair, Strong, VeryStrong }

@Composable
fun rememberStrength(password: String): RvPasswordStrength {
    val level by produceState(RvPasswordStrength.None, password) {
        val index = try {
            Vault.passwordStrength(password)
        } catch (failure: CancellationException) {
            throw failure
        } catch (_: Exception) {
            0
        }
        value = RvPasswordStrength.entries.getOrElse(index) { RvPasswordStrength.None }
    }
    return level
}

fun RvStrings.strengthLabel(level: RvPasswordStrength): String? = when (level) {
    RvPasswordStrength.None -> null
    RvPasswordStrength.Weak -> strengthWeak
    RvPasswordStrength.Fair -> strengthFair
    RvPasswordStrength.Strong -> strengthStrong
    RvPasswordStrength.VeryStrong -> strengthVeryStrong
}
