package io.github.xxlinnix.swivel.data.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.os.Parcelable
import android.util.Log
import io.github.xxlinnix.swivel.core.detect.MogaNames
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * The phone's Bluetooth adapter, behind the runtime permissions Android 12 introduced.
 *
 * Every call that needs BLUETOOTH_CONNECT or BLUETOOTH_SCAN checks for it first and
 * returns an empty or false result without it, so callers never see a SecurityException
 * (developer.android.com/develop/connectivity/bluetooth/bt-permissions).
 */
class BluetoothGateway(context: Context) {
    private val context = context.applicationContext
    private val adapter: BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    val isSupported: Boolean get() = adapter != null

    /** Needs no permission on any version. */
    val isEnabled: Boolean get() = adapter?.isEnabled == true

    /** Before Android 12 the install-time BLUETOOTH permission covers this. */
    fun hasConnectPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || granted(Manifest.permission.BLUETOOTH_CONNECT)

    /**
     * In-app scanning is offered on Android 12 and later only. Earlier versions would need
     * the location permission for a scan, and the companion-device chooser does not.
     */
    fun canScanInApp(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && granted(Manifest.permission.BLUETOOTH_SCAN)

    /** Bonded devices whose name marks them as MOGA controllers. */
    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission().
    fun pairedMogas(): List<PairedDevice> {
        val adapter = adapter ?: return emptyList()
        if (!hasConnectPermission()) return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .filter { MogaNames.isMoga(it.name) }
                .map { PairedDevice(it.address, it.name, it.uuids.toUuidStrings()) }
                .sortedBy { it.name }
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission().
    fun paired(address: String): PairedDevice? {
        val device = remote(address) ?: return null
        if (!hasConnectPermission()) return null
        return try {
            if (device.bondState != BluetoothDevice.BOND_BONDED) null
            else PairedDevice(device.address, device.name, device.uuids.toUuidStrings())
        } catch (e: SecurityException) {
            null
        }
    }

    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission().
    fun nameOf(address: String): String? {
        val device = remote(address) ?: return null
        if (!hasConnectPermission()) return null
        return try { device.name } catch (e: SecurityException) { null }
    }

    /**
     * Starts bonding. Android shows its own pairing dialog when the controller asks for a
     * PIN (older MOGAs use 1234). The outcome arrives as a [BluetoothEvent.BondChanged].
     * Returns true when bonding started or the device is already bonded.
     */
    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission().
    fun createBond(address: String): Boolean {
        val device = remote(address) ?: return false
        if (!hasConnectPermission()) return false
        return try {
            device.bondState == BluetoothDevice.BOND_BONDED || device.createBond()
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Asks the controller which services it offers now, rather than trusting the list Android
     * cached at pairing time. The answer arrives as a [BluetoothEvent.ServicesDiscovered].
     */
    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission().
    fun refreshServices(address: String): Boolean {
        val device = remote(address) ?: return false
        if (!hasConnectPermission()) return false
        return try { device.fetchUuidsWithSdp() } catch (e: SecurityException) { false }
    }

    @SuppressLint("MissingPermission") // Guarded by canScanInApp().
    fun startDiscovery(): Boolean {
        val adapter = adapter ?: return false
        if (!canScanInApp()) return false
        return try {
            adapter.cancelDiscovery()
            adapter.startDiscovery()
        } catch (e: SecurityException) {
            false
        }
    }

    /** Discovery slows every other Bluetooth link down, so it is stopped before connecting. */
    @SuppressLint("MissingPermission") // Guarded by canScanInApp().
    fun cancelDiscovery() {
        if (!canScanInApp()) return
        try { adapter?.cancelDiscovery() } catch (e: SecurityException) { }
    }

    /** Bluetooth broadcasts while collected. Each collector registers its own receiver. */
    fun events(): Flow<BluetoothEvent> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val event = toEvent(intent) ?: return
                Log.d(TAG, "event: $event")
                trySend(event)
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(BluetoothDevice.ACTION_UUID)
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        // Registered without an export flag, as Android asks for receivers of system
        // broadcasts only (developer.android.com/about/versions/14/behavior-changes-14).
        // RECEIVER_NOT_EXPORTED drops them: they come from the Bluetooth process, not the
        // system server, so the wizard never heard that bonding finished. They are all
        // protected broadcasts, which no app can forge.
        context.registerReceiver(receiver, filter)
        awaitClose { context.unregisterReceiver(receiver) }
    }

    private fun toEvent(intent: Intent): BluetoothEvent? {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
            val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
            return BluetoothEvent.AdapterChanged(state == BluetoothAdapter.STATE_ON)
        }
        if (intent.action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) return BluetoothEvent.DiscoveryFinished

        val device = intent.parcelable(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return null
        val address = device.address
        return when (intent.action) {
            BluetoothDevice.ACTION_BOND_STATE_CHANGED -> BluetoothEvent.BondChanged(
                address = address,
                name = safeName(device),
                bond = bondOf(intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)),
                previous = bondOf(intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.BOND_NONE)),
            )
            BluetoothDevice.ACTION_ACL_CONNECTED -> BluetoothEvent.LinkConnected(address)
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> BluetoothEvent.LinkDisconnected(address)
            BluetoothDevice.ACTION_UUID -> BluetoothEvent.ServicesDiscovered(address, uuidsFrom(intent))
            BluetoothDevice.ACTION_FOUND -> BluetoothEvent.DeviceFound(
                address,
                intent.getStringExtra(BluetoothDevice.EXTRA_NAME) ?: safeName(device),
            )
            else -> null
        }
    }

    @SuppressLint("MissingPermission") // Guarded by hasConnectPermission().
    private fun safeName(device: BluetoothDevice): String? {
        if (!hasConnectPermission()) return null
        return try { device.name } catch (e: SecurityException) { null }
    }

    private fun remote(address: String): BluetoothDevice? {
        val adapter = adapter ?: return null
        if (!BluetoothAdapter.checkBluetoothAddress(address)) return null
        return adapter.getRemoteDevice(address)
    }

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    private fun bondOf(state: Int): Bond = when (state) {
        BluetoothDevice.BOND_BONDED -> Bond.BONDED
        BluetoothDevice.BOND_BONDING -> Bond.BONDING
        else -> Bond.NONE
    }

    private fun uuidsFrom(intent: Intent): Set<String> {
        val parcels: Array<out Parcelable>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID, ParcelUuid::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)
        }
        return parcels.orEmpty().filterIsInstance<ParcelUuid>().map { it.uuid.toString().lowercase() }.toSet()
    }

    private fun Array<ParcelUuid>?.toUuidStrings(): Set<String> =
        orEmpty().map { it.uuid.toString().lowercase() }.toSet()

    private companion object {
        /** Filter Logcat on this tag to follow pairing. */
        const val TAG = "Swivel"
    }
}

/** Intent.getParcelableExtra without the deprecation dance at every call site. */
internal fun <T : Parcelable> Intent.parcelable(name: String, type: Class<T>): T? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(name, type)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(name)
    }
