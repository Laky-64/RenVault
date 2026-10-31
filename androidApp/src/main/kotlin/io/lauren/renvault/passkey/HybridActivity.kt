package io.lauren.renvault.passkey

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.ParcelUuid
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import io.lauren.renvault.R
import io.lauren.renvault.RvUnlockGate
import io.lauren.renvault.bridge.HybridPrompt
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.bridge.Vault
import io.lauren.renvault.ui.components.RvSegment
import io.lauren.renvault.ui.theme.RenVaultTheme
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

private const val LogTag = "RenVaultHybrid"
private const val NoCredentials = 0x2E
private const val CredentialExcluded = 0x19
private const val DiscoveryTimeout = 30_000L

private val FidoService = ParcelUuid.fromString("0000fff9-0000-1000-8000-00805f9b34fb")

internal sealed interface Stage {
    data object Starting : Stage
    data object Locked : Stage
    data object Bluetooth : Stage
    data object Connecting : Stage
    data class Asking(val prompt: HybridPrompt) : Stage
    data object Done : Stage
    data class Failed(val message: Int, val detail: String = "", val hint: Int? = null) : Stage
}

class HybridActivity : FragmentActivity() {
    private var session: String? = null
    private var started = false
    private var linked = false
    private val unseen = mutableStateOf(false)
    private var advertising: AdvertiseCallback? = null
    private val advertFailure = mutableStateOf<Int?>(null)

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)
        val fromIntent = intent.dataString

        setContent {
            RenVaultTheme {
                var stage by remember { mutableStateOf<Stage>(Stage.Starting) }
                var qr by remember { mutableStateOf(fromIntent) }
                val scope = rememberCoroutineScope()
                val sheetState = rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
                )
                val close: () -> Unit = {
                    scope.launch {
                        sheetState.hide()
                        finish()
                    }
                }
                val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
                    stage = if (bluetoothReady()) Stage.Connecting else Stage.Bluetooth
                }
                val enable = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
                    stage = if (bluetoothReady()) Stage.Connecting else Stage.Bluetooth
                }

                LaunchedEffect(Unit) {
                    lifecycleScope.launch { runCatching { Vault.listen() } }
                    runCatching { Vault.init() }
                    if (qr == null) qr = scan()
                    val code = qr
                    if (code == null) return@LaunchedEffect finish()
                    if (!code.startsWith("FIDO:/", ignoreCase = true)) {
                        stage = Stage.Failed(R.string.hybrid_invalid)
                        return@LaunchedEffect
                    }
                    stage = if (runCatching { Vault.state().unlocked }.getOrDefault(false)) Stage.Bluetooth else Stage.Locked
                }
                LaunchedEffect(stage) {
                    if (stage == Stage.Bluetooth && bluetoothReady()) {
                        stage = Stage.Connecting
                        return@LaunchedEffect
                    }
                    if (stage != Stage.Connecting || started) return@LaunchedEffect
                    val code = qr ?: return@LaunchedEffect
                    started = true
                    lifecycleScope.launch { connect(code)?.let { stage = it } }
                }
                LaunchedEffect(unseen.value) {
                    if (!unseen.value || linked || stage is Stage.Failed) return@LaunchedEffect
                    stage = Stage.Failed(R.string.hybrid_not_found, hint = R.string.hybrid_not_found_hint)
                }
                LaunchedEffect(advertFailure.value) {
                    val code = advertFailure.value ?: return@LaunchedEffect
                    stage = Stage.Failed(R.string.hybrid_failed, "bluetooth advertising failed ($code)")
                }
                LaunchedEffect(Unit) {
                    Vault.events.collect { event ->
                        Vault.hybridPrompt(event)?.takeIf { it.session == session }?.let { stage = Stage.Asking(it) }
                        Vault.hybridProgress(event)?.takeIf { it.session == session }?.let { progress ->
                            Log.i(LogTag, "progress ${progress.stage} command=${progress.command} status=${progress.status} ${progress.error}")
                            when (progress.stage) {
                                "ctap" -> if (progress.command in 1..2 && progress.status != 0) {
                                    stopAdvertising()
                                    stage = when (progress.status) {
                                        NoCredentials -> Stage.Failed(R.string.hybrid_no_passkey)
                                        CredentialExcluded -> Stage.Failed(R.string.hybrid_already_saved, hint = R.string.hybrid_already_saved_hint)
                                        else -> Stage.Failed(R.string.hybrid_failed, "CTAP 0x%02x".format(progress.status))
                                    }
                                }
                                "connected" -> {
                                    linked = true
                                    Log.i(LogTag, "handshake done")
                                    stopAdvertising()
                                }
                                "done" -> {
                                    stopAdvertising()
                                    stage = Stage.Done
                                    delay(1800)
                                    sheetState.hide()
                                    finish()
                                }
                                "failed" -> {
                                    stopAdvertising()
                                    Log.w(LogTag, "session failed: ${progress.error}")
                                    if (stage != Stage.Done && stage !is Stage.Failed && !unseen.value) stage = Stage.Failed(R.string.hybrid_failed, progress.error)
                                }
                            }
                        }
                    }
                }

                when (val current = stage) {
                    Stage.Starting -> Unit
                    Stage.Locked -> Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
                        RvUnlockGate(onUnlocked = { stage = Stage.Bluetooth })
                    }
                    else -> ModalBottomSheet(
                        onDismissRequest = ::finish,
                        sheetState = sheetState,
                    ) {
                        HybridSheet(
                            stage = current,
                            onBluetooth = {
                                val missing = missingPermissions()
                                if (missing.isNotEmpty()) {
                                    permissions.launch(missing)
                                } else {
                                    enable.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                }
                            },
                            onClose = close,
                            onAnswer = { prompt, account ->
                                scope.launch {
                                    val confirmed = account >= 0 && confirmUser() != false
                                    runCatching { Vault.hybridDecide(prompt.id, confirmed, account) }
                                    if (account < 0) close() else stage = Stage.Connecting
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    override fun onDestroy() {
        stopAdvertising()
        session?.let { id -> CoroutineScope(SupervisorJob() + Dispatchers.Default).launch { runCatching { Vault.hybridCancel(id) } } }
        super.onDestroy()
    }

    private suspend fun scan(): String? = suspendCancellableCoroutine { cont ->
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, options).startScan()
            .addOnSuccessListener { if (cont.isActive) cont.resume(it.rawValue) }
            .addOnCanceledListener { if (cont.isActive) cont.resume(null) }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) }
    }

    private suspend fun connect(qr: String): Stage? {
        val started = runCatching { Vault.hybridStart(qr) }.getOrElse {
            Log.w(LogTag, "start failed", it)
            val message = it.message.orEmpty()
            return if ("409" in message) {
                Stage.Failed(R.string.hybrid_qr_used, hint = R.string.hybrid_new_qr)
            } else {
                Stage.Failed(R.string.hybrid_failed, message)
            }
        }
        session = started.session
        Log.i(LogTag, "tunnel open, advertising ${started.advert.size} bytes")
        if (!startAdvertising(started.advert)) return Stage.Failed(R.string.hybrid_failed, "bluetooth advertising unavailable")
        lifecycleScope.launch {
            delay(DiscoveryTimeout)
            if (linked) return@launch
            Log.w(LogTag, "computer never connected")
            unseen.value = true
            stopAdvertising()
            runCatching { Vault.hybridCancel(started.session) }
        }
        return null
    }

    private fun missingPermissions(): Array<String> {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyList()
        }
        return needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }.toTypedArray()
    }

    private fun bluetoothReady(): Boolean {
        if (missingPermissions().isNotEmpty()) return false
        val adapter = getSystemService(BluetoothManager::class.java)?.adapter ?: return false
        return adapter.isEnabled && adapter.bluetoothLeAdvertiser != null
    }

    @SuppressLint("MissingPermission")
    private fun startAdvertising(advert: ByteArray): Boolean {
        val advertiser = getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser ?: return false
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()
        val data = AdvertiseData.Builder().addServiceData(FidoService, advert).build()
        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                Log.i(LogTag, "advertising started")
            }

            override fun onStartFailure(errorCode: Int) {
                Log.w(LogTag, "advertising failed: $errorCode")
                advertising = null
                advertFailure.value = errorCode
            }
        }
        return runCatching { advertiser.startAdvertising(settings, data, callback) }.isSuccess.also {
            if (it) advertising = callback
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopAdvertising() {
        val callback = advertising ?: return
        advertising = null
        runCatching { getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeAdvertiser?.stopAdvertising(callback) }
    }
}
