package io.lauren.renvault.bridge

internal object Op {
    const val PING = 0x0001
    const val SHUTDOWN = 0x0002

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

    const val LOGIN_START = 0x2001
    const val LOGIN_CODE = 0x2002
    const val LOGIN_SUBMIT = 0x2003
    const val LOGIN_BOTTLES = 0x2004
    const val LOGIN_RECOVER = 0x2005
    const val LOGIN_FINISH = 0x2006
    const val ANISETTE_SERVER = 0x2007
    const val ANISETTE_SET_SERVER = 0x2008

    const val ICON_TILE = 0x3001
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
}
