package io.lauren.renvault.lib

interface RvUpdater {
    val current: String
    val platform: String
    val directory: String
    suspend fun install(path: String): Boolean
}

expect fun platformUpdater(): RvUpdater?
