package io.lauren.renvault

import io.lauren.renvault.bridge.FailureReason
import io.lauren.renvault.bridge.VaultException
import io.lauren.renvault.lib.RvStrings

fun RvStrings.failureText(cause: Throwable): String {
    val failure = (cause as? VaultException)?.failure ?: return failureGeneric

    return when (failure.reason) {
        FailureReason.BadCredentials -> failureBadCredentials
        FailureReason.AccountLocked -> failureAccountLocked
        FailureReason.WrongPasscode -> failureWrongPasscode
        FailureReason.WrongMasterPassword -> failureWrongMasterPassword
        FailureReason.NoDevices -> failureNoDevices
        FailureReason.Rejected -> failure.detail ?: failureGeneric
        else -> failureGeneric
    }
}
