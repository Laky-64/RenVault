package io.lauren.renvault.bridge

internal object Op {
    const val VAULT_INIT = 0x1001
    const val VAULT_STATE = 0x1002
    const val VAULT_UNLOCK = 0x1003
    const val VAULT_LOCK = 0x1004
    const val VAULT_PROFILE = 0x1005
    const val VAULT_SETTINGS = 0x1006
    const val VAULT_SET_SORT = 0x1007
    const val VAULT_SET_AUTO_LOCK = 0x1008
    const val VAULT_AUTO_LOCK_CHOICES = 0x1009
    const val VAULT_SIGN_OUT = 0x100A
    const val VAULT_SYNC = 0x100B
    const val VAULT_SYNC_STATE = 0x100C
    const val VAULT_CHECK_PWNED = 0x100D
    const val VAULT_PWNED_INFO = 0x100E
    const val VAULT_PROFILE_PHOTO = 0x100F
    const val VAULT_NAMES = 0x1010
    const val VAULT_SET_NAMES = 0x1011
    const val VAULT_LOCAL_SETUP = 0x1012
    const val VAULT_SET_PROFILE = 0x1013
    const val BACKUP_EXPORT = 0x1014
    const val BACKUP_IMPORT = 0x1015
    const val IMPORT_CSV = 0x1016
    const val QUICK_STATE = 0x1017
    const val QUICK_ENABLE = 0x1018
    const val QUICK_DISABLE = 0x1019
    const val QUICK_UNLOCK = 0x101A

    const val DEVICES = 0x1201
    const val DEVICE_REMOVE = 0x1202
    const val PAIR_START = 0x1203
    const val PAIR_WAIT = 0x1204
    const val PAIR_JOIN = 0x1205
    const val NET_INFO = 0x1206
    const val NET_ADDRESSES = 0x1207
    const val NET_PEER_SEEN = 0x1208
    const val PEERS_SYNC = 0x1209

    const val LIST_WEB = 0x1101
    const val LIST_WIFI = 0x1102
    const val LIST_PASSKEY = 0x1103
    const val GET_PASSWORD = 0x1104
    const val GET_TOTP = 0x1105
    const val ADD_PASSWORD = 0x1106
    const val EDIT_PASSWORD = 0x1107
    const val DELETE_PASSWORDS = 0x1108
    const val RESTORE_PASSWORD = 0x1109
    const val PURGE_PASSWORDS = 0x110A
    const val CHANGE_PASSWORD_AT = 0x110B
    const val DELETE_PASSKEYS = 0x110C
    const val RESTORE_PASSKEY = 0x110D
    const val PURGE_PASSKEYS = 0x110E
    const val EDIT_PASSKEY = 0x110F
    const val CREATE_PASSKEY = 0x1110
    const val SIGN_PASSKEY = 0x1111
    const val HYBRID_START = 0x1301
    const val HYBRID_DECIDE = 0x1302
    const val HYBRID_CANCEL = 0x1303
    const val LOCAL_KEY_STATUS = 0x1304
    const val LOCAL_KEY_START = 0x1305
    const val LOCAL_KEY_STOP = 0x1306
    const val LOCAL_KEY_GRANT = 0x1307
    const val NOTIFY_SHOW = 0x1308
    const val NOTIFY_CLOSE = 0x1309
    const val WIN_PASSKEY_STATUS = 0x130a
    const val WIN_PASSKEY_ENABLE = 0x130b
    const val WIN_PASSKEY_SYNC = 0x130c
    const val WIN_PASSKEY_SETTINGS = 0x130d
    const val WIN_PASSKEY_RESTART = 0x130e

    const val LOGIN_START = 0x2001
    const val LOGIN_CODE = 0x2002
    const val LOGIN_SUBMIT = 0x2003
    const val LOGIN_BOTTLES = 0x2004
    const val LOGIN_RECOVER = 0x2005
    const val LOGIN_FINISH = 0x2006
    const val ANISETTE_SERVER = 0x2007
    const val ANISETTE_SET_SERVER = 0x2008
    const val ANISETTE_SERVERS = 0x2009

    const val ICON_TILE = 0x3001
    const val ICON_IMAGE = 0x3002
    const val PICK_IMAGE = 0x3003
    const val PICK_FILE = 0x3004
    const val SAVE_FILE = 0x3005
    const val SECURE = 0x3006
    const val TITLE_BAR = 0x3007
    const val TRAY = 0x3008
    const val TRAY_HIDE = 0x3009
    const val RAISE = 0x300A
}

internal object FailureKind {
    const val UNKNOWN = 0
    const val BAD_OP = 1
    const val BAD_REQUEST = 2
    const val NOT_INITIALISED = 3
    const val LOCKED = 4
    const val NO_VAULT = 5
    const val NO_LOGIN = 6
    const val TWO_FACTOR_REQUIRED = 7
    const val NO_BOTTLES = 8
    const val BOTTLE_INDEX = 9
    const val NO_PEER = 10
    const val NO_PROFILE = 11
    const val CANCELLED = 12
}

object EventKind {
    const val SYNC_CHANGED = 0x0001
    const val LOCKED = 0x0002
    const val SIGN_REQUESTED = 0x0003
    const val AGENT_PROGRESS = 0x0004
    const val PROFILE = 0x0005
    const val DEVICES = 0x0006
    const val TRAY = 0x0007
}
