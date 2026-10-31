package dev.trkw.nagare

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dev.trkw.nagare.core.ApiClient
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

class NagareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Push.init(this)
    }
}

/** FCM setup. Which feeds notify is set per feed on the server; this only registers the device. */
object Push {
    const val CHANNEL = "new_entries"
    const val EXTRA_ENTRY_ID = "entry_id"

    /** False when the app was built without google-services.json. */
    var available = false
        private set

    fun init(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "新着記事", NotificationManager.IMPORTANCE_DEFAULT))
        if (BuildConfig.FIREBASE_APP_ID.isEmpty()) return
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        available = true
    }

    fun needsPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    suspend fun token(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(task.exception ?: IllegalStateException("no FCM token"))
        }
    }

    suspend fun deleteToken() = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener { cont.resume(Unit) }
    }

    val deviceName: String get() = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /** Opens MainActivity on the entry when tapped. */
    fun contentIntent(context: Context, entryId: Int?, url: String?): PendingIntent {
        val intent = if (entryId == null && url != null) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
        } else {
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                entryId?.let { putExtra(EXTRA_ENTRY_ID, it.toString()) }
            }
        }
        return PendingIntent.getActivity(
            context,
            entryId ?: url.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/**
 * While the app is in the background, FCM shows notification messages itself and a tap opens
 * MainActivity with the data as extras. This service covers the foreground case and token changes.
 */
class NagareMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val store = TokenStore(applicationContext)
        if (!store.pushEnabled) return
        val server = store.server ?: return
        val apiToken = store.token ?: return
        scope.launch {
            runCatching { ApiClient(server, apiToken).registerPush(token, Push.deviceName) }
                .onSuccess { store.pushToken = token }
        }
    }

    @SuppressLint("MissingPermission") // checked by Push.needsPermission above
    override fun onMessageReceived(message: RemoteMessage) {
        if (Push.needsPermission(this)) return
        val title = message.notification?.title ?: message.data["title"] ?: return
        val body = message.notification?.body ?: message.data["body"].orEmpty()
        val entryId = message.data["entry_id"]?.toIntOrNull()
        val url = message.data["url"]
        val notification = NotificationCompat.Builder(this, Push.CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(Push.contentIntent(this, entryId, url))
            .build()
        // The tag groups notifications for one feed, so a newer batch replaces the older one.
        val tag = message.notification?.tag
        try {
            NotificationManagerCompat.from(this).notify(tag, if (tag != null) 0 else (entryId ?: message.messageId.hashCode()), notification)
        } catch (_: SecurityException) {
            // Permission was revoked between the check and the call.
        }
    }
}
