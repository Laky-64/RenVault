package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvSecureScreen
import io.lauren.renvault.lib.RvDraft
import io.lauren.renvault.lib.RvPasswordStrength
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.bareHost
import io.lauren.renvault.lib.domainOf
import io.lauren.renvault.lib.generatePassword
import io.lauren.renvault.lib.isDomain
import io.lauren.renvault.lib.strengthOf
import io.lauren.renvault.lib.titleOf
import kotlinx.coroutines.launch
import io.lauren.renvault.ui.theme.RvTone

@Composable
fun RvItemEditor(
    draft: RvDraft,
    onDraft: (RvDraft) -> Unit,
    hasTotp: Boolean,
    codeLoad: (suspend () -> String)?,
    strings: RvStrings,
    onChangePassword: (String) -> Unit,
    onDelete: () -> Unit,
    hidesName: Boolean = false,
) {
    var websitesOpen by remember { mutableStateOf(false) }
    var totpOpen by remember { mutableStateOf(false) }
    var dropOpen by remember { mutableStateOf(false) }

    val savedCode = hasTotp && !draft.totpRemoved
    val holdsCode = savedCode || draft.totpKey != null
    val liveCode = if (savedCode && draft.totpKey == null) codeLoad else null

    val websiteLabel = when {
        draft.websites.isEmpty() -> strings.editNoWebsite
        draft.websites.size == 1 -> draft.websites[0]
        else -> strings.fieldWebsiteMore(draft.websites[0], draft.websites.size - 1)
    }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (hidesName) {
            NoticeCard(
                icon = RvBadgeIcon,
                tone = RvTone(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer),
                title = strings.itemNameHiddenTitle,
                description = strings.editorNameHidden,
                action = null,
                onAction = {},
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            RvTextField(
                value = draft.title,
                onValueChange = { onDraft(draft.copy(title = it)) },
                label = draft.titleHint.ifEmpty { strings.editorTitle },
                capitalization = KeyboardCapitalization.Sentences,
            )
            RvTextField(
                value = draft.username,
                onValueChange = { onDraft(draft.copy(username = it)) },
                label = strings.fieldUsername,
                leading = RvPersonIcon,
                keyboard = KeyboardType.Email,
            )
            RvTextField(
                value = draft.password,
                onValueChange = { onDraft(draft.copy(password = it)) },
                label = strings.fieldPassword,
                leading = RvKeyIcon,
                secret = true,
            )
            if (draft.password.isNotEmpty()) {
                StrengthFor(draft.password, strings)
            }
            if (draft.websites.isNotEmpty()) {
                TextButton(
                    onClick = { onChangePassword(draft.websites[0]) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(RvOpenInNewIcon, contentDescription = null, modifier = Modifier.padding(end = ButtonDefaults.IconSpacing))
                    Text(strings.securityChangeAction)
                }
            }
        }

        RvSection {
            RvSegment(
                index = 0,
                count = 2,
                onClick = { websitesOpen = true },
                leading = { Icon(RvGlobeIcon, contentDescription = null) },
                supporting = { Text(websiteLabel, maxLines = 1) },
                trailing = { Icon(RvChevronRightIcon, contentDescription = null) },
            ) {
                Text(strings.fieldWebsite)
            }
            RvSegment(
                index = 1,
                count = 2,
                onClick = {
                    if (holdsCode) {
                        onDraft(draft.copy(totpKey = null, totpRemoved = hasTotp))
                    } else {
                        totpOpen = true
                    }
                },
                leading = { Icon(RvCodesIcon, contentDescription = null) },
                supporting = {
                    when {
                        liveCode != null -> RvTotpCode(load = liveCode, style = MaterialTheme.typography.bodyLarge)
                        holdsCode -> Text(strings.editSaveForCode)
                        else -> Text(strings.editNoCode)
                    }
                },
                trailing = {
                    Text(
                        text = if (holdsCode) strings.editDeleteCode else strings.editSetUpCode,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (holdsCode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                },
            ) {
                Text(strings.fieldVerificationCode)
            }
        }

        RvTextField(
            value = draft.note,
            onValueChange = { onDraft(draft.copy(note = it)) },
            label = strings.editorNotes,
            lines = 4,
            capitalization = KeyboardCapitalization.Sentences,
        )

        RvButton(
            text = strings.editDelete,
            onClick = { dropOpen = true },
            kind = RvButtonKind.DangerTonal,
            icon = RvDeleteIcon,
        )
    }

    RvWebsitesDialog(
        open = websitesOpen,
        onOpenChange = { websitesOpen = it },
        title = titleOf(draft),
        websites = draft.websites,
        onWebsites = { onDraft(draft.copy(websites = it)) },
        confirm = strings.editorSave,
        cancel = strings.cancel,
        heading = strings.websitesTitle,
        description = strings.websitesDesc(titleOf(draft)),
        placeholder = strings.websitesPlaceholder,
        addLabel = strings.websitesAdd,
    )

    RvTotpDialog(
        open = totpOpen,
        onOpenChange = { totpOpen = it },
        title = strings.editSetUpCode,
        description = strings.totpSetupDesc(draft.websites.firstOrNull() ?: titleOf(draft)),
        placeholder = strings.totpKeyPlaceholder,
        confirm = strings.totpKeyConfirm,
        cancel = strings.cancel,
        onKey = { onDraft(draft.copy(totpKey = it, totpRemoved = false)) },
    )

    RvConfirmDialog(
        open = dropOpen,
        onOpenChange = { dropOpen = it },
        title = strings.deleteTitle(1),
        description = strings.deleteSoftDesc(1),
        confirm = strings.deleteConfirm(1),
        cancel = strings.cancel,
        danger = true,
        icon = RvDeleteIcon,
        onConfirm = onDelete,
    )
}

@Composable
private fun StrengthFor(password: String, strings: RvStrings) {
    val level = strengthOf(password)
    RvStrengthMeter(
        strength = level,
        label = when (level) {
            RvPasswordStrength.Weak -> strings.strengthWeak
            RvPasswordStrength.Fair -> strings.strengthFair
            RvPasswordStrength.Strong -> strings.strengthStrong
            RvPasswordStrength.None -> null
        },
    )
}

@Composable
fun RvNewPasswordSheet(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    strings: RvStrings,
    favicon: (String) -> Painter?,
    onSave: (RvEditorDraft) -> Unit,
) {
    if (!open) return
    RvSecureScreen()

    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var draft by remember { mutableStateOf(RvEditorDraft(password = generatePassword())) }

    val domain = domainOf(draft.website)
    val typed = bareHost(draft.website)
    val websiteOk = if (draft.website.isNotBlank()) isDomain(typed) else draft.title.isNotBlank()
    val canSave = websiteOk && (draft.password.isNotEmpty() || draft.username.isNotBlank())

    val close: () -> Unit = {
        scope.launch { sheet.hide() }.invokeOnCompletion { onOpenChange(false) }
    }

    LaunchedEffect(Unit) { sheet.show() }

    ModalBottomSheet(
        onDismissRequest = { onOpenChange(false) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = close, shapes = IconButtonDefaults.shapes()) {
                    Icon(RvCloseIcon, contentDescription = strings.editorCancel)
                }
                Text(
                    text = strings.editorNewPassword,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Button(
                    onClick = {
                        onSave(draft.copy(website = domain))
                        close()
                    },
                    enabled = canSave,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(strings.editorSave)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                RvSiteIcon(
                    fallback = draft.title.ifEmpty { domain.ifEmpty { "?" } },
                    favicon = favicon(domain),
                    size = 72.dp,
                    shape = MaterialShapes.Cookie9Sided.toShape(),
                )
                Text(
                    text = draft.title.ifEmpty { domain.ifEmpty { strings.editorTitle } },
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            RvTextField(
                value = draft.title,
                onValueChange = { draft = draft.copy(title = it) },
                label = strings.editorTitle,
                capitalization = KeyboardCapitalization.Sentences,
            )
            RvTextField(
                value = draft.website,
                onValueChange = { draft = draft.copy(website = it) },
                label = strings.editorWebsiteHint,
                leading = RvGlobeIcon,
                keyboard = KeyboardType.Uri,
            )
            RvTextField(
                value = draft.username,
                onValueChange = { draft = draft.copy(username = it) },
                label = strings.editorUsernameHint,
                leading = RvPersonIcon,
                keyboard = KeyboardType.Email,
            )
            RvTextField(
                value = draft.password,
                onValueChange = { draft = draft.copy(password = it) },
                label = strings.fieldPassword,
                leading = RvKeyIcon,
                trailing = {
                    IconButton(
                        onClick = { draft = draft.copy(password = generatePassword()) },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(RvGenerateIcon, contentDescription = null)
                    }
                },
            )
            if (draft.password.isNotEmpty()) StrengthFor(draft.password, strings)
            RvTextField(
                value = draft.notes,
                onValueChange = { draft = draft.copy(notes = it) },
                label = strings.editorNotes,
                lines = 3,
                capitalization = KeyboardCapitalization.Sentences,
            )
        }
    }
}
