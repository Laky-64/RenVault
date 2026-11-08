package io.lauren.renvault.lib

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

@Composable
internal actual fun rememberLocalNetworkAccess(needed: Boolean): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.CINNAMON_BUN) return true
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED)
    }
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(needed, granted) {
        if (needed && !granted && !asked) {
            asked = true
            launcher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
        }
    }
    return granted
}
