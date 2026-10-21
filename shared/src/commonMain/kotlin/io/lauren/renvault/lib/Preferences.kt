package io.lauren.renvault.lib

expect fun readPreference(key: String): String?

expect fun writePreference(key: String, value: String)
