package io.github.xxlinnix.swivel.data.bluetooth

/** Bluetooth broadcasts, reduced to the facts the app uses. Addresses are upper-case MACs. */
sealed interface BluetoothEvent {
    data class AdapterChanged(val enabled: Boolean) : BluetoothEvent
    data class BondChanged(val address: String, val name: String?, val bond: Bond, val previous: Bond) : BluetoothEvent
    data class LinkConnected(val address: String) : BluetoothEvent
    data class LinkDisconnected(val address: String) : BluetoothEvent

    /** The result of an SDP lookup: the service UUIDs the controller offers right now. */
    data class ServicesDiscovered(val address: String, val uuids: Set<String>) : BluetoothEvent
    data class DeviceFound(val address: String, val name: String?) : BluetoothEvent
    data object DiscoveryFinished : BluetoothEvent
}

enum class Bond { NONE, BONDING, BONDED }

/** A controller Android has a pairing for. */
data class PairedDevice(val address: String, val name: String?, val cachedUuids: Set<String>)
