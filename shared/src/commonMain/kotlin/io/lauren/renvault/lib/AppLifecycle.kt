package io.lauren.renvault.lib

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlin.time.TimeSource

private const val LeaveGraceMs = 3_000L

object RvLockGuard {
    private var armed: TimeSource.Monotonic.ValueTimeMark? = null

    fun expectLeave() {
        armed = TimeSource.Monotonic.markNow()
    }

    internal fun expected(): Boolean {
        val mark = armed ?: return false
        return mark.elapsedNow().inWholeMilliseconds < LeaveGraceMs
    }
}

object RvLeaveSignal {
    private val leaves = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events: SharedFlow<Unit> = leaves.asSharedFlow()

    fun left() {
        leaves.tryEmit(Unit)
    }
}

expect val leaveEvent: Lifecycle.Event

@Composable
expect fun RvHideFromRecents(hidden: Boolean)
