package io.lauren.renvault.passkey

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

private const val Base64Url = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
private const val AlgES256 = -7
private val P256Prefix = byteArrayOf(
    0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x02, 0x01,
    0x06, 0x08, 0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
)

internal fun ByteArray.b64url(): String = Base64.encodeToString(this, Base64Url)

internal fun String.fromB64url(): ByteArray = Base64.decode(this, Base64Url)

internal fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal class CreationRequest(
    val rpId: String,
    val userHandle: ByteArray,
    val userName: String,
    val displayName: String,
    val challenge: String,
    val excluded: List<ByteArray>,
    val supported: Boolean,
)

internal class AssertionRequest(
    val rpId: String,
    val challenge: String,
    val allowed: List<ByteArray>,
)

internal fun parseCreation(json: String): CreationRequest {
    val root = JSONObject(json)
    val rp = root.getJSONObject("rp")
    val user = root.getJSONObject("user")
    val params = root.optJSONArray("pubKeyCredParams") ?: JSONArray()
    val supported = (0 until params.length()).any { params.getJSONObject(it).optInt("alg") == AlgES256 }
    return CreationRequest(
        rpId = rp.optString("id").lowercase(),
        userHandle = user.getString("id").fromB64url(),
        userName = user.optString("name"),
        displayName = user.optString("displayName"),
        challenge = root.getString("challenge"),
        excluded = credentialIds(root.optJSONArray("excludeCredentials")),
        supported = supported,
    )
}

internal fun parseAssertion(json: String): AssertionRequest {
    val root = JSONObject(json)
    return AssertionRequest(
        rpId = root.optString("rpId").lowercase(),
        challenge = root.getString("challenge"),
        allowed = credentialIds(root.optJSONArray("allowCredentials")),
    )
}

private fun credentialIds(list: JSONArray?): List<ByteArray> {
    if (list == null) return emptyList()
    return (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("id")?.takeIf(String::isNotEmpty)?.fromB64url() }
}

internal fun clientDataJson(type: String, challenge: String, origin: String, packageName: String?): ByteArray {
    val data = JSONObject()
        .put("type", type)
        .put("challenge", challenge)
        .put("origin", origin)
        .put("crossOrigin", false)
    if (packageName != null) data.put("androidPackageName", packageName)
    return data.toString().toByteArray()
}

internal fun attestationObject(authData: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(0xa3)
    cborText(out, "fmt")
    cborText(out, "none")
    cborText(out, "attStmt")
    out.write(0xa0)
    cborText(out, "authData")
    cborHead(out, 2, authData.size)
    out.write(authData)
    return out.toByteArray()
}

private fun cborText(out: ByteArrayOutputStream, text: String) {
    val bytes = text.toByteArray()
    cborHead(out, 3, bytes.size)
    out.write(bytes)
}

private fun cborHead(out: ByteArrayOutputStream, major: Int, size: Int) {
    val type = major shl 5
    when {
        size < 24 -> out.write(type or size)
        size < 0x100 -> {
            out.write(type or 24)
            out.write(size)
        }
        else -> {
            out.write(type or 25)
            out.write(size shr 8)
            out.write(size and 0xff)
        }
    }
}

internal fun publicKeyOf(authData: ByteArray): ByteArray? {
    if (authData.size < 55) return null
    val idLength = ((authData[53].toInt() and 0xff) shl 8) or (authData[54].toInt() and 0xff)
    val cose = authData.copyOfRange(55 + idLength, authData.size)
    val x = coordinate(cose, 0x21) ?: return null
    val y = coordinate(cose, 0x22) ?: return null
    return P256Prefix + byteArrayOf(0x04) + x + y
}

private fun coordinate(cose: ByteArray, label: Int): ByteArray? {
    for (i in 0 until cose.size - 34) {
        if (cose[i].toInt() and 0xff == label && cose[i + 1].toInt() and 0xff == 0x58 && cose[i + 2].toInt() == 32) {
            return cose.copyOfRange(i + 3, i + 35)
        }
    }
    return null
}

internal fun registrationJson(credentialId: ByteArray, authData: ByteArray, clientData: ByteArray): String {
    val response = JSONObject()
        .put("clientDataJSON", clientData.b64url())
        .put("attestationObject", attestationObject(authData).b64url())
        .put("authenticatorData", authData.b64url())
        .put("transports", JSONArray(listOf("internal", "hybrid")))
        .put("publicKeyAlgorithm", AlgES256)
    publicKeyOf(authData)?.let { response.put("publicKey", it.b64url()) }
    return JSONObject()
        .put("id", credentialId.b64url())
        .put("rawId", credentialId.b64url())
        .put("type", "public-key")
        .put("authenticatorAttachment", "platform")
        .put("response", response)
        .put("clientExtensionResults", JSONObject().put("credProps", JSONObject().put("rk", true)))
        .toString()
}

internal fun assertionJson(
    credentialId: ByteArray,
    authData: ByteArray,
    signature: ByteArray,
    userHandle: ByteArray,
    clientData: ByteArray,
): String {
    val response = JSONObject()
        .put("clientDataJSON", clientData.b64url())
        .put("authenticatorData", authData.b64url())
        .put("signature", signature.b64url())
        .put("userHandle", userHandle.b64url())
    return JSONObject()
        .put("id", credentialId.b64url())
        .put("rawId", credentialId.b64url())
        .put("type", "public-key")
        .put("authenticatorAttachment", "platform")
        .put("response", response)
        .put("clientExtensionResults", JSONObject())
        .toString()
}
