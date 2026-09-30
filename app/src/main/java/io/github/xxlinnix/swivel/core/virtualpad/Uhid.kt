package io.github.xxlinnix.swivel.core.virtualpad

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Messages for the Linux kernel's /dev/uhid, which creates HID devices from user space.
 * The layout is `struct uhid_event` from <linux/uhid.h>: a 32-bit type, then the request.
 * Android devices are little-endian. Each message is written with one write() call.
 */
object Uhid {
    const val DEVICE_PATH = "/dev/uhid"

    const val UHID_DESTROY = 1
    const val UHID_CREATE2 = 11
    const val UHID_INPUT2 = 12

    const val BUS_VIRTUAL = 0x06

    private const val NAME_SIZE = 128
    private const val PHYS_SIZE = 64
    private const val UNIQ_SIZE = 64

    /** The fixed part of UHID_CREATE2 before the descriptor bytes. */
    const val CREATE2_HEADER_SIZE = 4 + NAME_SIZE + PHYS_SIZE + UNIQ_SIZE + 2 + 2 + 4 + 4 + 4 + 4

    /** HID_MAX_DESCRIPTOR_SIZE and UHID_DATA_MAX in the kernel. */
    const val MAX_DATA = 4096

    fun create2(
        name: String,
        phys: String,
        uniq: String,
        descriptor: ByteArray,
        bus: Int,
        vendor: Int,
        product: Int,
    ): ByteArray {
        require(descriptor.size <= MAX_DATA)
        val buffer = ByteBuffer.allocate(CREATE2_HEADER_SIZE + descriptor.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(UHID_CREATE2)
        putString(buffer, name, NAME_SIZE)
        putString(buffer, phys, PHYS_SIZE)
        putString(buffer, uniq, UNIQ_SIZE)
        buffer.putShort(descriptor.size.toShort())
        buffer.putShort(bus.toShort())
        buffer.putInt(vendor)
        buffer.putInt(product)
        buffer.putInt(0) // version
        buffer.putInt(0) // country
        buffer.put(descriptor)
        return buffer.array()
    }

    fun input2(report: ByteArray): ByteArray {
        require(report.size <= MAX_DATA)
        val buffer = ByteBuffer.allocate(4 + 2 + report.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(UHID_INPUT2)
        buffer.putShort(report.size.toShort())
        buffer.put(report)
        return buffer.array()
    }

    fun destroy(): ByteArray = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(UHID_DESTROY).array()

    /** A NUL-terminated string in a fixed-size field; too-long text is cut short. */
    private fun putString(buffer: ByteBuffer, text: String, size: Int) {
        val bytes = text.toByteArray(Charsets.UTF_8).copyOf(minOf(text.toByteArray(Charsets.UTF_8).size, size - 1))
        val start = buffer.position()
        buffer.put(bytes)
        buffer.position(start + size)
    }
}
