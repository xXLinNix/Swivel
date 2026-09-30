package io.github.xxlinnix.swivel.core.virtualpad

/**
 * The Binder calls between Swivel and its virtual-pad service, which Shizuku runs as the
 * shell user so that it may open /dev/uhid. Both ends are Swivel's own code.
 */
object PadServiceContract {
    const val DESCRIPTOR = "io.github.xxlinnix.swivel.IVirtualPad"

    /** name: String → reply: error text, or null on success. */
    const val TX_OPEN = 1

    /** report: ByteArray. One-way. */
    const val TX_SEND = 2

    const val TX_CLOSE = 3

    /** Shizuku's reserved "destroy" call: IBinder.FIRST_CALL_TRANSACTION + 16777114. */
    const val TX_DESTROY = 16777115

    const val DEVICE_NAME = "Swivel virtual gamepad"
    const val PHYS = "swivel"
}
