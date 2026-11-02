package io.lauren.renvault.autofill

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

private const val AssetStatements = "asset_statements"
private const val LinkTimeoutMs = 4000
private const val VerifyTimeoutMs = 6000L
private const val MaxLinkBytes = 256 * 1024
private val LoginRelations = setOf(
    "delegate_permission/common.get_login_creds",
    "delegate_permission/common.handle_all_urls",
)

internal suspend fun verifiedSite(context: Context, packageName: String): String? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(VerifyTimeoutMs) {
        val fingerprints = signingFingerprints(context.packageManager, packageName)
        if (fingerprints.isEmpty()) return@withTimeoutOrNull null
        declaredSites(context.packageManager, packageName).firstOrNull { site ->
            confirms(site, packageName, fingerprints)
        }
    }
}

private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotEmpty) }

private fun declaredSites(manager: PackageManager, packageName: String): List<String> {
    val statements = runCatching {
        val info = manager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
        val res = info.metaData?.getInt(AssetStatements)?.takeIf { it != 0 } ?: return emptyList()
        JSONArray(manager.getResourcesForApplication(info).getString(res))
    }.getOrNull() ?: return emptyList()
    val sites = mutableListOf<String>()
    for (statement in statements.objects()) {
        val include = statement.optString("include")
        val nested = if (include.isNotEmpty()) fetchStatements(include).objects() else listOf(statement)
        for (item in nested) {
            val target = item.optJSONObject("target") ?: continue
            if (target.optString("namespace") != "web") continue
            val host = hostOfSite(target.optString("site")) ?: continue
            if (host !in sites) sites += host
        }
    }
    return sites
}

private fun confirms(site: String, packageName: String, fingerprints: Set<String>): Boolean =
    fetchStatements("https://$site/.well-known/assetlinks.json").objects().any { statement ->
        val target = statement.optJSONObject("target") ?: return@any false
        val relations = statement.optJSONArray("relation")?.strings().orEmpty()
        val prints = target.optJSONArray("sha256_cert_fingerprints")?.strings().orEmpty().map { it.uppercase() }
        relations.any { it in LoginRelations } &&
            target.optString("namespace") == "android_app" &&
            target.optString("package_name") == packageName &&
            prints.any { it in fingerprints }
    }

private fun hostOfSite(site: String): String? {
    val uri = runCatching { URI(site) }.getOrNull() ?: return null
    if (uri.scheme != "https") return null
    return uri.host?.lowercase()?.takeIf { it.contains('.') }
}

private fun InputStream.readLimited(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
    }
    return out.toByteArray()
}

private fun fetchStatements(address: String): JSONArray {
    val uri = runCatching { URI(address) }.getOrNull()
    if (uri?.scheme != "https") return JSONArray()
    return runCatching {
        val connection = uri.toURL().openConnection() as HttpURLConnection
        connection.connectTimeout = LinkTimeoutMs
        connection.readTimeout = LinkTimeoutMs
        connection.instanceFollowRedirects = false
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                JSONArray()
            } else {
                JSONArray(connection.inputStream.use { it.readLimited(MaxLinkBytes) }.decodeToString())
            }
        } finally {
            connection.disconnect()
        }
    }.getOrDefault(JSONArray())
}

@Suppress("DEPRECATION")
private fun signingFingerprints(manager: PackageManager, packageName: String): Set<String> = runCatching {
    val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        val info = manager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
            ?: return emptySet()
        if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
    } else {
        manager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
    }
    signatures.orEmpty().map { signature ->
        MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString(":") { "%02X".format(it) }
    }.toSet()
}.getOrDefault(emptySet())
