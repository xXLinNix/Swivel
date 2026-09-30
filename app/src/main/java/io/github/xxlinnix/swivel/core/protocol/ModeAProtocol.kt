package io.github.xxlinnix.swivel.core.protocol

/**
 * MOGA Mode A: a serial protocol over Bluetooth RFCOMM (docs/RESEARCH.md).
 *
 * The phone sends 5-byte commands starting 0x5A. The controller answers with 12- or
 * 14-byte reports starting 0x7A. Every frame's last byte is the XOR of all bytes before it.
 */
object ModeAProtocol {
    /** The Serial Port Profile UUID the controller listens on. */
    const val SERIAL_PORT_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    const val COMMAND_HEADER = 0x5A
    const val REPORT_HEADER = 0x7A
    const val COMMAND_LENGTH = 5

    const val SET_CONTROLLER_ID = 67

    /** Controller ids run from 1 to 4 and choose which player light is lit. */
    const val DEFAULT_CONTROLLER_ID = 1

    fun command(code: Int, controllerId: Int): ByteArray {
        val bytes = byteArrayOf(COMMAND_HEADER.toByte(), COMMAND_LENGTH.toByte(), code.toByte(), controllerId.toByte(), 0)
        bytes[4] = checksum(bytes, 4)
        return bytes
    }

    /** The XOR of the first [length] bytes. */
    fun checksum(bytes: ByteArray, length: Int): Byte {
        var sum = 0
        for (i in 0 until length) sum = sum xor (bytes[i].toInt() and 0xFF)
        return sum.toByte()
    }
}

/**
 * The two report formats. First-generation controllers (names starting "BD&A": the
 * MOGA Pocket and the original MOGA) send 12-byte reports with digital triggers. Later
 * controllers send 14 bytes with analog triggers.
 */
enum class ModeAGeneration(
    val pollCommand: Int,
    val listenCommand: Int,
    val pollReply: Int,
    val listenReply: Int,
    val reportLength: Int,
) {
    FIRST(pollCommand = 65, listenCommand = 68, pollReply = 97, listenReply = 100, reportLength = 12),
    SECOND(pollCommand = 69, listenCommand = 70, pollReply = 101, listenReply = 102, reportLength = 14),
    ;

    val other: ModeAGeneration get() = if (this == FIRST) SECOND else FIRST

    companion object {
        fun forReply(code: Int): ModeAGeneration? = entries.firstOrNull { code == it.pollReply || code == it.listenReply }
    }
}
