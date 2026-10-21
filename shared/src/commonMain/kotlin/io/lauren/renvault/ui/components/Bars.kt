package io.lauren.renvault.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LazyListState.scrolledPast(): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > TitleSlack }
}

@Composable
fun LazyGridState.scrolledPast(): State<Boolean> = remember(this) {
    derivedStateOf { firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > TitleSlack }
}

private const val TitleSlack = 120

@Composable
fun RvPageBar(
    title: String,
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colorScheme.surface,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    val motion = MaterialTheme.motionScheme
    val container by animateColorAsState(
        targetValue = if (scrolled) MaterialTheme.colorScheme.surfaceContainer else background,
        animationSpec = motion.defaultEffectsSpec(),
        label = "barTint",
    )

    TopAppBar(
        title = {
            AnimatedVisibility(
                visible = scrolled,
                enter = fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 },
                exit = fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 2 },
            ) {
                Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = container, scrolledContainerColor = container),
    )
}

@Composable
fun RvPageTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(start = 4.dp, top = 8.dp, bottom = 16.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
