package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TextButton
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.material3.toPath
import androidx.graphics.shapes.Morph
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import io.lauren.renvault.RvAppVersion
import io.lauren.renvault.bridge.UpdateRelease
import io.lauren.renvault.lib.RvStrings
import io.lauren.renvault.lib.RvUpdateChannel
import io.lauren.renvault.lib.RvUpdateFailure
import io.lauren.renvault.lib.RvUpdateState
import io.lauren.renvault.lib.RvUpdates
import io.lauren.renvault.lib.relativeOf
import io.lauren.renvault.lib.release
import io.lauren.renvault.ui.theme.RenVault
import io.lauren.renvault.ui.theme.RvTone
import kotlin.math.roundToInt

private class NotesGroup(val title: String?, val lines: List<String>)

private fun groupsOf(body: String): List<NotesGroup> {
    val groups = mutableListOf<NotesGroup>()
    var title: String? = null
    var lines = mutableListOf<String>()
    fun flush() {
        if (lines.isNotEmpty() || title != null) groups += NotesGroup(title, lines)
        lines = mutableListOf()
    }
    body.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        when {
            line.startsWith("#") -> {
                flush()
                title = line.dropWhile { !it.isLetterOrDigit() }.trim().ifEmpty { null }
            }
            line.startsWith("- ") || line.startsWith("* ") -> lines += line.drop(2).trim()
            else -> lines += line
        }
    }
    flush()
    return groups.filter { it.lines.isNotEmpty() }
}

private val UpdateRelease.installable: Boolean
    get() = url.isNotEmpty() && sums.isNotEmpty() && signature.isNotEmpty()



@Composable
private fun GithubFallback(release: UpdateRelease, strings: RvStrings) {
    val uri = LocalUriHandler.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RvButton(
            text = strings.updatesGithub,
            onClick = { runCatching { uri.openUri(release.page) } },
            icon = RvOpenInNewIcon,
        )
        Text(
            text = strings.updatesGithubNote,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun megabytes(size: Long): String {
    val tenths = (size / 100_000.0).roundToInt()
    return if (tenths % 10 == 0) "${tenths / 10}" else "${tenths / 10}.${tenths % 10}"
}

private enum class HeroKind { Idle, Checking, Current, Available, Downloading, Installing, Failed }

private val RvUpdateState.hero: HeroKind
    get() = when (this) {
        RvUpdateState.Idle -> HeroKind.Idle
        RvUpdateState.Checking -> HeroKind.Checking
        RvUpdateState.Current -> HeroKind.Current
        is RvUpdateState.Available -> HeroKind.Available
        is RvUpdateState.Downloading -> HeroKind.Downloading
        is RvUpdateState.Installing -> HeroKind.Installing
        is RvUpdateState.Failed -> HeroKind.Failed
    }

@Composable
private fun SpinningBadge(icon: ImageVector, tone: RvTone, polygon: RoundedPolygon) {
    val spin = rememberInfiniteTransition(label = "updateSpin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "updateTurn")
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .rotate(turn)
                .clip(polygon.toShape())
                .background(tone.container),
        )
        Icon(icon, contentDescription = null, tint = tone.content, modifier = Modifier.size(42.dp))
    }
}

private val RvUpdateState.group: Int
    get() = when (this) {
        RvUpdateState.Checking -> 0
        RvUpdateState.Idle, RvUpdateState.Current -> 1
        is RvUpdateState.Available, is RvUpdateState.Downloading, is RvUpdateState.Installing -> 2
        is RvUpdateState.Failed -> 3
    }

private val RingAmplitude = 0.8f

private val RingWavelength = 28.dp

@Composable
private fun UpdateBadge(state: RvUpdateState) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val busy = state is RvUpdateState.Downloading || state is RvUpdateState.Installing
    val shift by animateFloatAsState(if (busy) 1f else 0f, motion.slowSpatialSpec(), label = "updateMorph")
    val morph = remember { Morph(MaterialShapes.Sunny, MaterialShapes.Circle) }
    val spin = rememberInfiniteTransition(label = "updateSpin")
    val turn by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(24_000, easing = LinearEasing)), label = "updateTurn")
    val path = remember { Path() }
    val unit = remember { Matrix() }
    Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
        if (shift > 0.01f) {
            Box(
                modifier = Modifier.graphicsLayer {
                    alpha = shift.coerceIn(0f, 1f)
                    val grow = 0.85f + 0.15f * shift
                    scaleX = grow
                    scaleY = grow
                },
            ) {
                if (state is RvUpdateState.Downloading) {
                    val shown by animateFloatAsState(state.progress ?: 0f, motion.defaultEffectsSpec(), label = "updateProgress")
                    CircularWavyProgressIndicator(
                        progress = { shown },
                        modifier = Modifier.size(124.dp),
                        amplitude = { if (it <= 0.1f || it >= 0.95f) 0f else RingAmplitude },
                        wavelength = RingWavelength,
                    )
                } else {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(124.dp),
                        amplitude = RingAmplitude,
                        wavelength = RingWavelength,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .size(96.dp - 8.dp * shift.coerceIn(0f, 1f))
                .drawBehind {
                    path.rewind()
                    morph.toPath(shift.coerceIn(0f, 1f), path)
                    unit.reset()
                    unit.scale(size.width, size.height)
                    path.transform(unit)
                    withTransform({ rotate(turn * (1f - shift.coerceIn(0f, 1f))) }) {
                        drawPath(path, colors.primaryContainer)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(RvDownloadIcon, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(42.dp))
        }
    }
}

@Composable
private fun UpdateHero(state: RvUpdateState) {
    val accents = RenVault.accents
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        contentKey = { it.group },
        transitionSpec = {
            scaleIn(motion.defaultSpatialSpec(), initialScale = 0.6f) togetherWith scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f)
        },
        contentAlignment = Alignment.Center,
        label = "updateHero",
    ) { shown ->
        Box(modifier = Modifier.size(124.dp), contentAlignment = Alignment.Center) {
            when (shown.group) {
                0 -> LoadingIndicator(modifier = Modifier.size(120.dp))
                1 -> SpinningBadge(RvCheckIcon, accents.green, MaterialShapes.Cookie12Sided)
                2 -> UpdateBadge(shown)
                else -> SpinningBadge(RvWarningIcon, RvTone(MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer), MaterialShapes.Cookie6Sided)
            }
        }
    }
}

private fun headline(state: RvUpdateState, strings: RvStrings): String = when (state) {
    RvUpdateState.Checking -> strings.updatesChecking
    RvUpdateState.Idle, RvUpdateState.Current -> strings.updatesCurrent
    is RvUpdateState.Available -> strings.updatesAvailable(state.release.version)
    is RvUpdateState.Downloading -> strings.updatesAvailable(state.release.version)
    is RvUpdateState.Installing -> strings.updatesInstalling
    is RvUpdateState.Failed -> when (state.failure) {
        RvUpdateFailure.Check -> strings.updatesFailedCheck
        RvUpdateFailure.Limited -> strings.updatesFailedLimited
        RvUpdateFailure.Download -> strings.updatesFailedDownload
        RvUpdateFailure.Unsigned -> strings.updatesFailedUnsigned
        RvUpdateFailure.Install -> strings.updatesFailedInstall
    }
}

@Composable
private fun UpdateActions(current: RvUpdateState, strings: RvStrings) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = current,
        contentKey = { it.hero },
        transitionSpec = {
            (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth(),
        label = "updateActions",
    ) { state ->
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (state) {
                RvUpdateState.Idle, RvUpdateState.Current -> RvButton(
                    text = strings.updatesCheck,
                    onClick = RvUpdates::refresh,
                    kind = RvButtonKind.Tonal,
                    icon = RvSyncIcon,
                )
                RvUpdateState.Checking, is RvUpdateState.Installing -> Unit
                is RvUpdateState.Available -> if (state.release.installable) {
                    RvButton(
                        text = strings.updatesDownload,
                        onClick = RvUpdates::install,
                        icon = RvDownloadIcon,
                    )
                } else {
                    GithubFallback(state.release, strings)
                }
                is RvUpdateState.Downloading -> Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = listOfNotNull(
                            strings.updatesDownloading,
                            state.progress?.let { "${(it * 100).roundToInt()}%" },
                            state.release.size.takeIf { it > 0 }?.let { strings.updatesSize(megabytes(it)) },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    RvButton(
                        text = strings.updatesCancel,
                        onClick = RvUpdates::cancel,
                        kind = RvButtonKind.Tonal,
                        icon = RvCloseIcon,
                    )
                }
                is RvUpdateState.Failed -> RvButton(
                    text = strings.updatesRetry,
                    onClick = { if (state.release != null) RvUpdates.install() else RvUpdates.refresh() },
                    kind = RvButtonKind.Tonal,
                    icon = RvSyncIcon,
                )
            }
        }
    }
}

@Composable
private fun NotesCard(version: String, body: String, strings: RvStrings) {
    val groups = remember(body) { groupsOf(body) }
    if (groups.isEmpty()) return
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(
                text = strings.updatesWhatsNew(version),
                style = MaterialTheme.typography.titleLargeEmphasized,
            )
            groups.forEach { group ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    group.title?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    group.lines.forEach { line ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 8.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                            )
                            Text(line, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}



@Composable
internal fun UpdatesPage(strings: RvStrings, dialog: Boolean, onBack: () -> Unit) {
    val list = rememberLazyListState()
    val scrolled by list.scrolledPast()
    val background = if (dialog) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface
    val state = RvUpdates.state
    val release: UpdateRelease? = state.release
    val checked = relativeOf(RvUpdates.checkedAt)?.let { strings.updatesChecked(strings.relativeTime(it)) } ?: strings.updatesNever

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            RvPageBar(
                title = strings.updatesTitle,
                scrolled = scrolled,
                background = background,
                navigationIcon = {
                    IconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvBackIcon, contentDescription = strings.navBack)
                    }
                },
                actions = {
                    val busy = state is RvUpdateState.Checking || state is RvUpdateState.Downloading || state is RvUpdateState.Installing
                    IconButton(onClick = RvUpdates::refresh, enabled = !busy, shapes = IconButtonDefaults.shapes()) {
                        Icon(RvSyncIcon, contentDescription = strings.updatesCheck)
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
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item { RvPageTitle(strings.updatesTitle) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    UpdateHero(state)
                    Text(
                        text = headline(state, strings),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp),
                    )
                    Text(
                        text = listOf("v${RvUpdates.currentVersion.ifEmpty { RvAppVersion }}", checked).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            item { UpdateActions(state, strings) }

            release?.notes?.let { notes ->
                items(notes, key = { it.version }) { entry ->
                    NotesCard(entry.version, entry.body, strings)
                }
            }

            item {
                RvSection(title = strings.updatesChannel) {
                    RvUpdateChannel.entries.forEachIndexed { index, choice ->
                        val picked = choice == RvUpdates.channel
                        RvSegment(
                            index = index,
                            count = RvUpdateChannel.entries.size,
                            selected = picked,
                            onClick = { RvUpdates.choose(choice) },
                            leading = { RadioButton(selected = picked, onClick = null) },
                            supporting = {
                                Text(if (choice == RvUpdateChannel.Stable) strings.updatesStableDesc else strings.updatesBetaDesc)
                            },
                        ) {
                            Text(if (choice == RvUpdateChannel.Stable) strings.settingsChannelStable else strings.updatesBeta)
                        }
                    }
                }
            }

            item {
                RvSection(footer = strings.updatesVerified) {
                    RvSegment(
                        index = 0,
                        count = 1,
                        onClick = { RvUpdates.toggleAuto(!RvUpdates.auto) },
                        leading = { SettingBadge(RvVerifiedIcon, RenVault.accents.green) },
                        supporting = { Text(strings.updatesAutoDesc) },
                        trailing = { Switch(checked = RvUpdates.auto, onCheckedChange = null) },
                    ) {
                        Text(strings.updatesAuto)
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpdateBanner(strings: RvStrings, onOpen: () -> Unit) {
    val state = RvUpdates.state
    val release = (state as? RvUpdateState.Available)?.release
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = release != null,
        enter = expandVertically(motion.defaultSpatialSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f),
        exit = shrinkVertically(motion.fastSpatialSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.9f),
    ) {
        val colors = MaterialTheme.colorScheme
        Surface(
            onClick = onOpen,
            shape = MaterialTheme.shapes.extraLarge,
            color = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                RvShapeBadge(
                    icon = RvDownloadIcon,
                    tone = RvTone(colors.primary, colors.onPrimary),
                    polygon = MaterialShapes.Sunny,
                    size = 48.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(strings.updatesAvailable(release?.version.orEmpty()), style = MaterialTheme.typography.titleMedium)
                    Text(strings.updatesDownload, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
internal fun UpdatePill(strings: RvStrings) {
    val accents = RenVault.accents
    Surface(shape = CircleShape, color = accents.green.container, contentColor = accents.green.content) {
        Text(
            text = strings.updatesNew,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}



@Composable
internal fun UpdateSheet(strings: RvStrings) {
    if (!RvUpdates.supported || !RvUpdates.prompt) return
    val state = RvUpdates.state
    val busy = state is RvUpdateState.Downloading || state is RvUpdateState.Installing
    val sheet = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !(RvUpdates.state is RvUpdateState.Downloading || RvUpdates.state is RvUpdateState.Installing) },
    )
    val release = state.release
    ModalBottomSheet(
        onDismissRequest = { if (!busy) RvUpdates.later() },
        sheetState = sheet,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            UpdateHero(state)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = headline(state, strings),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                )
                release?.let {
                    Text(
                        text = "v${RvUpdates.currentVersion} → v${it.version}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            release?.notes?.forEach { entry -> NotesCard(entry.version, entry.body, strings) }
            val motion = MaterialTheme.motionScheme
            AnimatedContent(
                targetState = state,
                contentKey = { it.hero },
                transitionSpec = {
                    (fadeIn(motion.fastEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f)) togetherWith
                        (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)) using
                        SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                },
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth(),
                label = "updateSheetActions",
            ) { shown ->
                val target = shown.release
                when (shown) {
                    is RvUpdateState.Available, is RvUpdateState.Failed -> Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (target != null && !target.installable) {
                            GithubFallback(target, strings)
                        } else {
                            RvButton(
                                text = if (shown is RvUpdateState.Failed) strings.updatesRetry else strings.updatesNow,
                                onClick = RvUpdates::installNow,
                                icon = RvDownloadIcon,
                            )
                        }
                        TextButton(
                            onClick = RvUpdates::later,
                            shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(strings.updatesLater)
                        }
                    }
                    else -> Box(modifier = Modifier.fillMaxWidth()) { UpdateActions(shown, strings) }
                }
            }
        }
    }
}

@Composable
internal fun UpdateDot(avatar: Dp, content: @Composable () -> Unit) {
    val shown = RvUpdates.supported && RvUpdates.state is RvUpdateState.Available
    val motion = MaterialTheme.motionScheme
    val ring = 12.dp
    val edge = avatar / 2 * 0.7071f
    Box {
        content()
        AnimatedVisibility(
            visible = shown,
            enter = scaleIn(motion.defaultSpatialSpec()),
            exit = scaleOut(motion.fastSpatialSpec()),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 24.dp + edge - ring / 2, y = 24.dp - edge - ring / 2),
        ) {
            Box(
                modifier = Modifier
                    .size(ring)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                Badge(modifier = Modifier.size(8.dp), containerColor = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
