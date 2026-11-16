package io.lauren.renvault.lib

import io.lauren.renvault.bridge.Vault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class RvAppDetails(val name: String?, val icon: ByteArray?)

private val details = HashMap<String, RvAppDetails>()
private val detailsLock = Mutex()

suspend fun appDetails(packageName: String): RvAppDetails {
    detailsLock.withLock { details[packageName] }?.let { return it }
    val local = withContext(Dispatchers.Default) {
        RvAppDetails(appLabel(packageName), appIcon(packageName))
    }
    val remote = runCatching { Vault.appInfo(packageName, currentLocaleTag()) }.getOrNull()
    val resolved = RvAppDetails(
        name = local.name ?: remote?.name?.takeIf { it.isNotEmpty() },
        icon = remote?.icon?.takeIf { it.isNotEmpty() } ?: local.icon,
    )
    if (resolved.name != null || resolved.icon != null) detailsLock.withLock { details[packageName] = resolved }
    return resolved
}
