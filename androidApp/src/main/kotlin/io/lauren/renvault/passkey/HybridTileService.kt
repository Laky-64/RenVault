package io.lauren.renvault.passkey

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService

class HybridTileService : TileService() {
    override fun onClick() {
        if (isLocked) unlockAndRun(::launch) else launch()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun launch() {
        val intent = Intent(this, HybridActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
