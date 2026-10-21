package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.RvZoneKind

@Composable
fun RvZonesPane(
    zones: List<RvHomeZone>,
    title: String,
    photo: ImageBitmap?,
    strings: RvStrings,
    selected: RvZoneKind?,
    modifier: Modifier = Modifier,
    onZone: (RvZoneKind) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit = {},
) {
    if (selected != null) {
        ZonesRail(zones, title, photo, strings, selected, modifier, onZone, onSettings, onAdd)
        return
    }

    val grid = rememberLazyGridState()
    val scrolled by grid.scrolledPast()

    Scaffold(
        modifier = modifier,
        topBar = {
            RvPageBar(
                title = title,
                scrolled = scrolled,
                actions = {
                    IconButton(onClick = onSettings, shapes = IconButtonDefaults.shapes()) {
                        RvProfileAvatar(size = 32.dp, photo = photo)
                    }
                },
            )
        },
    ) { padding ->
        LazyVerticalGrid(
            state = grid,
            columns = GridCells.Adaptive(minSize = 156.dp),
            contentPadding = PaddingValues(
                start = RvLayout.gutter,
                end = RvLayout.gutter,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) { RvPageTitle(title) }
            item(span = { GridItemSpan(maxLineSpan) }) {
                SearchPill(text = strings.searchPlaceholder, onClick = onSearch)
            }
            items(zones, key = { it.kind }) { zone ->
                ZoneTile(zone = zone, name = strings.zoneText(zone.kind, zone.items.isEmpty()).name) {
                    onZone(zone.kind)
                }
            }
        }
    }
}

@Composable
private fun ZoneTile(zone: RvHomeZone, name: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                RvShapeBadge(
                    icon = zone.icon.icon,
                    tone = zone.tone,
                    polygon = zone.kind.polygon(),
                    size = 52.dp,
                    iconSize = 26.dp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = zone.items.size.toString(),
                    style = MaterialTheme.typography.headlineMediumEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun SearchPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(RvSearchIcon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ZonesRail(
    zones: List<RvHomeZone>,
    title: String,
    photo: ImageBitmap?,
    strings: RvStrings,
    selected: RvZoneKind,
    modifier: Modifier,
    onZone: (RvZoneKind) -> Unit,
    onSettings: () -> Unit,
    onAdd: () -> Unit,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, top = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = onSettings, shapes = IconButtonDefaults.shapes()) {
                RvProfileAvatar(size = 36.dp, photo = photo)
            }
        }

        ExtendedFloatingActionButton(
            text = { Text(strings.listAdd) },
            icon = { Icon(RvAddIcon, contentDescription = null) },
            onClick = onAdd,
            modifier = Modifier.padding(start = 4.dp, bottom = 20.dp),
        )

        RvSection {
            zones.forEachIndexed { index, zone ->
                RvSegment(
                    index = index,
                    count = zones.size,
                    selected = zone.kind == selected,
                    onClick = { onZone(zone.kind) },
                    leading = {
                        RvShapeBadge(
                            icon = zone.icon.icon,
                            tone = zone.tone,
                            polygon = zone.kind.polygon(),
                            size = 40.dp,
                            iconSize = 20.dp,
                        )
                    },
                    trailing = {
                        Text(
                            text = zone.items.size.toString(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                ) {
                    Text(
                        text = strings.zoneText(zone.kind, zone.items.isEmpty()).name,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.size(24.dp))
    }
}
