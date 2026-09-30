package io.github.xxlinnix.swivel.data.modea

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
import android.util.Log
import io.github.xxlinnix.swivel.SwivelApp
import io.github.xxlinnix.swivel.data.bluetooth.BluetoothEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps the app alive while a Mode A controller is linked, so the link survives the
 * screen turning off and the user switching to a game.
 *
 * A foreground service of type connectedDevice, as Android 14+ requires for Bluetooth
 * links (developer.android.com/develop/background-work/services/fgs/service-types). Its
 * prerequisite is the BLUETOOTH_CONNECT permission, which pairing already obtained.
 * Foreground services are exempt from App Standby, and Doze does not suspend Bluetooth
 * sockets. The service stops itself when the link stops.
 */
class ModeAService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val container get() = (application as SwivelApp).container
    private var watching = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val address = intent?.getStringExtra(EXTRA_ADDRESS)
        if (intent?.action == ACTION_DISCONNECT) {
            container.modeALink.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_CONNECT || address == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!goForeground(notification(ModeAState.Connecting(address, null, 1)))) {
            stopSelf()
            return START_NOT_STICKY
        }
        container.modeALink.start(address)
        watch()
        return START_NOT_STICKY
    }

    private fun watch() {
        if (watching) return
        watching = true
        scope.launch {
            container.modeALink.state.collect { state ->
                if (state is ModeAState.Idle || state is ModeAState.Stopped) {
                    Log.i(TAG, "link ended ($state), stopping service")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                } else {
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
                }
            }
        }
        // A controller that wakes up reconnects to the phone by itself; answer at once.
        scope.launch {
            container.bluetooth.events().collect { event ->
                if (event is BluetoothEvent.LinkConnected) {
                    container.modeALink.onControllerAppeared(event.address)
                }
            }
        }
    }

    private fun goForeground(notification: Notification): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        true
    } catch (e: RuntimeException) {
        // SecurityException without BLUETOOTH_CONNECT, or a background start Android refused.
        Log.w(TAG, "could not start in the foreground", e)
        false
    }

    private fun notification(state: ModeAState): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Controller connection", NotificationManager.IMPORTANCE_LOW),
        )
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val disconnect = PendingIntent.getService(
            this, 1, Intent(this, ModeAService::class.java).setAction(ACTION_DISCONNECT), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle(title(state))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Disconnect", disconnect).build())
            .build()
    }

    private fun title(state: ModeAState): String = when (state) {
        is ModeAState.Connecting -> "Connecting to ${state.name ?: "controller"}…"
        is ModeAState.Connected -> "${state.name ?: "Controller"} connected in Mode A"
        is ModeAState.Waiting -> "${state.name ?: "Controller"} disconnected, retrying"
        is ModeAState.Stopped, ModeAState.Idle -> "Controller disconnected"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "Swivel"
        private const val CHANNEL_ID = "mode_a_link"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_CONNECT = "io.github.xxlinnix.swivel.CONNECT"
        private const val ACTION_DISCONNECT = "io.github.xxlinnix.swivel.DISCONNECT"
        private const val EXTRA_ADDRESS = "address"

        /** Only call while the app is in the foreground: Android refuses background starts. */
        fun connect(context: Context, address: String) {
            val intent = Intent(context, ModeAService::class.java)
                .setAction(ACTION_CONNECT)
                .putExtra(EXTRA_ADDRESS, address)
            context.startForegroundService(intent)
        }
    }
}
