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
    val settingsIdentityTitle: String
    val namesTitle: String
    val namesOff: String
    val namesOn: String
    val namesIntro: String
    val namesEntry: String
    val namesAdd: String
    val namesNotice: String
    val namesNoticeDesc: String
    val namesDialogTitle: String
    val namesHiddenLabel: String
    val namesShownLabel: String
    val namesRemove: String
    val namesWhereTitle: String
    val namesWhereEmpty: String
    val namesWhereFooter: String
    val namesNote: String
    val itemNameHiddenTitle: String
    val itemNameHiddenDesc: String
    val itemNameOriginalDesc: String
    val itemNameShowOriginal: String
    val itemNameHideOriginal: String
    val editorNameHidden: String
    val fieldCopiedOriginal: String
    val settingsTheme: String
    val themeSystem: String
    val themeLight: String
    val themeDark: String
    val themeChoose: String
    val pwnedChecking: String
    val pwnedNone: String
    val pwnedFailed: String
    val pwnedShow: String
    val settingsPwnedNever: String
    val settingsAboutTitle: String
    val settingsLastSync: String
    val settingsSyncedNever: String
    val settingsSignOut: String
    val settingsSignOutNote: String
    val settingsSignOutTitle: String
    val settingsSignOutDesc: String
    val settingsOpenLabel: String
    val cancel: String
    val settingsChannelStable: String
    fun settingsBasedOn(core: String): String
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
    val trayOpen: String
    val trayQuit: String
    val photoFilter: String
    val settingsSyncTitle: String
    val authLocalAction: String
    val authLocalTitle: String
    val authLocalHint: String
    val authLocalNew: String
    val authLocalNewDesc: String
    val authLocalJoin: String
    val authLocalJoinDesc: String
    val authProfileTitle: String
    val authProfileHint: String
    val profileName: String
    val profilePhotoPick: String
    val profilePhotoRemove: String
    val profileEditTitle: String
    val authJoinTitle: String
    val authJoinSkip: String
    val pairJoinHint: String
    val pairJoinManualHint: String
    val pairScan: String
    val pairCodeLabel: String
    val pairJoinAction: String
    val pairHostHint: String
    val pairWaiting: String
    val pairRetry: String
    val pairClose: String
    val busyJoiningTitle: String
    val busyJoining: String
    val devicesTitle: String
    val devicesIntro: String
    val devicesThis: String
    val devicesNever: String
    val devicesAdd: String
    val devicesJoin: String
    val devicesSyncNow: String
    val devicesSyncDone: String
    val devicesSyncFailed: String
    val devicesRemove: String
    val devicesRemoveDesc: String
    val devicesFooter: String
    val settingsLocalVault: String
    val settingsDeleteVault: String
    val settingsDeleteVaultTitle: String
    val settingsDeleteVaultDesc: String
    val settingsDeleteVaultNote: String
    val failurePairRefused: String
    val failurePairMissing: String
    val failureNoNetwork: String
    val failureBadPhoto: String
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
    fun namesFound(count: Int): String
    fun settingsPwnedLast(time: String): String
    fun pwnedFound(count: Int): String
    fun pairDone(name: String): String
    fun devicesCount(count: Int): String
    fun devicesSynced(time: String): String
    fun devicesRemoveTitle(name: String): String
    fun relativeTime(value: RvRelative): String
    fun zoneText(kind: RvZoneKind, empty: Boolean): RvZoneText
}

enum class RvZoneKind { All, Passkeys, Codes, Networks, Security, Deleted }

data class RvZoneText(val name: String, val emptyTitle: String, val emptyDescription: String)
