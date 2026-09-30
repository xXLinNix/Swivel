package io.github.xxlinnix.swivel.data.virtualpad

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import rikka.shizuku.Shizuku

/** Whether Shizuku can run Swivel's pad service as the shell user. */
enum class ShizukuStatus {
    /** The Shizuku app is not installed. */
    NOT_INSTALLED,

    /** Installed, but not started since the phone booted. */
    NOT_RUNNING,

    /** Too old: user services need Shizuku 11 or later. */
    TOO_OLD,

    /** Running, but the user has not allowed Swivel yet. */
    NEEDS_PERMISSION,

    READY,
}

/**
 * Watches Shizuku (github.com/RikkaApps/Shizuku-API, MIT). Shizuku runs a server with
 * `adb shell` privileges that the user starts through Wireless debugging, and lets apps
 * the user approves run code under it.
 */
class ShizukuGate(context: Context) {
    private val packageManager = context.applicationContext.packageManager

    private val _status = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    init {
        Shizuku.addBinderReceivedListenerSticky { refresh() }
        Shizuku.addBinderDeadListener { refresh() }
        Shizuku.addRequestPermissionResultListener { _, _ -> refresh() }
        refresh()
    }

    fun refresh() {
        _status.value = when {
            !installed() -> ShizukuStatus.NOT_INSTALLED
            !Shizuku.pingBinder() -> ShizukuStatus.NOT_RUNNING
            Shizuku.isPreV11() -> ShizukuStatus.TOO_OLD
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuStatus.NEEDS_PERMISSION
            else -> ShizukuStatus.READY
        }
        Log.i(TAG, "Shizuku: ${_status.value}")
    }

    /** Shows Shizuku's own "allow Swivel?" dialog. */
    fun requestPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
    }

    private fun installed(): Boolean = try {
        packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val REQUEST_CODE = 7
        private const val TAG = "Swivel"
    }
}
