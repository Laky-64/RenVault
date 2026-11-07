package io.lauren.renvault.autofill

import io.lauren.renvault.RvAppId
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Build
import android.service.autofill.Dataset
import android.service.autofill.Field
import android.service.autofill.FillResponse
import android.service.autofill.SaveInfo
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import java.util.regex.Pattern
import android.graphics.drawable.Icon
import android.service.autofill.InlinePresentation
import android.service.autofill.Presentations
import android.view.inputmethod.InlineSuggestionsRequest
import androidx.autofill.inline.UiVersions
import androidx.autofill.inline.v1.InlineSuggestionUi
import io.lauren.renvault.MainActivity
import androidx.annotation.RequiresApi
import io.lauren.renvault.R
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.bridge.WebEntry
import io.lauren.renvault.lib.autofillCopyTotp

private const val MaxDatasets = 8

internal const val ExtraUsername = "$RvAppId.autofill.USERNAME"
internal const val ExtraPassword = "$RvAppId.autofill.PASSWORD"
internal const val ExtraDomain = "$RvAppId.autofill.DOMAIN"
internal const val ExtraPackage = "$RvAppId.autofill.PACKAGE"
internal const val ExtraPick = "$RvAppId.autofill.PICK"
internal const val ExtraInline = "$RvAppId.autofill.INLINE"
internal const val ExtraEntry = "$RvAppId.autofill.ENTRY"
internal const val ExtraFresh = "$RvAppId.autofill.FRESH"
internal const val ExtraRules = "$RvAppId.autofill.RULES"

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
        .putExtra(ExtraInline, form.inline)
        .putParcelableArrayListExtra(ExtraFresh, ArrayList(form.fresh))
        .putExtra(ExtraRules, form.rules)
    val flags = PendingIntent.FLAG_CANCEL_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getActivity(context, if (pick) 2 else 1, intent, flags).intentSender
}

@RequiresApi(Build.VERSION_CODES.O)
private fun totpSender(context: Context, form: Form, entry: String): IntentSender {
    val intent = Intent(context, AutofillTotpActivity::class.java)
        .putExtra(ExtraUsername, form.username)
        .putExtra(ExtraPassword, form.password)
        .putExtra(ExtraDomain, form.webDomain)
        .putExtra(ExtraPackage, form.packageName)
        .putExtra(ExtraEntry, entry)
    val flags = PendingIntent.FLAG_CANCEL_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getActivity(context, 1000 + (entry.hashCode() and 0xFFFF), intent, flags).intentSender
}

@RequiresApi(Build.VERSION_CODES.O)
private fun generateSender(context: Context, form: Form): IntentSender {
    val intent = Intent(context, AutofillGenerateActivity::class.java)
        .putParcelableArrayListExtra(ExtraFresh, ArrayList(form.fresh))
        .putExtra(ExtraDomain, form.webDomain)
        .putExtra(ExtraPackage, form.packageName)
        .putExtra(ExtraRules, form.rules)
    val flags = PendingIntent.FLAG_CANCEL_CURRENT or
        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getActivity(context, 4, intent, flags).intentSender
}

@RequiresApi(Build.VERSION_CODES.O)
private fun generateDataset(context: Context, form: Form, slot: Int): Dataset {
    val title = context.getString(R.string.autofill_generate)
    val detail = context.getString(R.string.autofill_generate_detail)
    val views = presentation(context, title, detail)
    val chip = inlineOf(context, form, slot, title, detail)
    val builder = datasetBuilder(views, chip)
    form.fresh.forEach { id -> builder.setField(id, null, views, chip) }
    builder.setAuthentication(generateSender(context, form))
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun generatedDataset(context: Context, form: Form, password: String): Dataset {
    val title = context.getString(R.string.autofill_generate)
    val views = presentation(context, title)
    val builder = datasetBuilder(views, null)
    form.fresh.forEach { id ->
        builder.setField(id, AutofillValue.forText(password), views, null, filtered = false)
    }
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
internal suspend fun datasetOf(context: Context, form: Form, entry: WebEntry, slot: Int = 0, deferTotp: Boolean = true): Dataset {
    val title = entry.title.ifEmpty { entry.domain }
    val views = presentation(context, title, entry.username)
    val chip = inlineOf(context, form, slot, title, entry.username)
    val builder = datasetBuilder(views, chip)
    if (deferTotp && entry.hasTotp && autofillCopyTotp()) {
        form.ids.forEach { id -> builder.setField(id, null, views, chip) }
        builder.setAuthentication(totpSender(context, form, entry.id))
        return builder.build()
    }
    form.username?.let { builder.setField(it, AutofillValue.forText(entry.username), views, chip) }
    form.password?.let { builder.setField(it, AutofillValue.forText(Vault.password(entry.id)), views, chip, filtered = false) }
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
internal fun presentationsOf(views: RemoteViews, chip: Any?): Presentations =
    Presentations.Builder()
        .setMenuPresentation(views)
        .apply { if (chip != null) setInlinePresentation(chip as InlinePresentation) }
        .build()

@RequiresApi(Build.VERSION_CODES.O)
private fun datasetBuilder(views: RemoteViews, chip: Any?): Dataset.Builder =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Dataset.Builder(presentationsOf(views, chip))
    } else {
        legacyDatasetBuilder(views)
    }

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
private fun legacyDatasetBuilder(views: RemoteViews): Dataset.Builder = Dataset.Builder(views)

@RequiresApi(Build.VERSION_CODES.O)
private fun Dataset.Builder.setField(
    id: AutofillId,
    value: AutofillValue?,
    views: RemoteViews,
    chip: Any?,
    filtered: Boolean = true,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        setField(
            id,
            Field.Builder()
                .apply {
                    if (value != null) setValue(value)
                    if (!filtered) setFilter(null)
                }
                .build(),
        )
    } else {
        setLegacyField(id, value, views, chip, filtered)
    }
}

@Suppress("DEPRECATION")
@RequiresApi(Build.VERSION_CODES.O)
private fun Dataset.Builder.setLegacyField(
    id: AutofillId,
    value: AutofillValue?,
    views: RemoteViews,
    chip: Any?,
    filtered: Boolean,
) {
    val inline = chip != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    when {
        filtered && inline -> setValue(id, value, views, chip as InlinePresentation)
        filtered -> setValue(id, value)
        inline -> setValue(id, value, null as Pattern?, views, chip as InlinePresentation)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> setValue(id, value, null as Pattern?, views)
        else -> setValue(id, value)
    }
}

internal fun inlineOf(context: Context, form: Form, slot: Int, title: String, detail: String = ""): Any? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val request = form.inline as? InlineSuggestionsRequest ?: return null
    if (slot >= request.maxSuggestionCount) return null
    val spec = request.inlinePresentationSpecs.getOrNull(slot)
        ?: request.inlinePresentationSpecs.lastOrNull()
        ?: return null
    if (!UiVersions.getVersions(spec.style).contains(UiVersions.INLINE_UI_VERSION_1)) return null
    val attribution = PendingIntent.getActivity(
        context,
        100 + slot,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE,
    )
    val content = InlineSuggestionUi.newContentBuilder(attribution)
        .setTitle(title)
        .setStartIcon(Icon.createWithResource(context, R.drawable.autofill_inline_icon))
        .apply { if (detail.isNotEmpty()) setSubtitle(detail) }
        .build()
    return InlinePresentation(content.slice, spec, false)
}

@RequiresApi(Build.VERSION_CODES.O)
internal suspend fun unlockedResponse(context: Context, form: Form, entries: List<WebEntry>): FillResponse {
    val response = FillResponse.Builder()
    val offer = form.signup
    if (offer) response.addDataset(generateDataset(context, form, 0))
    val first = if (offer) 1 else 0
    val shown = entries.take(MaxDatasets - first)
    shown.forEachIndexed { index, entry -> response.addDataset(datasetOf(context, form, entry, first + index)) }
    val searchTitle = context.getString(R.string.autofill_search)
    val views = presentation(context, searchTitle)
    val chip = inlineOf(context, form, first + shown.size, searchTitle)
    val search = datasetBuilder(views, chip)
    form.ids.forEach { id -> search.setField(id, null, views, chip) }
    search.setAuthentication(authSender(context, form, pick = true))
    response.addDataset(search.build())
    return response.withSave(form).build()
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun FillResponse.Builder.withSave(form: Form): FillResponse.Builder {
    val password = form.password
    if (password == null) {
        val fields = (listOfNotNull(form.username) + form.texts).distinct()
        if (fields.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return this
        return setSaveInfo(
            SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_USERNAME)
                .setOptionalIds(fields.toTypedArray())
                .setFlags(SaveInfo.FLAG_DELAY_SAVE)
                .build(),
        )
    }
    val info = SaveInfo.Builder(SaveInfo.SAVE_DATA_TYPE_PASSWORD or SaveInfo.SAVE_DATA_TYPE_USERNAME, arrayOf(password))
        .apply { form.username?.let { setOptionalIds(arrayOf(it)) } }
        .setFlags(SaveInfo.FLAG_SAVE_ON_ALL_VIEWS_INVISIBLE)
        .build()
    return setSaveInfo(info)
}
