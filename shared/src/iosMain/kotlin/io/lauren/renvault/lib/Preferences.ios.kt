package io.lauren.renvault.lib

import platform.Foundation.NSUserDefaults

actual fun readPreference(key: String): String? =
    NSUserDefaults.standardUserDefaults.stringForKey("renvault.$key")

actual fun writePreference(key: String, value: String) {
    NSUserDefaults.standardUserDefaults.setObject(value, forKey = "renvault.$key")
}
