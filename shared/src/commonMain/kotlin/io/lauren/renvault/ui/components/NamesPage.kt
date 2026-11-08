package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItemDefaults
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvNameSwap
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.ui.theme.RenVault
import kotlinx.coroutines.launch

data class RvNameMatch(val id: String, val title: String, val subtitle: String = "")

private data class NameEdit(val index: Int?, val swap: RvNameSwap)

@Composable
internal fun NamesPage(
    names: List<RvNameSwap>,
    matches: List<RvNameMatch>,
    notice: Boolean,
    onNotice: (Boolean) -> Unit,
    strings: RvStrings,
    dialog: Boolean,
    onBack: () -> Unit,
    onNames: suspend (List<RvNameSwap>) -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val accents = RenVault.accents
    val list = rememberLazyListState()
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface

    var editing by remember { mutableStateOf<NameEdit?>(null) }

    val store: (List<RvNameSwap>) -> Unit = { next -> scope.launch { runCatching { onNames(next) } } }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            RvPageBar(
                title = strings.namesTitle,
                scrolled = scrolled,
                background = background,
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvBackIcon, contentDescription = strings.navBack)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = RvLayout.gutter,
                end = RvLayout.gutter,
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 32.dp,
            ),
        ) {
            item { RvPageTitle(strings.namesTitle) }

            item {
                Text(
                    text = strings.namesIntro,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = SectionGap).padding(horizontal = 4.dp),
                )
            }

            itemsIndexed(names) { index, swap ->
                RvSegment(
                    index = index,
                    count = names.size + 1,
                    modifier = Modifier.padding(top = if (index == 0) SectionGap else RowGap),
                    onClick = { editing = NameEdit(index, swap) },
                    leading = { SettingBadge(RvBadgeIcon, accents.forIndex(index)) },
                    supporting = { Text(strings.namesEntry) },
                    trailing = {
                        IconButton(
                            onClick = { store(names.filterIndexed { at, _ -> at != index }) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(RvDeleteIcon, contentDescription = strings.namesRemove)
                        }
                    },
                ) {
                    Text(swap.to.ifEmpty { "—" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            item {
                Column {
                    RvSegment(
                        index = names.size,
                        count = names.size + 1,
                        modifier = Modifier.padding(top = if (names.isEmpty()) SectionGap else RowGap),
                        onClick = { editing = NameEdit(null, RvNameSwap("", "")) },
                        leading = { SettingBadge(RvAddIcon, accents.blue) },
                    ) {
                        Text(strings.namesAdd)
                    }
                    RvSectionFooter(strings.namesNote)
                }
            }

            if (names.isNotEmpty()) {
                item {
                    RvSection(modifier = Modifier.padding(top = SectionGap)) {
                        RvSegment(
                            index = 0,
                            count = 1,
                            onClick = { onNotice(!notice) },
                            supporting = { Text(strings.namesNoticeDesc) },
                            trailing = { Switch(checked = notice, onCheckedChange = null) },
                        ) {
                            Text(strings.namesNotice)
                        }
                    }
                }

                item { RvSectionTitle(strings.namesWhereTitle, Modifier.padding(top = SectionGap)) }

                if (matches.isEmpty()) {
                    item {
                        RvSegment(index = 0, count = 1) {
                            Text(
                                text = strings.namesWhereEmpty,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    itemsIndexed(matches, key = { _, match -> match.id }) { index, match ->
                        RvSegment(
                            index = index,
                            count = matches.size,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else RowGap),
                            onClick = { onOpenItem(match.id) },
                            supporting = match.subtitle.takeIf { it.isNotEmpty() }?.let { subtitle ->
                                { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            },
                        ) {
                            Text(match.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item { RvSectionFooter(strings.namesWhereFooter) }
                }
            }
        }
    }

    editing?.let { edit ->
        NameDialog(
            initial = edit.swap,
            strings = strings,
            onDismiss = { editing = null },
            onSave = { swap ->
                editing = null
                val at = edit.index
                store(if (at == null) names + swap else names.mapIndexed { i, old -> if (i == at) swap else old })
            },
        )
    }
}

@Composable
private fun NameDialog(
    initial: RvNameSwap,
    strings: RvStrings,
    onDismiss: () -> Unit,
    onSave: (RvNameSwap) -> Unit,
) {
    var hidden by remember { mutableStateOf(initial.from) }
    var shown by remember { mutableStateOf(initial.to) }
    val ready = hidden.isNotBlank()
    val save = { if (ready) onSave(RvNameSwap(hidden.trim(), shown.trim())) }

    RvAlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(RvBadgeIcon, contentDescription = null) },
        title = { Text(strings.namesDialogTitle) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                RvTextField(
                    value = hidden,
                    onValueChange = { hidden = it },
                    label = strings.namesHiddenLabel,
                    capitalization = KeyboardCapitalization.Words,
                )
                RvTextField(
                    value = shown,
                    onValueChange = { shown = it },
                    label = strings.namesShownLabel,
                    capitalization = KeyboardCapitalization.Words,
                    onDone = save,
                )
            }
        },
        confirmButton = {
            Button(onClick = save, enabled = ready, shapes = ButtonDefaults.shapes()) {
                Text(strings.editorSave)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )
}

private val SectionGap = 24.dp
private val RowGap = ListItemDefaults.SegmentedGap
