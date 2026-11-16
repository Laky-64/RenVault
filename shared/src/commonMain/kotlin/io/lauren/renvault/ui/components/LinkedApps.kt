package io.lauren.renvault.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import io.lauren.renvault.lib.appDetails
import io.lauren.renvault.lib.appIcon
import androidx.compose.ui.Alignment
import androidx.compose.material3.LoadingIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.decodeToImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.lauren.renvault.lib.RvFavicons
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.appLabel
import io.lauren.renvault.lib.installedApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val AppIconSize = 40.dp

private data class RvInstalledApp(val packageName: String, val label: String)

@Composable
fun rememberAppName(packageName: String): String {
    val name by produceState(appLabel(packageName) ?: packageName, packageName) {
        appDetails(packageName).name?.let { value = it }
    }
    return name
}

@Composable
fun RvAppIcon(packageName: String, label: String, store: Boolean = true) {
    val target = with(LocalDensity.current) { AppIconSize.toPx() }.roundToInt()
    val painter by produceState<Painter?>(null, packageName, store, target) {
        val bytes = if (store) appDetails(packageName).icon else withContext(Dispatchers.Default) { appIcon(packageName) }
        if (bytes == null) return@produceState
        value = withContext(Dispatchers.Default) {
            runCatching {
                val image = bytes.decodeToImageBitmap()
                RvFavicon(image, RvFavicons.enhance, drawn = image.halvedToward(target))
            }.getOrNull()
        }
    }
    RvSiteIcon(fallback = label, favicon = painter)
}

@Composable
fun rememberAppsSummary(packages: List<String>, strings: RvStrings): String {
    val first = rememberAppName(packages.firstOrNull().orEmpty())
    return when (packages.size) {
        0 -> ""
        1 -> first
        else -> strings.fieldAppMore(first, packages.size - 1)
    }
}

@Composable
private fun AppSegment(
    packageName: String,
    index: Int,
    count: Int,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    store: Boolean = true,
    known: String? = null,
) {
    val label = known ?: rememberAppName(packageName)
    RvSegment(
        index = index,
        count = count,
        onClick = onClick,
        leading = { RvAppIcon(packageName, label, store) },
        trailing = trailing,
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun RvAppsSheet(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    apps: List<String>,
    strings: RvStrings,
) {
    if (!open) return
    ModalBottomSheet(
        onDismissRequest = { onOpenChange(false) },
        sheetState = rememberRvSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = strings.apps,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.fillMaxWidth(),
            )
            RvSection {
                apps.forEachIndexed { index, app ->
                    AppSegment(packageName = app, index = index, count = apps.size)
                }
            }
        }
    }
}

@Composable
fun RvAppsDialog(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    apps: List<String>,
    onApps: (List<String>) -> Unit,
    strings: RvStrings,
) {
    if (!open) return

    val linked = remember { mutableStateListOf<String>().apply { addAll(apps) } }
    var pickerOpen by remember { mutableStateOf(false) }
    val canPick by produceState(false) { value = withContext(Dispatchers.Default) { installedApps().isNotEmpty() } }

    RvAlertDialog(
        onDismissRequest = { onOpenChange(false) },
        icon = { Icon(RvPhoneIcon, contentDescription = null) },
        title = { Text(strings.apps) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(strings.appsDesc, modifier = Modifier.padding(bottom = 8.dp))
                if (linked.isNotEmpty()) {
                    RvSection {
                        linked.forEachIndexed { index, app ->
                            AppSegment(
                                packageName = app,
                                index = index,
                                count = linked.size,
                                trailing = {
                                    IconButton(onClick = { linked.remove(app) }, shapes = IconButtonDefaults.shapes()) {
                                        Icon(
                                            imageVector = RvRemoveCircleIcon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
                if (canPick) {
                    TextButton(onClick = { pickerOpen = true }, shapes = ButtonDefaults.shapes()) {
                        Icon(RvAddIcon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                        Text(strings.appsAdd, modifier = Modifier.padding(start = ButtonDefaults.IconSpacing))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onApps(linked.distinct())
                    onOpenChange(false)
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(strings.editorSave)
            }
        },
        dismissButton = {
            TextButton(onClick = { onOpenChange(false) }, shapes = ButtonDefaults.shapes()) { Text(strings.cancel) }
        },
    )

    RvAppPickerSheet(
        open = pickerOpen,
        onOpenChange = { pickerOpen = it },
        exclude = linked.toSet(),
        strings = strings,
        onPick = { linked += it },
    )
}

@Composable
private fun RvAppPickerSheet(
    open: Boolean,
    onOpenChange: (Boolean) -> Unit,
    exclude: Set<String>,
    strings: RvStrings,
    onPick: (String) -> Unit,
) {
    if (!open) return
    val sheet = rememberRvSheetState()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    val installed by produceState<List<RvInstalledApp>?>(null) {
        value = withContext(Dispatchers.Default) {
            installedApps()
                .map { RvInstalledApp(it, appLabel(it) ?: it) }
                .sortedBy { it.label.lowercase() }
        }
    }
    val shown = installed.orEmpty().filter { app ->
        app.packageName !in exclude && (query.isBlank() || app.label.contains(query.trim(), ignoreCase = true) || app.packageName.contains(query.trim(), ignoreCase = true))
    }

    ModalBottomSheet(
        onDismissRequest = { onOpenChange(false) },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = strings.appsAdd,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.fillMaxWidth(),
            )
            RvSearchInput(query = query, onQuery = { query = it }, placeholder = strings.appsSearch)
            if (installed == null) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator(modifier = Modifier.size(48.dp))
                }
            } else if (shown.isEmpty()) {
                Text(
                    text = strings.appsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = WindowInsets.navigationBars.asPaddingValues().let { bars ->
                    PaddingValues(bottom = bars.calculateBottomPadding() + 24.dp)
                },
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            ) {
                itemsIndexed(shown, key = { _, app -> app.packageName }) { index, app ->
                    AppSegment(
                        packageName = app.packageName,
                        index = index,
                        count = shown.size,
                        store = false,
                        known = app.label,
                        onClick = {
                            onPick(app.packageName)
                            scope.launch { sheet.hide() }.invokeOnCompletion { onOpenChange(false) }
                        },
                    )
                }
            }
        }
    }
}
