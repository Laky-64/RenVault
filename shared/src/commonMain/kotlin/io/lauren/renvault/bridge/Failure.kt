package io.lauren.renvault.bridge

import io.lauren.renvault.lib.unescape

enum class FailureReason {
    NotInitialised,
    Locked,
    Cancelled,
    BadCredentials,
    AccountLocked,
    WrongPasscode,
    WrongMasterPassword,
    NoDevices,
    Rejected,
    PairRefused,
    PairMissing,
    NoNetwork,
    BadPhoto,
    BackupPassword,
    BackupFormat,
    Generic,
}

data class Failure(
    val kind: Int,
    val message: String,
) {
    val reason: FailureReason get() = reasonOf(kind, message)
    val detail: String? get() = detailOf(message)
}

private val REJECTED = Regex("""\bec=(-?\d+)\s+em=("(?:[^"\\]|\\.)*")""")
private val ESCROW = Regex("""\bescrow:.*?\b(?:http status code|status) (\d{3})\b""")
private val BAD_MASTER = Regex("""message authentication failed""", RegexOption.IGNORE_CASE)

private val REJECTION_CODES = mapOf(
    -20101 to FailureReason.BadCredentials,
    -20751 to FailureReason.AccountLocked,
)

private fun reasonOf(kind: Int, message: String): FailureReason = when (kind) {
    FailureKind.NOT_INITIALISED -> FailureReason.NotInitialised
    FailureKind.LOCKED -> FailureReason.Locked
    FailureKind.CANCELLED -> FailureReason.Cancelled
    FailureKind.NO_BOTTLES -> FailureReason.NoDevices
    else -> classify(message)
}

private fun classify(raw: String): FailureReason {
    REJECTED.find(raw)?.let { match ->
        val code = match.groupValues[1].toIntOrNull()
        REJECTION_CODES[code]?.let { return it }
        return if (unquote(match.groupValues[2]).isBlank()) {
            FailureReason.Generic
        } else {
            FailureReason.Rejected
        }
    }

    ESCROW.find(raw)?.let { match ->
        return if (match.groupValues[1] == "409") FailureReason.WrongPasscode else FailureReason.Generic
    }

    if (BAD_MASTER.containsMatchIn(raw)) return FailureReason.WrongMasterPassword
    if ("pairing was refused" in raw) return FailureReason.PairRefused
    if ("no pairing in progress" in raw) return FailureReason.PairMissing
    if ("not connected to a network" in raw) return FailureReason.NoNetwork
    if ("must be a JPEG or PNG" in raw) return FailureReason.BadPhoto
    if ("wrong backup password" in raw) return FailureReason.BackupPassword
    if ("not a RenVault backup" in raw || "no password column" in raw || "needs a newer RenVault" in raw) {
        return FailureReason.BackupFormat
    }

    return FailureReason.Generic
}

private fun detailOf(raw: String): String? {
    val match = REJECTED.find(raw) ?: return null
    val code = match.groupValues[1].toIntOrNull()
    if (REJECTION_CODES.containsKey(code)) return null
    return unquote(match.groupValues[2]).trim().takeIf(String::isNotEmpty)
}

private fun unquote(quoted: String): String = unescape(quoted.removeSurrounding("\""))
