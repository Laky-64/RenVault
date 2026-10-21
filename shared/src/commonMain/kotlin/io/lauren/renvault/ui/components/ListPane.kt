package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvItem
import io.lauren.renvault.lib.RvItemIcon
import io.lauren.renvault.lib.RvSecretSource
import io.lauren.renvault.lib.RvSortField
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.RvZoneKind
import io.lauren.renvault.lib.viewOf

@Composable
fun RvListPane(
    zone: RvHomeZone,
    items: List<RvItem>,
    strings: RvStrings,
    secrets: RvSecretSource,
    favicon: (String) -> Painter?,
    listState: LazyListState,
    compact: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    searching: Boolean,
    onSearching: (Boolean) -> Unit,
    selecting: Boolean,
    onSelecting: (Boolean) -> Unit,
    picked: Set<String>,
    onPicked: (Set<String>) -> Unit,
    openId: String?,
    sortField: RvSortField,
    sortAscending: Boolean,
    onSort: (RvSortField, Boolean) -> Unit,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onDeleteSelected: () -> Unit,
    canDeleteSelected: Boolean,
    modifier: Modifier = Modifier,
) {
    val text = strings.zoneText(zone.kind, zone.items.isEmpty())
    val past by listState.scrolledPast()
    val scrolled = past || items.isEmpty()
    val allPicked = items.isNotEmpty() && picked.size == items.size
    val placeholder = if (zone.kind == RvZoneKind.All) strings.searchPlaceholder else strings.searchPlaceholderIn(text.name)

    val bars = when {
        selecting -> 2
        searching -> 1
        else -> 0
    }

    val motion = MaterialTheme.motionScheme

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = bars,
                transitionSpec = { fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
                label = "listBar",
            ) { bar ->
                when (bar) {
                    2 -> TopAppBar(
                        title = { Text(strings.listSelectedCount(picked.size)) },
                        navigationIcon = {
                            IconButton(onClick = { onSelecting(false) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(RvCloseIcon, contentDescription = strings.cancel)
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { onPicked(if (allPicked) emptySet() else items.map { it.id }.toSet()) },
                                shapes = IconButtonDefaults.shapes(),
                            ) {
                                Icon(
                                    imageVector = if (allPicked) RvDeselectIcon else RvSelectAllIcon,
                                    contentDescription = if (allPicked) strings.listDeselectAll else strings.listSelectAll,
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                    )

                    1 -> SearchBar(
                        query = query,
                        onQuery = onQuery,
                        placeholder = placeholder,
                        onClose = { onSearching(false) },
                        back = strings.navBack,
                    )

                    else -> RvPageBar(
                        title = text.name,
                        scrolled = scrolled,
                        navigationIcon = {
                            if (compact) {
                                IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                                    Icon(RvBackIcon, contentDescription = strings.navBack)
                                }
                            }
                        },
                        actions = {
                            IconButton(onClick = { onSearching(true) }, shapes = IconButtonDefaults.shapes()) {
                                Icon(RvSearchIcon, contentDescription = placeholder)
                            }
                            SortMenu(sortField, sortAscending, strings, onSort)
                            val deletable = zone.kind != RvZoneKind.Passkeys && zone.kind != RvZoneKind.Networks
                            if (items.isNotEmpty() && deletable) {
                                IconButton(onClick = { onSelecting(true) }, shapes = IconButtonDefaults.shapes()) {
                                    Icon(RvSelectIcon, contentDescription = strings.listSelect)
                                }
                            }
                        },
                    )
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = compact && zone.kind == RvZoneKind.All && !selecting && !searching,
                enter = scaleIn(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(strings.listAdd) },
                    icon = { Icon(RvAddIcon, contentDescription = null) },
                    onClick = onAdd,
                    expanded = !past,
                    modifier = Modifier.padding(end = 8.dp, bottom = 8.dp),
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
            if (items.isEmpty()) {
                EmptyList(
                    zone = zone,
                    title = if (query.isNotBlank()) strings.searchEmpty(query.trim()) else text.emptyTitle,
                    description = if (query.isNotBlank()) strings.searchEmptyDesc else text.emptyDescription,
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = RvLayout.gutter,
                        end = RvLayout.gutter,
                        top = 8.dp,
                        bottom = padding.calculateBottomPadding() + 112.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                ) {
                    item(key = "title") { RvPageTitle(text.name, strings.zoneItemCount(items.size)) }

                    itemsIndexed(items, key = { _, item -> item.id }) { index, entry ->
                        ItemRow(
                            entry = entry,
                            index = index,
                            count = items.size,
                            zone = zone.kind,
                            strings = strings,
                            secrets = secrets,
                            favicon = favicon,
                            selecting = selecting,
                            checked = entry.id in picked,
                            open = entry.id == openId,
                            onClick = {
                                if (selecting) {
                                    onPicked(if (entry.id in picked) picked - entry.id else picked + entry.id)
                                } else {
                                    onOpen(entry.id)
                                }
                            },
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selecting,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it * 2 } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it * 2 } + fadeOut(),
            ) {
                HorizontalFloatingToolbar(expanded = true) {
                    FilledTonalButton(
                        onClick = onDeleteSelected,
                        enabled = canDeleteSelected,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(RvDeleteIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(strings.listDelete, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemRow(
    entry: RvItem,
    index: Int,
    count: Int,
    zone: RvZoneKind,
    strings: RvStrings,
    secrets: RvSecretSource,
    favicon: (String) -> Painter?,
    selecting: Boolean,
    checked: Boolean,
    open: Boolean,
    onClick: () -> Unit,
) {
    val view = remember(entry, strings) { viewOf(entry, strings) }
    val icon = view.icon
    val compromised = zone == RvZoneKind.Security
    val code = if (zone == RvZoneKind.Codes && view.hasTotp) {
        remember(entry.id, secrets) { suspend { secrets.totp(entry.id) } }
    } else {
        null
    }

    val motion = MaterialTheme.motionScheme

    RvSegment(
        index = index,
        count = count,
        onClick = onClick,
        checked = if (selecting) checked else null,
        selected = if (!selecting) open else null,
        leading = {
            AnimatedContent(
                targetState = selecting && checked,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f) + fadeIn()) togetherWith
                        (scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f) + fadeOut())
                },
                label = "rowPick",
            ) { tick ->
                if (tick) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(RvCheckIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    RvSiteIcon(
                        fallback = view.title,
                        glyph = if (icon is RvItemIcon.WifiGlyph) RvWifiIcon else null,
                        favicon = (icon as? RvItemIcon.Favicon)?.let { favicon(it.domain) },
                    )
                }
            }
        },
        supporting = when {
            compromised -> {
                { Text(strings.itemCompromised, color = MaterialTheme.colorScheme.error) }
            }
            view.subtitle.isNotEmpty() -> {
                { Text(view.subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            else -> null
        },
        trailing = code?.let { load ->
            { RvTotpCode(load = load, style = MaterialTheme.typography.titleSmall) }
        },
    ) {
        Text(view.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyList(zone: RvHomeZone, title: String, description: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        RvShapeBadge(
            icon = zone.icon.icon,
            tone = zone.tone,
            polygon = zone.kind.polygon(),
            size = 112.dp,
            iconSize = 48.dp,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLargeEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp).widthIn(max = 360.dp),
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 360.dp),
        )
    }
}

@Composable
private fun SortMenu(
    field: RvSortField,
    ascending: Boolean,
    strings: RvStrings,
    onSort: (RvSortField, Boolean) -> Unit,
) {
    var open by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { open = true }, shapes = IconButtonDefaults.shapes()) {
            Icon(RvSortIcon, contentDescription = strings.listOrder)
        }

        DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
            listOf(
                RvSortField.Title to strings.sortTitle,
                RvSortField.Website to strings.sortWebsite,
                RvSortField.Modified to strings.sortModified,
                RvSortField.Created to strings.sortCreated,
            ).forEach { (option, label) ->
                MenuChoice(label = label, chosen = option == field) {
                    onSort(option, ascending)
                    open = false
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            MenuChoice(label = strings.sortAscending, chosen = ascending, icon = RvArrowUpIcon) {
                onSort(field, true)
                open = false
            }
            MenuChoice(label = strings.sortDescending, chosen = !ascending, icon = RvArrowDownIcon) {
                onSort(field, false)
                open = false
            }
        }
    }
}

@Composable
private fun MenuChoice(
    label: String,
    chosen: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = icon?.let { glyph -> { Icon(glyph, contentDescription = null) } },
        trailingIcon = if (chosen) {
            { Icon(RvCheckIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
        } else {
            null
        },
    )
}

@Composable
private fun SearchBar(
    query: String,
    onQuery: (String) -> Unit,
    placeholder: String,
    onClose: () -> Unit,
    back: String,
) {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { requester.requestFocus() } }

    TopAppBar(
        title = {
            RvSearchInput(
                query = query,
                onQuery = onQuery,
                placeholder = placeholder,
                modifier = Modifier.padding(end = 16.dp),
                focusRequester = requester,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                Icon(RvBackIcon, contentDescription = back)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
    )
}
