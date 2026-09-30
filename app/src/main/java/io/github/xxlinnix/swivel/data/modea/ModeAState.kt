package io.github.xxlinnix.swivel.data.modea

import io.github.xxlinnix.swivel.core.protocol.ModeAGeneration

/** Where the Mode A link is. Only one controller is linked at a time. */
sealed interface ModeAState {
    data object Idle : ModeAState

    data class Connecting(val address: String, val name: String?, val attempt: Int) : ModeAState

    data class Connected(val address: String, val name: String?, val generation: ModeAGeneration) : ModeAState

    /** The link dropped or could not be made. Another attempt follows after [retryInMs]. */
    data class Waiting(val address: String, val name: String?, val attempt: Int, val retryInMs: Long) : ModeAState

    /** The link gave up. The user has to connect again, or the controller has to reconnect itself. */
    data class Stopped(val address: String, val name: String?, val reason: StopReason) : ModeAState
}

enum class StopReason {
    /** The controller stayed unreachable for the whole reconnect window: asleep or off. */
    UNREACHABLE,

    /** A socket opened, but nothing answered in the MOGA protocol: probably not in Mode A. */
    NO_ANSWER,

    /** Bluetooth is off or the Nearby devices permission was taken away. */
    BLUETOOTH_UNAVAILABLE,
}

val ModeAState.address: String?
    get() = when (this) {
        ModeAState.Idle -> null
        is ModeAState.Connecting -> address
        is ModeAState.Connected -> address
        is ModeAState.Waiting -> address
        is ModeAState.Stopped -> address
    }
