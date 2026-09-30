package io.github.xxlinnix.swivel.data.bridge

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import io.github.xxlinnix.swivel.SwivelApp
import io.github.xxlinnix.swivel.core.bridge.KeyCodeStyle
import io.github.xxlinnix.swivel.core.bridge.ListenerRegistry
import io.github.xxlinnix.swivel.core.bridge.MogaSdk
import io.github.xxlinnix.swivel.core.bridge.MogaSdkMapping
import io.github.xxlinnix.swivel.core.bridge.SdkConnection
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.protocol.ModeAGeneration
import io.github.xxlinnix.swivel.data.modea.ModeAState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Stands in for the MOGA Pivot app's controller service, so games built with PowerA's
 * MOGA SDK get input from the Mode A link.
 *
 * Those games bind with the implicit action `com.bda.controller.IControllerService`,
 * which only games targeting Android 4.4W (API 20) or lower can still use. This answers
 * the SDK's Binder calls by hand, from the contract in docs/RESEARCH.md, and sends key,
 * motion and state events to each game's listener as one-way calls, so a slow game never
 * holds up the link. It only relays: the controller must already be connected in Swivel.
 */
class MogaSdkService : Service() {
    private val container get() = (application as SwivelApp).container
    private val link get() = container.modeALink
    private val registry = ListenerRegistry<IBinder>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var allowNewConnections = true
    private val binder = SdkBinder()

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "bridge created")
        scope.launch {
            var previous = link.snapshot.value
            link.reports.collect { snapshot ->
                sendChanges(previous, snapshot)
                previous = snapshot
            }
        }
        scope.launch {
            link.state.map { connectionOf(it) }.distinctUntilChanged().collect { connection ->
                broadcastState(MogaSdk.STATE_CONNECTION, MogaSdkMapping.connectionValue(connection))
            }
        }
        scope.launch {
            link.battery.map { lowBattery(it) }.distinctUntilChanged().collect { low ->
                broadcastState(MogaSdk.STATE_POWER_LOW, if (low) MogaSdk.TRUE else MogaSdk.FALSE)
            }
        }
    }

    /** The SDK starts the service before binding; being bound is what keeps it alive. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf(startId)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "a game bound to the bridge")
        return binder
    }

    override fun onDestroy() {
        scope.cancel()
        registry.all().forEach { forget(it.listener) }
        container.bridgeGames.value = 0
        Log.i(TAG, "bridge destroyed")
        super.onDestroy()
    }

    private inner class SdkBinder : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(MogaSdk.SERVICE_DESCRIPTOR)
                return true
            }
            if (code < MogaSdk.TX_REGISTER_LISTENER || code > MogaSdk.TX_IS_ALLOWING_NEW_CONNECTIONS) {
                return super.onTransact(code, data, reply, flags)
            }
            data.enforceInterface(MogaSdk.SERVICE_DESCRIPTOR)
            val uid = getCallingUid()
            when (code) {
                MogaSdk.TX_REGISTER_LISTENER, MogaSdk.TX_REGISTER_LISTENER_2 -> {
                    val listener = data.readStrongBinder()
                    val activityEvent = data.readInt()
                    val style = if (code == MogaSdk.TX_REGISTER_LISTENER) KeyCodeStyle.LEGACY else KeyCodeStyle.STANDARD
                    if (listener != null) register(listener, uid, style, activityEvent)
                    reply?.writeNoException()
                }
                MogaSdk.TX_UNREGISTER_LISTENER -> {
                    data.readStrongBinder()?.let { forget(it) }
                    data.readInt()
                    reply?.writeNoException()
                }
                MogaSdk.TX_REGISTER_MONITOR, MogaSdk.TX_UNREGISTER_MONITOR -> {
                    // Monitors received Pivot's debug log. There is nothing to send them.
                    data.readStrongBinder()
                    data.readInt()
                    reply?.writeNoException()
                }
                MogaSdk.TX_GET_INFO -> {
                    val info = data.readInt()
                    reply?.writeNoException()
                    reply?.writeInt(info(info))
                }
                MogaSdk.TX_GET_KEY_CODE, MogaSdk.TX_GET_KEY_CODE_2 -> {
                    val controllerId = data.readInt()
                    val keyCode = data.readInt()
                    val style = if (code == MogaSdk.TX_GET_KEY_CODE) KeyCodeStyle.LEGACY else KeyCodeStyle.STANDARD
                    val action = if (controllerId == MogaSdk.CONTROLLER_ID) {
                        MogaSdkMapping.keyAction(link.snapshot.value, keyCode, style)
                    } else {
                        MogaSdk.KEY_ACTION_UP
                    }
                    reply?.writeNoException()
                    reply?.writeInt(action)
                }
                MogaSdk.TX_GET_AXIS_VALUE -> {
                    val controllerId = data.readInt()
                    val axis = data.readInt()
                    val value = if (controllerId == MogaSdk.CONTROLLER_ID) MogaSdkMapping.axisValue(link.snapshot.value, axis) else 0f
                    reply?.writeNoException()
                    reply?.writeFloat(value)
                }
                MogaSdk.TX_GET_STATE -> {
                    val controllerId = data.readInt()
                    val state = data.readInt()
                    reply?.writeNoException()
                    reply?.writeInt(stateValue(controllerId, state))
                }
                MogaSdk.TX_SEND_MESSAGE -> {
                    val message = data.readInt()
                    val value = data.readInt()
                    if (message == MogaSdk.MSG_SET_ACTIVITY_EVENT) {
                        registry.setActivityEvent(uid, value)
                        Log.d(TAG, "game $uid activity event $value")
                    }
                    reply?.writeNoException()
                }
                MogaSdk.TX_ALLOW_NEW_CONNECTIONS -> {
                    allowNewConnections = true
                    reply?.writeNoException()
                }
                MogaSdk.TX_DISALLOW_NEW_CONNECTIONS -> {
                    allowNewConnections = false
                    reply?.writeNoException()
                }
                MogaSdk.TX_IS_ALLOWING_NEW_CONNECTIONS -> {
                    reply?.writeNoException()
                    reply?.writeInt(if (allowNewConnections) 1 else 0)
                }
            }
            return true
        }
    }

    private fun register(listener: IBinder, uid: Int, style: KeyCodeStyle, activityEvent: Int) {
        val known = registry.all().any { it.listener == listener }
        registry.register(listener, uid, style, activityEvent)
        if (!known) {
            try {
                listener.linkToDeath({ forget(listener) }, 0)
            } catch (e: RemoteException) {
                forget(listener)
                return
            }
        }
        container.bridgeGames.value = registry.gameCount()
        Log.i(TAG, "listener registered: uid $uid, $style, activity event $activityEvent")
        // Tell the game where things stand straight away, as a fresh connection would.
        val connection = connectionOf(link.state.value)
        sendState(listener, MogaSdk.STATE_CONNECTION, MogaSdkMapping.connectionValue(connection))
        if (lowBattery(link.battery.value)) sendState(listener, MogaSdk.STATE_POWER_LOW, MogaSdk.TRUE)
    }

    private fun forget(listener: IBinder) {
        registry.unregister(listener)
        container.bridgeGames.value = registry.gameCount()
    }

    private fun info(info: Int): Int {
        val state = link.state.value
        return when (info) {
            MogaSdk.INFO_KNOWN_DEVICE_COUNT -> if (state is ModeAState.Idle) 0 else 1
            MogaSdk.INFO_ACTIVE_DEVICE_COUNT -> if (state is ModeAState.Connected) 1 else 0
            else -> 0
        }
    }

    private fun stateValue(controllerId: Int, state: Int): Int {
        val current = link.state.value
        val firstGeneration = (current as? ModeAState.Connected)?.generation != ModeAGeneration.SECOND
        return MogaSdkMapping.state(controllerId, state, connectionOf(current), lowBattery(link.battery.value), firstGeneration)
    }

    private fun sendChanges(before: ControllerSnapshot, after: ControllerSnapshot) {
        val listeners = registry.receiving()
        if (listeners.isEmpty()) return
        val time = SystemClock.uptimeMillis()
        val keys = MogaSdkMapping.keyChanges(before, after)
        val moved = MogaSdkMapping.axesChanged(before, after)
        for (entry in listeners) {
            for (change in keys) {
                send(entry.listener, MogaSdk.TX_ON_KEY_EVENT) { parcel ->
                    writeBaseEvent(parcel, time)
                    parcel.writeInt(MogaSdkMapping.keyCode(change.button, entry.style))
                    parcel.writeInt(change.action)
                }
            }
            if (moved) {
                send(entry.listener, MogaSdk.TX_ON_MOTION_EVENT) { parcel ->
                    writeBaseEvent(parcel, time)
                    writePairs(parcel, MogaSdkMapping.motionAxes(after))
                    writePairs(parcel, MogaSdk.MOTION_PRECISION)
                }
            }
        }
    }

    private fun broadcastState(state: Int, action: Int) {
        registry.all().forEach { sendState(it.listener, state, action) }
    }

    private fun sendState(listener: IBinder, state: Int, action: Int) {
        send(listener, MogaSdk.TX_ON_STATE_EVENT) { parcel ->
            writeBaseEvent(parcel, SystemClock.uptimeMillis())
            parcel.writeInt(state)
            parcel.writeInt(action)
        }
    }

    /**
     * Sends one event the way the SDK's generated proxy does: interface token, then 1
     * for "not null", then the event's fields. One-way, so the game never blocks the link.
     */
    private fun send(listener: IBinder, code: Int, writeEvent: (Parcel) -> Unit) {
        val parcel = Parcel.obtain()
        try {
            parcel.writeInterfaceToken(MogaSdk.LISTENER_DESCRIPTOR)
            parcel.writeInt(1)
            writeEvent(parcel)
            listener.transact(code, parcel, null, IBinder.FLAG_ONEWAY)
        } catch (e: RemoteException) {
            Log.i(TAG, "a game went away")
            forget(listener)
        } finally {
            parcel.recycle()
        }
    }

    /** BaseEvent: event time, then controller id. */
    private fun writeBaseEvent(parcel: Parcel, time: Long) {
        parcel.writeLong(time)
        parcel.writeInt(MogaSdk.CONTROLLER_ID)
    }

    /** A SparseArray of floats as the SDK's MotionEvent writes it: count, then key and value pairs. */
    private fun writePairs(parcel: Parcel, values: Map<Int, Float>) {
        parcel.writeInt(values.size)
        values.forEach { (key, value) ->
            parcel.writeInt(key)
            parcel.writeFloat(value)
        }
    }

    private fun connectionOf(state: ModeAState): SdkConnection = when (state) {
        is ModeAState.Connected -> SdkConnection.CONNECTED
        is ModeAState.Connecting, is ModeAState.Waiting -> SdkConnection.CONNECTING
        ModeAState.Idle, is ModeAState.Stopped -> SdkConnection.DISCONNECTED
    }

    private fun lowBattery(battery: BatteryReading): Boolean = battery is BatteryReading.LowFlag && battery.low

    private companion object {
        const val TAG = "Swivel"
    }
}
