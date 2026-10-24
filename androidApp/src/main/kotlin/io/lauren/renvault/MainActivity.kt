package io.lauren.renvault

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import io.lauren.renvault.bridge.PlatformStorage
import io.lauren.renvault.ui.theme.RenVaultTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        PlatformStorage.attach(applicationContext)

        setContent {
            RenVaultTheme {
                RvVaultApp(
                    onOpenUrl = { target ->
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
                        }
                    },
                )
            }
        }
    }
}
