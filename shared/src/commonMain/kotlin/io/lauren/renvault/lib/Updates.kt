package io.lauren.renvault.lib

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.lauren.renvault.RvAppChannel
import io.lauren.renvault.RvAppUpdates
import io.lauren.renvault.bridge.UpdateRelease
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.bridge.VaultException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

enum class RvUpdateChannel(val key: String) { Stable("stable"), Beta("beta") }

enum class RvUpdateFailure { Check, Limited, Download, Unsigned, Install }

sealed interface RvUpdateState {
    data object Idle : RvUpdateState
    data object Checking : RvUpdateState
    data object Current : RvUpdateState
    data class Available(val release: UpdateRelease) : RvUpdateState
    data class Downloading(val release: UpdateRelease, val progress: Float?) : RvUpdateState
    data class Installing(val release: UpdateRelease) : RvUpdateState
    data class Failed(val release: UpdateRelease?, val failure: RvUpdateFailure) : RvUpdateState
}

val RvUpdateState.release: UpdateRelease?
    get() = when (this) {
        is RvUpdateState.Available -> release
        is RvUpdateState.Downloading -> release
        is RvUpdateState.Installing -> release
        is RvUpdateState.Failed -> release
        else -> null
    }

object RvUpdates {
    private const val ChannelKey = "update_channel"
    private const val AutoKey = "update_auto"
    private const val CheckedKey = "update_checked"
    private const val SnoozeKey = "update_snooze"
    private const val SnoozeMs = 24L * 60 * 60 * 1000
    private const val OffKey = "off"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var updater: RvUpdater? = null
    private var job: Job? = null
    private var prepared = false

    val currentVersion: String get() = updater?.current.orEmpty()

    var supported by mutableStateOf(false)
        private set

    var state by mutableStateOf<RvUpdateState>(RvUpdateState.Idle)
        private set

    var channel by mutableStateOf(
        RvUpdateChannel.entries.firstOrNull { it.key == readPreference(ChannelKey) }
            ?: if (RvAppChannel == RvUpdateChannel.Stable.key) RvUpdateChannel.Stable else RvUpdateChannel.Beta,
    )
        private set

    var auto by mutableStateOf(readPreference(AutoKey) != OffKey)
        private set

    var checkedAt by mutableLongStateOf(readPreference(CheckedKey)?.toLongOrNull() ?: 0L)
        private set

    private var snooze by mutableStateOf(readPreference(SnoozeKey).orEmpty())

    var following by mutableStateOf(false)
        private set

    val prompt: Boolean
        get() {
            val current = state
            if (following) return current is RvUpdateState.Downloading || current is RvUpdateState.Installing || current is RvUpdateState.Failed
            val release = (current as? RvUpdateState.Available)?.release ?: return false
            val version = snooze.substringBefore('@')
            val at = snooze.substringAfter('@', "").toLongOrNull() ?: 0L
            return version != release.version || Clock.System.now().toEpochMilliseconds() - at > SnoozeMs
        }

    fun later() {
        following = false
        val release = state.release ?: return
        snooze = release.version + "@" + Clock.System.now().toEpochMilliseconds()
        writePreference(SnoozeKey, snooze)
    }

    fun installNow() {
        following = true
        install()
    }

    private suspend fun prepare(): RvUpdater? {
        if (!prepared) {
            prepared = true
            updater = if (RvAppUpdates) withContext(Dispatchers.Default) { runCatching { platformUpdater() }.getOrNull() } else null
            supported = updater != null
            updater?.let { found -> runCatching { Vault.cleanUpdates(found.directory) } }
        }
        return updater
    }

    fun start() {
        scope.launch {
            prepare() ?: return@launch
            if (auto) check(quiet = true)
        }
    }

    fun choose(next: RvUpdateChannel) {
        if (next == channel) return
        channel = next
        writePreference(ChannelKey, next.key)
        if (job?.isActive != true) {
            state = RvUpdateState.Idle
            refresh()
        }
    }

    fun toggleAuto(value: Boolean) {
        auto = value
        writePreference(AutoKey, if (value) "on" else OffKey)
    }

    fun refresh() {
        if (job?.isActive == true) return
        job = scope.launch { check(quiet = false) }
    }

    private suspend fun check(quiet: Boolean) {
        val found = prepare() ?: return
        if (state is RvUpdateState.Downloading || state is RvUpdateState.Installing) return
        if (!quiet) state = RvUpdateState.Checking
        try {
            val release = Vault.checkUpdate(found.current, found.platform, channel == RvUpdateChannel.Beta, found.directory)
            val now = Clock.System.now().toEpochMilliseconds()
            checkedAt = now
            writePreference(CheckedKey, now.toString())
            state = if (release.available) RvUpdateState.Available(release) else RvUpdateState.Current
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            val limited = "rate limited" in failure.message.orEmpty()
            state = if (quiet) RvUpdateState.Idle else RvUpdateState.Failed(null, if (limited) RvUpdateFailure.Limited else RvUpdateFailure.Check)
        }
    }

    fun install() {
        val release = state.release ?: return
        if (job?.isActive == true) return
        job = scope.launch {
            val found = prepare() ?: return@launch
            state = RvUpdateState.Downloading(release, null)
            val progress = launch {
                Vault.events.mapNotNull(Vault::updateProgress).collect { event ->
                    if (event.asset == release.asset && state is RvUpdateState.Downloading) {
                        state = RvUpdateState.Downloading(release, if (event.total > 0) (event.done.toFloat() / event.total).coerceIn(0f, 1f) else null)
                    }
                }
            }
            val path = try {
                Vault.downloadUpdate(release, found.directory)
            } catch (cancelled: CancellationException) {
                state = RvUpdateState.Available(release)
                throw cancelled
            } catch (failure: VaultException) {
                val unsigned = failure.message.orEmpty().let { "signed" in it || "checksum" in it }
                state = RvUpdateState.Failed(release, if (unsigned) RvUpdateFailure.Unsigned else RvUpdateFailure.Download)
                return@launch
            } catch (failure: Exception) {
                state = RvUpdateState.Failed(release, RvUpdateFailure.Download)
                return@launch
            } finally {
                progress.cancel()
            }
            state = RvUpdateState.Installing(release)
            val installed = runCatching { found.install(path) }.getOrDefault(false)
            state = if (installed) RvUpdateState.Current else RvUpdateState.Failed(release, RvUpdateFailure.Install)
        }
    }

    fun cancel() {
        if (state is RvUpdateState.Downloading) job?.cancel()
    }
}
