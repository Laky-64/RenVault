package io.lauren.renvault.lib

interface RvStrings {
    val strengthVeryStrong: String
    val fieldUsername: String
    val fieldPassword: String
    val fieldWebsite: String
    val fieldApp: String
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
    val editDeletePasskey: String
    val passkeyComputer: String
    val localKeySection: String
    val localKeyTitle: String
    val localKeyDesc: String
    fun localKeyGet(site: String): String
    fun localKeyCreate(site: String): String
    val localKeyGetHint: String
    val localKeyCreateHint: String
    val localKeySave: String
    val localKeyPermission: String
    val winPasskeysTitle: String
    val winPasskeysDesc: String
    val winPasskeysEnableHint: String
    val winPasskeysActive: String
    val winPasskeysRestartTitle: String
    val winPasskeysRestartDesc: String
    val winPasskeysRestart: String
    val winPasskeysFailed: String
    val winPasskeysMissing: String
    val masterChangeTitle: String
    val masterChangeDesc: String
    val masterCurrent: String
    val masterNew: String
    val masterRepeat: String
    val masterChange: String
    val masterChanged: String
    val masterSame: String
    val settingsSiteIcons: String
    val settingsSiteIconsOff: String
    val iconsRecommended: String
    val iconsUse: String
    val iconsProvider: String
    val iconsSiteDesc: String
    val iconsGoogleDesc: String
    val iconsDuckDesc: String
    val iconsBitwardenDesc: String
    val iconsOffDesc: String
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
    val iconsEnhance: String
    val iconsEnhanceDesc: String
    val listOpen: String
    val listCopyUsername: String
    val listCopyPassword: String
    val listCopyCode: String
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
    val settingsMadeBy: String
    val settingsSourceCode: String
    val settingsAboutTagline: String
    val settingsReportIssue: String
    val settingsSyncedNever: String
    val settingsSignOut: String
    val settingsSignOutNote: String
    val settingsSignOutTitle: String
    val settingsSignOutDesc: String
    val settingsOpenLabel: String
    val cancel: String
    val settingsChannelStable: String
    val quickCredential: String
    val quickCredentialDesc: String
    val settingsAutofillTotp: String
    val settingsAutofillTotpDesc: String
    val settingsAutofillStyle: String
    val settingsAutofillInline: String
    val settingsAutofillPopup: String
    val settingsPrivacyTitle: String
    val settingsAutoLockImmediate: String
    val settingsScreenshots: String
    val settingsScreenshotsDesc: String
    val settingsClipboard: String
    fun settingsClipboardAfter(time: String): String
    fun settingsSeconds(count: String): String
    val settingsAutofill: String
    val settingsAutofillDesc: String
    val anisetteInsecureWarning: String
    val anisetteDefaultDesc: String
    val anisetteCustom: String
    val anisetteChecking: String
    val anisetteUnreachable: String
    val anisetteInsecure: String
    fun anisetteLatency(ms: String): String
    fun anisetteRecommended(ms: String): String
    val resetForgot: String
    val resetTitle: String
    val resetIcloud: String
    val resetLocal: String
    val resetAction: String
    val quickBiometrics: String
    fun quickUnlockWith(method: String): String
    val quickPromptTitle: String
    val quickEnablePrompt: String
    val quickNote: String
    val quickGone: String
    val quickExpired: String
    val quickFailed: String
    val settingsBackupTitle: String
    val settingsBackupNote: String
    val backupExport: String
    val backupExportDesc: String
    val backupRestore: String
    val backupRestoreDesc: String
    val backupImportCsv: String
    val backupImportCsvDesc: String
    val backupPassword: String
    val backupPasswordConfirm: String
    val backupPasswordMismatch: String
    val backupPasswordShort: String
    val backupExportHint: String
    val backupRestoreHint: String
    val backupSaved: String
    val backupWrongPassword: String
    val backupBadFile: String
    fun backupResult(added: String, updated: String): String
    val backupNothing: String
    val backupFileType: String
    val transferExportTitle: String
    val transferImportTitle: String
    val transferExportDesc: String
    val transferImportDesc: String
    fun transferStep(step: String, total: String): String
    val transferFormatTitle: String
    val transferExportFormatIntro: String
    val transferImportFormatIntro: String
    val transferBackupExportDesc: String
    val transferBackupImportDesc: String
    val transferCsvExportDesc: String
    val transferZip: String
    val transferZipDesc: String
    val transferZipExportDesc: String
    val transferZipNote: String
    val transferZipHint: String
    val transferBackupNote: String
    val transferCsvNote: String
    val transferContinue: String
    val transferChooseFile: String
    val transferProtectTitle: String
    val transferCsvTitle: String
    val transferCsvWarning: String
    val transferCsvConfirm: String
    val transferCsvLeftOut: String
    val transferExportAction: String
    val transferSavedTitle: String
    val transferDone: String
    val transferCheckTitle: String
    val transferDetected: String
    val transferSourceUnknown: String
    val transferCountEntries: String
    val transferCountCodes: String
    val transferCountPasskeys: String
    val transferCountSkipped: String
    val transferImportAction: String
    val transferUnlockBackupTitle: String
    val transferUnlockArchiveTitle: String
    val transferArchiveHint: String
    val transferArchivePassword: String
    val transferArchiveWrongPassword: String
    val transferOpen: String
    val transferResultTitle: String
    val transferUpToDate: String
    val transferCountAdded: String
    val transferCountUpdated: String
    val authRestore: String
    fun settingsBasedOn(core: String): String
    val settingsAdvancedTitle: String
    val anisetteTitle: String
    val anisetteDesc: String
    val anisettePlaceholder: String
    val anisetteDefault: String
    val anisetteInvalid: String
    val anisetteNote: String
    val websitesTitle: String
    val websitesPlaceholder: String
    val websitesAdd: String
    val totpKeyPlaceholder: String
    val totpKeyConfirm: String
    val totpScanInvalid: String
    val totpScanFound: String
    fun totpScanFormat(digits: Int, period: Int): String
    fun totpChooseDesc(site: String): String
    val totpScanDetail: String
    val totpEnterKey: String
    val totpEnterKeyDetail: String
    fun totpScanOtherSite(issuer: String, site: String): String
    fun totpScanOtherAccount(account: String, username: String): String
    val totpScanOtherSiteTitle: String
    val totpScanOtherAccountTitle: String

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
    val devicesOnline: String
    val devicesSystem: String
    val devicesModel: String
    val devicesAddress: String
    val devicesVersion: String
    val devicesJoined: String
    val devicesLastSync: String
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
    fun websitesDescPasskey(title: String): String
    fun totpSetupDesc(site: String): String
    fun authMasterTooShort(count: Int): String
    fun fieldWebsiteMore(domain: String, count: Int): String
    fun fieldPasskeyCreationDate(date: String): String
    fun deleteTitle(count: Int): String
    fun deletePasskeyTitle(count: Int): String
    fun deletePasskeySoftDesc(count: Int): String
    fun deletePasskeyHardDesc(count: Int): String
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
