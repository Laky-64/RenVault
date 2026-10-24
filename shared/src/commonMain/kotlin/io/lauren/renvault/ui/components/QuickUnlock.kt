package io.lauren.renvault.ui.components

interface RvQuickUnlock {
    val label: String
    suspend fun ready(): Boolean
    suspend fun unlock(onVerified: () -> Unit)
}
