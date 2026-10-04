package io.homeassistant.companion.android.kiosk.camera

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.AndroidEntryPoint
import io.homeassistant.companion.android.common.R as commonR
import io.homeassistant.companion.android.common.data.kiosk.KioskSettingsRepository
import io.homeassistant.companion.android.common.data.kiosk.KioskStreamCredentials
import io.homeassistant.companion.android.common.util.SdkVersion
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/** Notification channel for the stream's ongoing notification, which a camera service must show. */
private const val CHANNEL_CAMERA_STREAM = "Kiosk camera stream"

private const val NOTIFICATION_ID = 95123

/**
 * Keeps the kiosk camera stream served while it is switched on.
 *
 * A foreground service, typed `camera`, because the stream has to answer while the device is
 * sitting on a wall with its screen off or showing something else -- which is exactly what Android
 * stops an app from doing with a camera otherwise. The ongoing notification it must show is also
 * the honest signal that the camera is in use, and it stays for as long as the stream does.
 */
@AndroidEntryPoint
class KioskCameraStreamService : Service() {

    @Inject
    lateinit var kioskSettingsRepository: KioskSettingsRepository

    @Inject
    internal lateinit var streamServer: KioskCameraStreamServer

    private val scope = CoroutineScope(SupervisorJob())
    private var serving: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground()

        scope.launch {
            kioskSettingsRepository.settingsFlow()
                .map { Triple(it.cameraStreamEnabled, it.cameraStreamPort, it.cameraStreamPassword) }
                .distinctUntilChanged()
                .collect { (enabled, port, password) ->
                    serving?.cancel()
                    // No password means no stream. The server has no unauthenticated mode, and
                    // starting it without credentials would be a listener nobody could use and
                    // nobody asked for.
                    if (!enabled || password == null) {
                        stopSelf()
                        return@collect
                    }
                    serving = launch { streamServer.serve(port, KioskStreamCredentials(password)) }
                }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        Timber.d("Kiosk camera stream service stopped")
    }

    private fun startForeground() {
        createNotificationChannel()

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_CAMERA_STREAM)
            .setSmallIcon(commonR.drawable.ic_stat_ic_notification)
            .setContentTitle(getString(commonR.string.kiosk_camera_stream_notification_title))
            .setContentText(getString(commonR.string.kiosk_camera_stream_notification_text))
            .setOngoing(true)
            .build()

        // The service type is only required, and the constant only available to lint, from API 34,
        // the same guard the microphone service uses.
        if (SdkVersion.isAtLeast(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (!SdkVersion.isAtLeast(Build.VERSION_CODES.O)) return

        val manager = getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_CAMERA_STREAM) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CAMERA_STREAM,
                    getString(commonR.string.kiosk_camera_stream_notification_channel),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )
        }
    }

    companion object {
        /** Starts or stops the stream service to match [enabled]. */
        fun setEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, KioskCameraStreamService::class.java)
            if (enabled) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.stopService(intent)
            }
        }
    }
}
