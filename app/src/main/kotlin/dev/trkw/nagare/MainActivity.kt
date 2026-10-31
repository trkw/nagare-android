package dev.trkw.nagare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private val model: ReaderViewModel by viewModels()

    /** A URL shared from another app, offered in the subscribe dialog. */
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // On recreation (rotation) the launch intent comes back unchanged, so handle it only once.
        if (savedInstanceState == null) handle(intent)
        setContent {
            NagareTheme {
                NagareApp(model, sharedUrl.value, onSharedUrlUsed = { sharedUrl.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent == null) return
        // A notification tap: our own intent, or FCM's when it showed the notification in the background.
        intent.getStringExtra(Push.EXTRA_ENTRY_ID)?.toIntOrNull()?.let { id ->
            intent.removeExtra(Push.EXTRA_ENTRY_ID)
            model.openById(id)
            return
        }
        if (intent.action != Intent.ACTION_SEND) return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
        sharedUrl.value = Regex("https?://\\S+").find(text)?.value
    }
}
