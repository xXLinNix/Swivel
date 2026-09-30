package io.github.xxlinnix.swivel.core.protocol

/** One checksummed report from the controller, header and checksum included. */
class ModeAFrame(val bytes: ByteArray) {
    val code: Int get() = bytes[2].toInt() and 0xFF
    val controllerId: Int get() = bytes[3].toInt() and 0xFF

    fun hex(): String = bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xFF) }
}

/**
 * Cuts the byte stream from the socket into frames.
 *
 * Bluetooth delivers the stream in arbitrary pieces, so a frame can arrive split or
 * glued to the next one. After noise or a checksum error, the parser drops one byte and
 * looks for the next 0x7A, so a single bad byte costs at most one report.
 */
class ModeAFrameParser {
    private val buffer = ByteArray(BUFFER_SIZE)
    private var size = 0

    var checksumErrors = 0
        private set
    var skippedBytes = 0
        private set

    fun feed(data: ByteArray, offset: Int = 0, length: Int = data.size): List<ModeAFrame> {
        var read = offset
        val end = offset + length
        val frames = mutableListOf<ModeAFrame>()
        while (read < end) {
            val count = minOf(end - read, BUFFER_SIZE - size)
            data.copyInto(buffer, size, read, read + count)
            size += count
            read += count
            extract(frames)
            if (size == BUFFER_SIZE) drop(1) // Cannot happen with a sane stream: frames are short.
        }
        return frames
    }

    private fun extract(frames: MutableList<ModeAFrame>) {
        while (size > 0) {
            if ((buffer[0].toInt() and 0xFF) != ModeAProtocol.REPORT_HEADER) {
                drop(1)
                continue
            }
            if (size < 2) return
            val length = buffer[1].toInt() and 0xFF
            if (length < MIN_FRAME || length > MAX_FRAME) {
                drop(1)
                continue
            }
            if (size < length) return
            if (ModeAProtocol.checksum(buffer, length - 1) == buffer[length - 1]) {
                frames += ModeAFrame(buffer.copyOf(length))
                removeFront(length)
            } else {
                checksumErrors++
                drop(1)
            }
        }
    }

    private fun drop(count: Int) {
        skippedBytes += count
        removeFront(count)
    }

    private fun removeFront(count: Int) {
        buffer.copyInto(buffer, 0, count, size)
        size -= count
    }

    private companion object {
        const val BUFFER_SIZE = 256
        const val MIN_FRAME = 5
        const val MAX_FRAME = 32
    }
}
