package bridge

const (
	OpPing     uint32 = 0x0001
	OpShutdown uint32 = 0x0002
)

const (
	OpVaultInit            uint32 = 0x1001
	OpVaultState           uint32 = 0x1002
	OpVaultUnlock          uint32 = 0x1003
	OpVaultLock            uint32 = 0x1004
	OpVaultProfile         uint32 = 0x1005
	OpVaultSettings        uint32 = 0x1006
	OpVaultSetSort         uint32 = 0x1007
	OpVaultSetAutoLock     uint32 = 0x1008
	OpVaultAutoLockChoices uint32 = 0x1009
	OpVaultSignOut         uint32 = 0x100A
	OpVaultSync            uint32 = 0x100B
	OpVaultSyncState       uint32 = 0x100C
	OpVaultCheckPwned      uint32 = 0x100D
	OpVaultPwnedInfo       uint32 = 0x100E
	OpVaultProfilePhoto    uint32 = 0x100F
	OpVaultNames           uint32 = 0x1010
	OpVaultSetNames        uint32 = 0x1011
	OpVaultLocalSetup      uint32 = 0x1012
	OpVaultSetProfile      uint32 = 0x1013
	OpBackupExport         uint32 = 0x1014
	OpBackupImport         uint32 = 0x1015
	OpImportCSV            uint32 = 0x1016
)

const (
	OpDevices      uint32 = 0x1201
	OpDeviceRemove uint32 = 0x1202
	OpPairStart    uint32 = 0x1203
	OpPairWait     uint32 = 0x1204
	OpPairJoin     uint32 = 0x1205
	OpNetInfo      uint32 = 0x1206
	OpNetAddresses uint32 = 0x1207
	OpNetPeerSeen  uint32 = 0x1208
	OpPeersSync    uint32 = 0x1209
)

const (
	OpListWeb          uint32 = 0x1101
	OpListWiFi         uint32 = 0x1102
	OpListPasskey      uint32 = 0x1103
	OpGetPassword      uint32 = 0x1104
	OpGetTOTP          uint32 = 0x1105
	OpAddPassword      uint32 = 0x1106
	OpEditPassword     uint32 = 0x1107
	OpDeletePasswords  uint32 = 0x1108
	OpRestorePassword  uint32 = 0x1109
	OpPurgePasswords   uint32 = 0x110A
	OpChangePasswordAt uint32 = 0x110B
)

const (
	OpLoginStart   uint32 = 0x2001
	OpLoginCode    uint32 = 0x2002
	OpLoginSubmit  uint32 = 0x2003
	OpLoginBottles uint32 = 0x2004
	OpLoginRecover uint32 = 0x2005
	OpLoginFinish  uint32 = 0x2006

	OpAnisetteServer    uint32 = 0x2007
	OpAnisetteSetServer uint32 = 0x2008
)

const (
	OpIconTile  uint32 = 0x3001
	OpIconImage uint32 = 0x3002
	OpPickImage uint32 = 0x3003
	OpPickFile  uint32 = 0x3004
	OpSaveFile  uint32 = 0x3005
)
