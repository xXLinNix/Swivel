package io.github.xxlinnix.swivel.data.modea

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import io.github.xxlinnix.swivel.core.detect.MogaNames
import io.github.xxlinnix.swivel.core.model.BatteryReading
import io.github.xxlinnix.swivel.core.model.ControllerSnapshot
import io.github.xxlinnix.swivel.core.protocol.LinkWatchdog
import io.github.xxlinnix.swivel.core.protocol.ModeADecoder
import io.github.xxlinnix.swivel.core.protocol.ModeAFrameParser
import io.github.xxlinnix.swivel.core.protocol.ModeAGeneration
import io.github.xxlinnix.swivel.core.protocol.ModeAProtocol
import io.github.xxlinnix.swivel.core.protocol.ReconnectPolicy
import io.github.xxlinnix.swivel.core.protocol.WatchdogAction
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The RFCOMM link to one Mode A controller, with reconnection.
 *
 * Follows developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices:
 * discovery is cancelled first, and the blocking connect and read calls run on the IO
 * dispatcher. Closing the socket is the only way to unblock them, so every exit path
 * closes it. [ModeAService] keeps the process alive while the link is up.
 */
class ModeALink(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow<ModeAState>(ModeAState.Idle)
    val state: StateFlow<ModeAState> = _state.asStateFlow()

    private val _snapshot = MutableStateFlow(ControllerSnapshot())
    val snapshot: StateFlow<ControllerSnapshot> = _snapshot.asStateFlow()

    /**
     * Every report in order, for the MOGA SDK bridge. [snapshot] keeps only the latest
     * value, so a quick tap between two reads of it would be lost to a game.
     */
    private val _reports = MutableSharedFlow<ControllerSnapshot>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val reports: SharedFlow<ControllerSnapshot> = _reports.asSharedFlow()

    private val _battery = MutableStateFlow<BatteryReading>(BatteryReading.NotReported)
    val battery: StateFlow<BatteryReading> = _battery.asStateFlow()

    /** The last report as hex, for the test screen. */
    private val _lastReport = MutableStateFlow<String?>(null)
    val lastReport: StateFlow<String?> = _lastReport.asStateFlow()

    private var job: Job? = null
    private val wake = Channel<Unit>(Channel.CONFLATED)

    /**
     * Bumped by every start and stop. A run only publishes state while its token is
     * current, so a run that is finishing cannot overwrite a newer Idle or Connecting.
     */
    @Volatile
    private var runToken = 0

    @Volatile
    private var socket: BluetoothSocket? = null

    fun start(address: String) {
        val name = nameOf(address)
        Log.i(TAG, "start $address ($name)")
        // Connecting is published before the job starts, so a watcher never sees a stale Idle.
        val token = restart(ModeAState.Connecting(address, name, 1))
        job = scope.launch(Dispatchers.IO) { run(token, address, name) }
    }

    fun stop() {
        Log.i(TAG, "stop")
        restart(ModeAState.Idle)
    }

    @Synchronized
    private fun restart(state: ModeAState): Int {
        job?.cancel()
        job = null
        closeSocket()
        resetControls()
        runToken++
        _state.value = state
        return runToken
    }

    @Synchronized
    private fun publish(token: Int, state: ModeAState) {
        if (token == runToken) _state.value = state
    }

    /** The controller connected at the Bluetooth level, usually after waking up. Retry at once. */
    fun onControllerAppeared(address: String) {
        val current = _state.value
        if (current is ModeAState.Waiting && current.address == address) wake.trySend(Unit)
    }

    private suspend fun run(token: Int, address: String, name: String?) {
        var attempt = 0
        var firstFailureAt: Long? = null
        var noAnswers = 0
        while (currentCoroutineContext().isActive) {
            attempt++
            publish(token, ModeAState.Connecting(address, name, attempt))
            val outcome = try {
                session(token, address, name)
            } catch (e: SecurityException) {
                Log.w(TAG, "permission lost", e)
                SessionEnd.UNAVAILABLE
            }
            if (token == runToken) resetControls()
            when (outcome) {
                SessionEnd.UNAVAILABLE -> {
                    publish(token, ModeAState.Stopped(address, name, StopReason.BLUETOOTH_UNAVAILABLE))
                    return
                }
                SessionEnd.NO_ANSWER -> if (++noAnswers >= MAX_NO_ANSWERS) {
                    publish(token, ModeAState.Stopped(address, name, StopReason.NO_ANSWER))
                    return
                }
                SessionEnd.WAS_CONNECTED -> {
                    // A fresh drop starts a fresh reconnect window.
                    attempt = 0
                    firstFailureAt = null
                    noAnswers = 0
                }
                SessionEnd.UNREACHABLE -> Unit
            }
            val now = SystemClock.elapsedRealtime()
            val since = firstFailureAt ?: now.also { firstFailureAt = it }
            if (ReconnectPolicy.shouldGiveUp(now - since)) {
                publish(token, ModeAState.Stopped(address, name, StopReason.UNREACHABLE))
                return
            }
            val retryIn = ReconnectPolicy.delayMs(maxOf(attempt, 1))
            publish(token, ModeAState.Waiting(address, name, maxOf(attempt, 1), retryIn))
            wake.tryReceive() // Drop a stale wake-up from before this wait.
            withTimeoutOrNull(retryIn) { wake.receive() }
        }
    }

    private enum class SessionEnd { WAS_CONNECTED, UNREACHABLE, NO_ANSWER, UNAVAILABLE }

    /** One connection, from opening the socket until it dies. */
    @SuppressLint("MissingPermission") // Checked by canConnect(); a revocation surfaces as SecurityException.
    private suspend fun session(token: Int, address: String, name: String?): SessionEnd {
        val adapter = adapter ?: return SessionEnd.UNAVAILABLE
        if (!adapter.isEnabled || !canConnect()) return SessionEnd.UNAVAILABLE
        if (canScan()) adapter.cancelDiscovery()

        val device = adapter.getRemoteDevice(address)
        val s = try {
            open(device)
        } catch (e: IOException) {
            Log.i(TAG, "connect failed: ${e.message}")
            return SessionEnd.UNREACHABLE
        }
        Log.i(TAG, "socket open")

        val progress = SessionProgress(
            if (MogaNames.isFirstGeneration(name)) ModeAGeneration.FIRST else ModeAGeneration.SECOND,
        )
        val output = s.outputStream
        return try {
            coroutineScope {
                launch { readLoop(token, s.inputStream, progress) }
                send(output, ModeAProtocol.SET_CONTROLLER_ID)
                sendPollAndListen(output, progress.generation)
                val watchdog = LinkWatchdog(SystemClock.elapsedRealtime())
                var result: SessionEnd? = null
                try {
                    while (result == null) {
                        delay(LinkWatchdog.TICK_MS)
                        if (progress.readerStopped) {
                            result = if (progress.lastReportAt != null) SessionEnd.WAS_CONNECTED else SessionEnd.UNREACHABLE
                            break
                        }
                        when (watchdog.onTick(SystemClock.elapsedRealtime(), progress.lastReportAt)) {
                            WatchdogAction.NONE -> Unit
                            WatchdogAction.RESEND, WatchdogAction.POLL -> sendPollAndListen(output, progress.generation)
                            WatchdogAction.SWITCH_GENERATION -> {
                                progress.generation = progress.generation.other
                                Log.i(TAG, "no answer, trying ${progress.generation}")
                                sendPollAndListen(output, progress.generation)
                            }
                            WatchdogAction.FAIL_NO_ANSWER -> result = SessionEnd.NO_ANSWER
                            WatchdogAction.FAIL_SILENT -> result = SessionEnd.WAS_CONNECTED
                        }
                    }
                } finally {
                    closeSocket(s) // Unblocks the reader so the scope can finish.
                }
                checkNotNull(result)
            }
        } catch (e: IOException) {
            Log.i(TAG, "write failed: ${e.message}")
            if (progress.lastReportAt != null) SessionEnd.WAS_CONNECTED else SessionEnd.UNREACHABLE
        } finally {
            closeSocket(s)
        }
    }

    /** Written by the reader thread, read by the watchdog. */
    private class SessionProgress(initial: ModeAGeneration) {
        @Volatile var generation: ModeAGeneration = initial
        @Volatile var lastReportAt: Long? = null
        @Volatile var readerStopped = false
    }

    /**
     * Reads until the socket closes, whether the controller went away or the watchdog
     * closed it. Either way it just stops and lets the watchdog decide why.
     */
    private fun readLoop(token: Int, input: InputStream, progress: SessionProgress) {
        val parser = ModeAFrameParser()
        val buffer = ByteArray(128)
        try {
            while (true) {
                val count = input.read(buffer)
                if (count < 0) return
                for (frame in parser.feed(buffer, 0, count)) {
                    val report = ModeADecoder.decode(frame) ?: continue
                    progress.generation = report.generation
                    progress.lastReportAt = SystemClock.elapsedRealtime()
                    _snapshot.value = report.snapshot
                    _reports.tryEmit(report.snapshot)
                    _battery.value = BatteryReading.LowFlag(report.lowBattery)
                    _lastReport.value = frame.hex()
                    val current = _state.value
                    if (current is ModeAState.Connecting) {
                        Log.i(TAG, "answering as ${report.generation}")
                        publish(token, ModeAState.Connected(current.address, current.name, report.generation))
                    }
                }
            }
        } catch (e: IOException) {
            Log.i(TAG, "read ended: ${e.message}")
        } finally {
            progress.readerStopped = true
        }
    }

    private fun sendPollAndListen(output: OutputStream, generation: ModeAGeneration) {
        send(output, generation.pollCommand)
        send(output, generation.listenCommand)
    }

    /**
     * The secure socket is Android's recommended one. The original Pivot app fell back to
     * an insecure socket on some phones, so that is tried second.
     */
    @SuppressLint("MissingPermission")
    private fun open(device: BluetoothDevice): BluetoothSocket {
        val uuid = UUID.fromString(ModeAProtocol.SERIAL_PORT_UUID)
        return try {
            connect(device.createRfcommSocketToServiceRecord(uuid))
        } catch (e: IOException) {
            Log.i(TAG, "secure socket failed (${e.message}), trying insecure")
            connect(device.createInsecureRfcommSocketToServiceRecord(uuid))
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(candidate: BluetoothSocket): BluetoothSocket {
        socket = candidate // So stop() can close it and abort a slow connect.
        try {
            candidate.connect()
        } catch (e: IOException) {
            closeSocket(candidate)
            throw e
        }
        return candidate
    }

    private fun send(output: OutputStream, command: Int) {
        output.write(ModeAProtocol.command(command, ModeAProtocol.DEFAULT_CONTROLLER_ID))
        output.flush()
    }

    /** Closes [target], by default the current socket, and forgets it if it is the current one. */
    private fun closeSocket(target: BluetoothSocket? = socket) {
        if (target == null) return
        if (socket === target) socket = null
        try { target.close() } catch (e: IOException) { }
    }

    private fun resetControls() {
        _snapshot.value = ControllerSnapshot()
        _reports.tryEmit(ControllerSnapshot()) // Games see every held button released.
        _battery.value = BatteryReading.NotReported
        _lastReport.value = null
    }

    @SuppressLint("MissingPermission")
    private fun nameOf(address: String): String? {
        val adapter = adapter ?: return null
        if (!canConnect() || !BluetoothAdapter.checkBluetoothAddress(address)) return null
        return try { adapter.getRemoteDevice(address).name } catch (e: SecurityException) { null }
    }

    private fun canConnect(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)

    private fun canScan(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_SCAN)

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "Swivel"

        /** Sockets that open but never answer, in a row, before giving up. */
        const val MAX_NO_ANSWERS = 2
    }
}
