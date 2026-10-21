package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.DeletedDays
import io.lauren.renvault.lib.RvDetailField
import io.lauren.renvault.lib.RvDraft
import io.lauren.renvault.lib.RvItem
import io.lauren.renvault.lib.RvItemIcon
import io.lauren.renvault.lib.RvPasskeyItem
import io.lauren.renvault.lib.RvSecretSource
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.RvWebItem
import io.lauren.renvault.lib.RvWifiItem
import io.lauren.renvault.lib.RvZoneKind
import io.lauren.renvault.lib.code
import io.lauren.renvault.lib.daysLeft
import io.lauren.renvault.lib.detailOf
import io.lauren.renvault.lib.labelOf
import io.lauren.renvault.lib.plain
import io.lauren.renvault.lib.secret
import io.lauren.renvault.lib.siteOf
import io.lauren.renvault.lib.titleOf
import io.lauren.renvault.lib.viewOf
import io.lauren.renvault.ui.theme.RvTone
import kotlinx.coroutines.launch

@Composable
fun RvDetailPane(
    zone: RvHomeZone,
    item: RvItem?,
    secrets: RvSecretSource,
    strings: RvStrings,
    favicon: (String) -> Painter?,
    compact: Boolean,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    canEdit: Boolean,
    draft: RvDraft,
    onDraft: (RvDraft) -> Unit,
    canSave: Boolean,
    saving: Boolean,
    onSave: () -> Unit,
    onBack: () -> Unit,
    onCopy: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onLearnMore: () -> Unit,
    onDelete: () -> Unit,
    onRecover: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (item == null) {
        Placeholder(zone = zone, strings = strings, modifier = modifier)
        return
    }

    val view = remember(item, strings) { viewOf(item, strings) }
    val detail = remember(item, secrets, strings) { detailOf(item, secrets, strings) }
    val glyph = if (view.icon is RvItemIcon.WifiGlyph) RvWifiIcon else null
    val icon = (view.icon as? RvItemIcon.Favicon)?.let { favicon(it.domain) }
    val scroll = rememberScrollState()

    LaunchedEffect(item.id) { scroll.scrollTo(0) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    when {
                        editing -> IconButton(onClick = { onEditing(false) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(RvCloseIcon, contentDescription = strings.editorCancel)
                        }
                        compact -> IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                            Icon(RvBackIcon, contentDescription = strings.navBack)
                        }
                    }
                },
                actions = {
                    if (editing) {
                        Button(
                            onClick = onSave,
                            enabled = canSave,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            if (saving) {
                                LoadingIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                            } else {
                                Text(strings.editorSave)
                            }
                        }
                    } else if (canEdit) {
                        IconButton(onClick = { onEditing(true) }, shapes = IconButtonDefaults.shapes()) {
                            Icon(RvEditIcon, contentDescription = strings.itemEdit)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding())
                .verticalScroll(scroll)
                .imePadding(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = RvLayout.readable)
                    .fillMaxWidth()
                    .padding(horizontal = RvLayout.gutter)
                    .padding(bottom = 32.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Header(
                    title = if (editing) titleOf(draft).ifEmpty { "?" } else detail.title,
                    subtitle = if (editing) null else (item as? RvWebItem)?.let(::siteOf)?.takeIf { it.isNotEmpty() },
                    glyph = glyph,
                    favicon = icon,
                    fallback = view.title,
                )

                val motion = MaterialTheme.motionScheme
                AnimatedContent(
                    targetState = editing,
                    transitionSpec = {
                        fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                    },
                    label = "detailMode",
                ) { edit ->
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        if (edit) {
                            RvItemEditor(
                                draft = draft,
                                onDraft = onDraft,
                                hasTotp = view.hasTotp,
                                codeLoad = detail.fields.firstNotNullOfOrNull { it.code },
                                strings = strings,
                                onChangePassword = onChangePassword,
                                onDelete = onDelete,
                            )
                        } else {
                            ItemView(
                                item = item,
                                zone = zone.kind,
                                fields = detail.fields,
                                strings = strings,
                                onCopy = onCopy,
                                onChangePassword = onChangePassword,
                                onLearnMore = onLearnMore,
                                onRecover = onRecover,
                                onPurge = onPurge,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    title: String,
    subtitle: String?,
    glyph: ImageVector?,
    favicon: Painter?,
    fallback: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RvSiteIcon(
            fallback = fallback,
            glyph = glyph,
            favicon = favicon,
            size = 96.dp,
            shape = MaterialShapes.Cookie9Sided.toShape(),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ItemView(
    item: RvItem,
    zone: RvZoneKind,
    fields: List<RvDetailField>,
    strings: RvStrings,
    onCopy: (String) -> Unit,
    onChangePassword: (String) -> Unit,
    onLearnMore: () -> Unit,
    onRecover: () -> Unit,
    onPurge: () -> Unit,
) {
    val web = item as? RvWebItem
    val pwned = web?.entry?.pwned == true
    val hasPasskey = item is RvPasskeyItem || web?.passkey != null
    val discarded = if (item !is RvWifiItem && item.isDeleted) deletedAt(item) else null
    var purgeOpen by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (pwned) {
            val site = siteOf(web)
            NoticeCard(
                icon = RvWarningIcon,
                tone = RvTone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer),
                title = if (zone == RvZoneKind.Security) strings.securityChangeTitle else strings.itemCompromised,
                description = strings.securityChangeDesc(labelOf(web)),
                action = if (site.isNotEmpty()) strings.securityChangeAction else null,
                onAction = { onChangePassword(site) },
            )
        }

        val shown = fields.filter { !it.bare }
        val notes = fields.filter { it.bare }

        RvSection {
            shown.forEachIndexed { index, field ->
                FieldRow(field = field, index = index, count = shown.size, onCopy = onCopy)
            }
        }

        notes.forEach { note ->
            RvSection(title = note.label) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = note.plain.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        if (hasPasskey) {
            NoticeCard(
                icon = RvPasskeyIcon,
                tone = RvTone(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer),
                title = strings.fieldPasskey,
                description = strings.passkeyNoteDesc,
                action = strings.passkeyLearnMore,
                onAction = onLearnMore,
            )
        }

        if (discarded != null) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = strings.deletedCountdown(daysLeft(discarded, DeletedDays).toInt()),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                RvButton(text = strings.deletedRecover, onClick = onRecover, kind = RvButtonKind.Tonal, icon = RvRecoverIcon)
                RvButton(
                    text = strings.deletedDelete,
                    onClick = { purgeOpen = true },
                    kind = RvButtonKind.DangerTonal,
                    icon = RvDeleteIcon,
                )
            }
        }
    }

    RvConfirmDialog(
        open = purgeOpen,
        onOpenChange = { purgeOpen = it },
        title = strings.deleteTitle(1),
        description = strings.deleteHardDesc(1),
        confirm = strings.deleteConfirm(1),
        cancel = strings.cancel,
        danger = true,
        icon = RvDeleteIcon,
        onConfirm = onPurge,
    )
}

@Composable
private fun FieldRow(
    field: RvDetailField,
    index: Int,
    count: Int,
    onCopy: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val secret = field.secret
    val code = field.code
    var revealed by remember(field) { mutableStateOf<String?>(null) }

    val copy: () -> Unit = {
        scope.launch {
            val text = field.plain ?: revealed ?: (secret ?: code)?.let { load -> runCatching { load() }.getOrNull() }
            if (!text.isNullOrEmpty()) onCopy(text)
        }
    }

    RvSegment(
        index = index,
        count = count,
        overline = { Text(field.label) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (secret != null) {
                    IconButton(
                        onClick = {
                            if (revealed != null) {
                                revealed = null
                            } else {
                                scope.launch { revealed = runCatching { secret() }.getOrNull() }
                            }
                        },
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            imageVector = if (revealed != null) RvVisibilityOffIcon else RvVisibilityIcon,
                            contentDescription = null,
                        )
                    }
                }
                if (field.copyable) {
                    IconButton(onClick = copy, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvCopyIcon, contentDescription = null)
                    }
                }
            }
        },
    ) {
        when {
            code != null -> RvTotpCode(load = code, style = MaterialTheme.typography.titleLarge)
            secret != null -> Text(
                text = revealed ?: "•".repeat(12),
                fontFamily = if (revealed != null) FontFamily.Monospace else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text(field.plain.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun NoticeCard(
    icon: ImageVector,
    tone: RvTone,
    title: String,
    description: String,
    action: String?,
    onAction: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = tone.container,
        contentColor = tone.content,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(title, style = MaterialTheme.typography.titleMediumEmphasized)
            }
            Text(description, style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                TextButton(
                    onClick = onAction,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = tone.content),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(action)
                }
            }
        }
    }
}

@Composable
private fun Placeholder(zone: RvHomeZone, strings: RvStrings, modifier: Modifier) {
    val text = strings.zoneText(zone.kind, zone.items.isEmpty())

    Column(
        modifier = modifier.fillMaxSize().padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RvShapeBadge(
            icon = zone.icon.icon,
            tone = zone.tone,
            polygon = zone.kind.polygon(),
            size = 128.dp,
            iconSize = 56.dp,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = text.emptyTitle,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = text.emptyDescription,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 420.dp),
        )
    }
}

private fun deletedAt(item: RvItem): Long = when (item) {
    is RvWebItem -> item.entry.deleted
    is RvPasskeyItem -> item.entry.deleted
    is RvWifiItem -> 0L
}
