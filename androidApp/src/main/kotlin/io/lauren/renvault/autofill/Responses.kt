package io.lauren.renvault.autofill

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.FillResponse
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import io.lauren.renvault.R
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.bridge.WebEntry

private const val MaxDatasets = 8

internal const val ExtraUsername = "io.lauren.renvault.autofill.USERNAME"
internal const val ExtraPassword = "io.lauren.renvault.autofill.PASSWORD"
internal const val ExtraDomain = "io.lauren.renvault.autofill.DOMAIN"
internal const val ExtraPackage = "io.lauren.renvault.autofill.PACKAGE"
internal const val ExtraPick = "io.lauren.renvault.autofill.PICK"

internal fun presentation(context: Context, title: String, detail: String = ""): RemoteViews =
    RemoteViews(context.packageName, R.layout.autofill_item).apply {
        setTextViewText(R.id.autofill_title, title)
        setTextViewText(R.id.autofill_detail, detail)
        setViewVisibility(R.id.autofill_detail, if (detail.isEmpty()) View.GONE else View.VISIBLE)
    }

@RequiresApi(Build.VERSION_CODES.O)
internal fun authSender(context: Context, form: Form, pick: Boolean): IntentSender {
    val intent = Intent(context, AutofillActivity::class.java)
        .putExtra(ExtraUsername, form.username)
        .putExtra(ExtraPassword, form.password)
        .putExtra(ExtraDomain, form.webDomain)
        .putExtra(ExtraPackage, form.packageName)
        .putExtra(ExtraPick, pick)
    val flags = PendingIntent.FLAG_CANCEL_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getActivity(context, if (pick) 2 else 1, intent, flags).intentSender
}

@RequiresApi(Build.VERSION_CODES.O)
@Suppress("DEPRECATION")
internal suspend fun datasetOf(context: Context, form: Form, entry: WebEntry): Dataset {
    val title = entry.title.ifEmpty { entry.domain }
    val builder = Dataset.Builder(presentation(context, title, entry.username))
    form.username?.let { builder.setValue(it, AutofillValue.forText(entry.username)) }
    form.password?.let { builder.setValue(it, AutofillValue.forText(Vault.password(entry.id))) }
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
@Suppress("DEPRECATION")
internal suspend fun unlockedResponse(context: Context, form: Form, entries: List<WebEntry>): FillResponse {
    val response = FillResponse.Builder()
    entries.take(MaxDatasets).forEach { response.addDataset(datasetOf(context, form, it)) }
    val search = Dataset.Builder(presentation(context, context.getString(R.string.autofill_search)))
    form.ids.forEach { id: AutofillId -> search.setValue(id, null) }
    search.setAuthentication(authSender(context, form, pick = true))
    response.addDataset(search.build())
    return response.build()
}
