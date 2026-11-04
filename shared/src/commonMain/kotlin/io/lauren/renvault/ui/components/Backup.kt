package io.lauren.renvault.ui.components

data class RvImported(val added: Int, val updated: Int)

data class RvImportPreview(
    val source: String,
    val entries: Int,
    val codes: Int,
    val passkeys: Int,
    val skipped: Int,
    val locked: Boolean,
)

interface RvBackupActions {
    suspend fun export(password: String): ByteArray
    suspend fun restore(data: ByteArray, password: String): RvImported
    suspend fun importCsv(data: ByteArray): RvImported
    suspend fun exportCsv(): ByteArray
    suspend fun inspectCsv(data: ByteArray): RvImportPreview
    suspend fun inspectArchive(data: ByteArray, password: String): RvImportPreview
    suspend fun inspectBackup(data: ByteArray, password: String): RvImportPreview
    suspend fun importArchive(data: ByteArray, password: String): RvImported
    fun describe(failure: Throwable): String
}

internal const val MinBackupPassword = 8
