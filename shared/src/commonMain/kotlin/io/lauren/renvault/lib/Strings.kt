package io.lauren.renvault.lib

interface RvStrings {
    val fieldUsername: String
    val fieldPassword: String
    val fieldWebsite: String
    val fieldVerificationCode: String
    val fieldNotes: String
    val fieldModified: String
    val fieldPasskey: String
    val fieldNetworkName: String
    val fieldCopied: String
    val itemWifiNetwork: String
    val itemCompromised: String

    val editNoWebsite: String
    val editSaveForCode: String
    val editNoCode: String
    val editDeleteCode: String
    val editSetUpCode: String
    val editDelete: String
    val editorUsernameHint: String
    val editorNotes: String
    val editorNewPassword: String
    val editorCancel: String
    val editorSave: String
    val editorTitle: String
    val editorWebsiteHint: String
    val securityChangeAction: String
    val securityChangeTitle: String
    val passkeyNoteDesc: String
    val passkeyLearnMore: String

    val deletedRecover: String
    val deletedDelete: String

    val searchPlaceholder: String
    val searchEmptyDesc: String

    val navBack: String
    val listSelect: String
    val listSelectAll: String
    val listDeselectAll: String
    val itemEdit: String
    val listDelete: String
    val listOrder: String
    val listAdd: String
    val sortAscending: String
    val sortDescending: String
    val sortModified: String
    val sortCreated: String
    val sortWebsite: String
    val sortTitle: String
    val settingsTitle: String
    val settingsClose: String
    val settingsGeneralTitle: String
    val settingsLanguage: String
    val settingsSecurityTitle: String
    val settingsAutoLock: String
    val settingsAutoLockNever: String
    val settingsAutoLockMinute: String
    val settingsAutoLockHour: String
    val settingsAutoLockNote: String
    val settingsLockNow: String
    val settingsCheckPwned: String
    val settingsAboutTitle: String
    val settingsLastSync: String
    val settingsSyncedNever: String
    val settingsSignOut: String
    val settingsSignOutNote: String
    val settingsSignOutTitle: String
    val settingsSignOutDesc: String
    val settingsOpenLabel: String
    val cancel: String
    val settingsVersion: String
    val settingsAdvancedTitle: String
    val anisetteTitle: String
    val anisetteDesc: String
    val anisettePlaceholder: String
    val anisetteDefault: String
    val anisetteReset: String
    val anisetteInvalid: String
    val anisetteNote: String
    val websitesTitle: String
    val websitesPlaceholder: String
    val websitesAdd: String
    val totpKeyPlaceholder: String
    val totpKeyConfirm: String

    val authSignInTitle: String
    val authSignInHint: String
    val authEmailPlaceholder: String
    val authPasswordPlaceholder: String
    val authSignInAction: String
    val authPrivacyNotice: String
    val authPrivacyLink: String
    val authTwoFactorTitle: String
    val authTwoFactorHint: String
    val authTwoFactorResend: String
    val authDeviceSelectionTitle: String
    val authDeviceSelectionHint: String
    val authDevicePasscodeTitle: String
    val authDevicePasscodePlaceholder: String
    val authContinueAction: String
    val authMasterSetupTitle: String
    val authMasterSetupHint: String
    val authMasterPlaceholder: String
    val authMasterRepeatPlaceholder: String
    val authMasterMismatch: String
    val authUnlockTitle: String
    val authUnlockHint: String
    val authUnlockAction: String

    val busySigningInTitle: String
    val busyCheckingCredentials: String
    val busyOpeningVaultTitle: String
    val busyOpeningVault: String
    val busySendingCode: String
    val busyVerifyingCode: String
    val busyVerifyingPasscode: String
    val busyCreatingVaultTitle: String
    val busyCreatingVault: String

    val strengthWeak: String
    val strengthFair: String
    val strengthStrong: String

    val failureGeneric: String
    val failureBadCredentials: String
    val failureAccountLocked: String
    val failureWrongPasscode: String
    val failureWrongMasterPassword: String
    val failureNoDevices: String

    fun authDevicePasscodeHint(device: String): String
    fun websitesDesc(title: String): String
    fun totpSetupDesc(site: String): String
    fun authMasterTooShort(count: Int): String
    fun fieldWebsiteMore(domain: String, count: Int): String
    fun fieldPasskeyCreationDate(date: String): String
    fun deleteTitle(count: Int): String
    fun deleteSoftDesc(count: Int): String
    fun deleteHardDesc(count: Int): String
    fun deleteConfirm(count: Int): String
    fun deletedCountdown(count: Int): String
    fun listSelectedCount(count: Int): String
    fun zoneItemCount(count: Int): String
    fun searchPlaceholderIn(zone: String): String
    fun searchEmpty(query: String): String
    fun securityChangeDesc(domain: String): String
    fun settingsAutoLockMinutes(count: Int): String
    fun settingsPending(count: Int): String
    fun relativeTime(value: RvRelative): String
    fun zoneText(kind: RvZoneKind, empty: Boolean): RvZoneText
}

enum class RvZoneKind { All, Passkeys, Codes, Networks, Security, Deleted }

data class RvZoneText(val name: String, val emptyTitle: String, val emptyDescription: String)
