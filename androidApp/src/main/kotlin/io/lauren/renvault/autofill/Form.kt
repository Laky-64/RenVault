package io.lauren.renvault.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.os.Parcelable
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi
import io.lauren.renvault.bridge.WebEntry
import io.lauren.renvault.lib.appLink
import io.lauren.renvault.lib.isAppLink

@RequiresApi(Build.VERSION_CODES.O)
internal class Form(
    val username: AutofillId?,
    val password: AutofillId?,
    val webDomain: String?,
    val packageName: String,
    val inline: Parcelable? = null,
    val texts: List<AutofillId> = emptyList(),
    val focused: AutofillId? = null,
) {
    val ids: List<AutofillId> get() = listOfNotNull(username, password)
}

private val UserWords = listOf("user", "email", "e-mail", "login", "account", "utente", "mail")

@RequiresApi(Build.VERSION_CODES.O)
internal fun parseForm(structure: AssistStructure): Form {
    var username: AutofillId? = null
    var password: AutofillId? = null
    var lastText: AutofillId? = null
    var domain: String? = null
    var focused: AutofillId? = null
    val texts = mutableListOf<AutofillId>()

    fun visit(node: AssistStructure.ViewNode) {
        if (domain == null) node.webDomain?.takeIf { it.isNotBlank() }?.let { domain = it.lowercase() }
        val id = node.autofillId
        if (id != null && node.autofillType == View.AUTOFILL_TYPE_TEXT) {
            val kind = kindOf(node)
            if (kind == FieldKind.Username || kind == FieldKind.Text) {
                texts += id
                if (node.isFocused) focused = id
            }
            when (kind) {
                FieldKind.Password -> if (password == null) {
                    password = id
                    if (username == null) username = lastText
                }
                FieldKind.Username -> if (username == null) username = id
                FieldKind.Text -> lastText = id
                FieldKind.None -> Unit
            }
        }
        for (i in 0 until node.childCount) visit(node.getChildAt(i))
    }

    for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
    return Form(username, password, domain, structure.activityComponent.packageName, texts = texts, focused = focused)
}

private enum class FieldKind { Username, Password, Text, None }

@RequiresApi(Build.VERSION_CODES.O)
private fun kindOf(node: AssistStructure.ViewNode): FieldKind {
    val hints = node.autofillHints.orEmpty().map { it.lowercase() }
    if (hints.any { "password" in it }) return FieldKind.Password
    if (hints.any { it == View.AUTOFILL_HINT_USERNAME.lowercase() || "email" in it }) return FieldKind.Username

    val html = node.htmlInfo
    if (html != null && html.tag.equals("input", ignoreCase = true)) {
        val attrs = html.attributes.orEmpty().associate { it.first.lowercase() to it.second.orEmpty().lowercase() }
        when (attrs["type"]) {
            "password" -> return FieldKind.Password
            "email" -> return FieldKind.Username
            "hidden", "submit", "button", "checkbox", "radio" -> return FieldKind.None
        }
        val label = listOfNotNull(attrs["name"], attrs["id"], attrs["autocomplete"]).joinToString(" ")
        if ("password" in label) return FieldKind.Password
        if (UserWords.any { it in label }) return FieldKind.Username
        return FieldKind.Text
    }

    val type = node.inputType
    if (type and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT) {
        when (type and InputType.TYPE_MASK_VARIATION) {
            InputType.TYPE_TEXT_VARIATION_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> return FieldKind.Password
            InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS -> return FieldKind.Username
        }
    }
    val label = listOfNotNull(node.idEntry, node.hint).joinToString(" ").lowercase()
    if ("password" in label) return FieldKind.Password
    if (UserWords.any { it in label }) return FieldKind.Username
    return if (node.isFocusable && node.className?.contains("EditText") == true) FieldKind.Text else FieldKind.None
}

private val GenericParts = setOf("com", "org", "net", "io", "app", "android", "mobile", "client", "www")

@RequiresApi(Build.VERSION_CODES.O)
internal fun matchEntries(entries: List<WebEntry>, form: Form): List<WebEntry> {
    val live = entries.filter { !it.isDeleted && it.website }
    val domain = form.webDomain?.removePrefix("www.")
    if (domain != null) {
        return live.filter { entry -> (entry.domains + entry.domain).any { sameSite(it, domain) } }
    }
    val link = appLink(form.packageName)
    val linked = entries.filter { !it.isDeleted && link in it.domains }
    val parts = form.packageName.lowercase().split('.').filter { it.length > 2 && it !in GenericParts }.toSet()
    val guessed = live.filter { entry ->
        (entry.domains + entry.domain).filterNot(::isAppLink).any { siteLabel(it) in parts }
    }
    return (linked + guessed).distinctBy { it.id }
}

private fun sameSite(a: String, b: String): Boolean {
    val x = a.lowercase().removePrefix("www.")
    val y = b.lowercase().removePrefix("www.")
    return x == y || x.endsWith(".$y") || y.endsWith(".$x")
}

private fun siteLabel(domain: String): String {
    val labels = domain.lowercase().removePrefix("www.").split('.')
    return if (labels.size >= 2) labels[labels.size - 2] else labels.first()
}
