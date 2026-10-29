package io.lauren.renvault.passkey

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.CallingAppInfo
import io.lauren.renvault.R
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal class Caller(val origin: String, val packageName: String?)

private const val LoginRelation = "delegate_permission/common.get_login_creds"
private const val UrlsRelation = "delegate_permission/common.handle_all_urls"

@RequiresApi(Build.VERSION_CODES.P)
internal suspend fun callerOf(context: Context, info: CallingAppInfo, rpId: String): Caller? {
    val allowlist = context.resources.openRawResource(R.raw.privileged_browsers).bufferedReader().use { it.readText() }
    val browserOrigin = runCatching { info.getOrigin(allowlist) }.getOrNull()
    if (browserOrigin != null) {
        val host = runCatching { URL(browserOrigin).host.lowercase() }.getOrNull() ?: return null
        return if (host == rpId || host.endsWith(".$rpId")) Caller(browserOrigin.trimEnd('/'), null) else null
    }
    val certificate = info.signingInfoCompat.apkContentsSigners.firstOrNull()?.toByteArray() ?: return null
    val digest = sha256(certificate)
    if (!linked(rpId, info.packageName, digest)) return null
    return Caller("android:apk-key-hash:${digest.b64url()}", info.packageName)
}

private suspend fun linked(rpId: String, packageName: String, digest: ByteArray): Boolean = withContext(Dispatchers.IO) {
    val fingerprint = digest.joinToString(":") { "%02X".format(it) }
    runCatching {
        val connection = URL("https://$rpId/.well-known/assetlinks.json").openConnection() as HttpURLConnection
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        connection.instanceFollowRedirects = false
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val statements = JSONArray(body)
        (0 until statements.length()).any { i ->
            val statement = statements.getJSONObject(i)
            val relations = statement.optJSONArray("relation") ?: JSONArray()
            val related = (0 until relations.length()).any { relations.optString(it) in setOf(LoginRelation, UrlsRelation) }
            val target = statement.optJSONObject("target") ?: return@any false
            val prints = target.optJSONArray("sha256_cert_fingerprints") ?: JSONArray()
            related && target.optString("namespace") == "android_app" &&
                target.optString("package_name") == packageName &&
                (0 until prints.length()).any { prints.optString(it).equals(fingerprint, ignoreCase = true) }
        }
    }.getOrDefault(false)
}
