package com.aiconsole.kittlauncher2.obd

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aiconsole.kittlauncher2.KittApplication
import com.aiconsole.kittlauncher2.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Keeps the OBD link alive while other apps are in front (foreground service, type
 * connectedDevice). The session itself lives in [ObdManager]; this service owns its lifetime.
 */
class ObdConnectionService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var statusJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as KittApplication
        ensureChannel()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification("Connecting…"),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )

        val config = app.savedAdapter()
        if (config == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        app.obd.connect(app.savedAdapters())

        statusJob?.cancel()
        statusJob = scope.launch {
            combine(app.obd.state, app.obd.refreshHz) { state, hz -> status(state, hz) }
                .collect { text ->
                    getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification(text))
                }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        (application as KittApplication).obd.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun status(state: ObdState, hz: Float): String = when (state) {
        ObdState.Idle -> "Standing by"
        is ObdState.Connecting -> state.stage
        is ObdState.Connected -> "Linked to ${state.info.adapterName} · ${"%.1f".format(Locale.US, hz)} Hz"
        is ObdState.Error -> "${state.message} · retrying in ${state.retryInSeconds}s"
    }

    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, com.aiconsole.kittlauncher2.EditionComponents.mainActivity), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_kitt)
            .setContentTitle("KITT vehicle link")
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Vehicle link", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "kitt_obd"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ObdConnectionService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ObdConnectionService::class.java))
        }
    }
}
