package io.github.xxlinnix.swivel.data.virtualpad

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.util.Log
import io.github.xxlinnix.swivel.core.virtualpad.PadServiceContract
import io.github.xxlinnix.swivel.core.virtualpad.XboxPadReport
import io.github.xxlinnix.swivel.data.modea.ModeALink
import io.github.xxlinnix.swivel.data.modea.ModeAState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

/** What the virtual gamepad is doing. */
sealed interface VirtualPadState {
    data object Off : VirtualPadState
    data class WaitingForShizuku(val status: ShizukuStatus) : VirtualPadState
    data object WaitingForController : VirtualPadState
    data object Starting : VirtualPadState
    data object Active : VirtualPadState
    data class Failed(val reason: String) : VirtualPadState
}

/**
 * Shows the Mode A controller to Android as a standard gamepad, so every game that
 * supports controllers can use it (docs/DECISIONS.md, D-024).
 *
 * While it is switched on, Shizuku is ready and the Mode A link is connected, a service
 * running as the shell user holds a virtual HID gamepad open and every report from the
 * link is forwarded to it. When the link drops, the gamepad is removed, so games see it
 * disconnect as they would a real one.
 */
class VirtualPad(
    context: Context,
    private val scope: CoroutineScope,
    private val shizuku: ShizukuGate,
    private val link: ModeALink,
) {
    private val prefs = context.applicationContext.getSharedPreferences("virtual_pad", Context.MODE_PRIVATE)

    private val _enabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _state = MutableStateFlow<VirtualPadState>(VirtualPadState.Off)
    val state: StateFlow<VirtualPadState> = _state.asStateFlow()

    private val args = Shizuku.UserServiceArgs(ComponentName(context.packageName, VirtualPadUserService::class.java.name))
        .daemon(false)
        .processNameSuffix("pad")
        .tag("virtual-pad")
        .version(SERVICE_VERSION)

    private var service: IBinder? = null
    private var bound = false
    private var forwardJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            Log.i(TAG, "pad service connected")
            service = binder
            scope.launch { reconcile() }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.i(TAG, "pad service disconnected")
            service = null
            bound = false
            stopForwarding()
            scope.launch { reconcile() }
        }
    }

    init {
        scope.launch {
            combine(
                _enabled,
                shizuku.status,
                link.state.map { it is ModeAState.Connected }.distinctUntilChanged(),
            ) { _, _, _ -> }.collect { reconcile() }
        }
    }

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, value).apply()
        _enabled.value = value
    }

    /** Brings the pad in line with the switch, Shizuku and the link. */
    @Synchronized
    private fun reconcile() {
        val status = shizuku.status.value
        val connected = link.state.value is ModeAState.Connected
        when {
            !_enabled.value -> {
                tearDown()
                _state.value = VirtualPadState.Off
            }
            status != ShizukuStatus.READY -> {
                tearDown()
                _state.value = VirtualPadState.WaitingForShizuku(status)
            }
            !connected -> {
                closePad()
                _state.value = VirtualPadState.WaitingForController
            }
            else -> openPad()
        }
    }

    private fun openPad() {
        val binder = service
        if (binder == null) {
            _state.value = VirtualPadState.Starting
            if (!bound) {
                bound = true
                try {
                    Shizuku.bindUserService(args, connection)
                } catch (e: RuntimeException) {
                    bound = false
                    _state.value = VirtualPadState.Failed("Shizuku refused to start the pad service: ${e.message}")
                }
            }
            return
        }
        if (_state.value == VirtualPadState.Active && forwardJob != null) return
        val error = call(binder, PadServiceContract.TX_OPEN) { it.writeString(PadServiceContract.DEVICE_NAME) }
        if (error != null) {
            _state.value = VirtualPadState.Failed(error)
            return
        }
        _state.value = VirtualPadState.Active
        forwardJob = scope.launch {
            // The current state first, then every report in order, so no quick tap is lost.
            send(binder, XboxPadReport.encode(link.snapshot.value))
            link.reports.collect { send(binder, XboxPadReport.encode(it)) }
        }
    }

    private fun closePad() {
        stopForwarding()
        service?.let { call(it, PadServiceContract.TX_CLOSE) { } }
    }

    private fun tearDown() {
        closePad()
        if (bound) {
            try {
                Shizuku.unbindUserService(args, connection, true)
            } catch (e: RuntimeException) {
                Log.w(TAG, "unbind failed", e)
            }
        }
        bound = false
        service = null
    }

    private fun stopForwarding() {
        forwardJob?.cancel()
        forwardJob = null
    }

    /** A two-way call. Returns the error text the service answered, or why the call failed. */
    private fun call(binder: IBinder, code: Int, write: (Parcel) -> Unit): String? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(PadServiceContract.DESCRIPTOR)
            write(data)
            binder.transact(code, data, reply, 0)
            reply.readException()
            if (code == PadServiceContract.TX_OPEN) reply.readString() else null
        } catch (e: RemoteException) {
            "The pad service stopped: ${e.message}"
        } catch (e: RuntimeException) {
            e.message ?: e.toString()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    private fun send(binder: IBinder, report: ByteArray) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(PadServiceContract.DESCRIPTOR)
            data.writeByteArray(report)
            binder.transact(PadServiceContract.TX_SEND, data, null, IBinder.FLAG_ONEWAY)
        } catch (e: RemoteException) {
            Log.w(TAG, "report not delivered", e)
        } finally {
            data.recycle()
        }
    }

    private companion object {
        const val TAG = "Swivel"
        const val KEY_ENABLED = "enabled"

        /** Bump when VirtualPadUserService changes, so Shizuku replaces the running one. */
        const val SERVICE_VERSION = 1
    }
}
