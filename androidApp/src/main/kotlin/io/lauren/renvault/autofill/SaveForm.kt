package io.lauren.renvault.autofill

import android.app.assist.AssistStructure
import android.os.Build
import android.service.autofill.FillContext
import android.view.View
import android.view.autofill.AutofillId
import androidx.annotation.RequiresApi

internal class SavedLogin(
    val username: String,
    val password: String,
    val webDomain: String?,
    val packageName: String,
)

@RequiresApi(Build.VERSION_CODES.O)
internal fun parseSaved(contexts: List<FillContext>): SavedLogin? {
    var username = ""
    var rank = -1
    var password = ""
    var domain: String? = null
    var packageName = ""
    for (context in contexts) {
        val structure = context.structure
        val form = parseForm(structure)
        packageName = form.packageName
        if (domain == null) domain = form.webDomain
        val values = HashMap<AutofillId, String>()
        fun visit(node: AssistStructure.ViewNode) {
            val id = node.autofillId
            val text = node.autofillValue?.takeIf { it.isText }?.textValue?.toString()?.trim().orEmpty()
            if (id != null && text.isNotEmpty() && node.autofillType == View.AUTOFILL_TYPE_TEXT) values[id] = text
            for (i in 0 until node.childCount) visit(node.getChildAt(i))
        }
        for (i in 0 until structure.windowNodeCount) visit(structure.getWindowNodeAt(i).rootViewNode)
        form.password?.let { id -> values[id]?.let { password = it } }
        val candidates = listOfNotNull(
            form.username?.let { values[it] }?.let { it to 3 },
            form.texts.firstNotNullOfOrNull { id -> values[id]?.takeIf { '@' in it } }?.let { it to 2 },
            form.focused?.let { values[it] }?.let { it to 1 },
            form.texts.firstNotNullOfOrNull { values[it] }?.let { it to 0 },
        )
        candidates.firstOrNull()?.let { (value, score) ->
            if (score >= rank) {
                username = value
                rank = score
            }
        }
    }
    if (password.isEmpty() || packageName.isEmpty()) return null
    return SavedLogin(username, password, domain, packageName)
}
