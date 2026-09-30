package io.github.xxlinnix.swivel.core.protocol

/** Builds a controller report with a correct checksum, for tests. */
internal fun report(code: Int, vararg payload: Int, controllerId: Int = 1): ByteArray {
    val length = 4 + payload.size + 1
    val bytes = ByteArray(length)
    bytes[0] = 0x7A
    bytes[1] = length.toByte()
    bytes[2] = code.toByte()
    bytes[3] = controllerId.toByte()
    payload.forEachIndexed { i, value -> bytes[4 + i] = value.toByte() }
    var sum = 0
    for (i in 0 until length - 1) sum = sum xor (bytes[i].toInt() and 0xFF)
    bytes[length - 1] = sum.toByte()
    return bytes
}

/** A first-generation report: buttons, pad, four stick bytes, power. */
internal fun firstGen(buttons: Int = 0, pad: Int = 0, lx: Int = 0, ly: Int = 0, rx: Int = 0, ry: Int = 0, power: Int = 0x10, code: Int = 100) =
    report(code, buttons, pad, lx, ly, rx, ry, power)

/** A second-generation report: as first generation, plus two analog triggers. */
internal fun secondGen(buttons: Int = 0, pad: Int = 0, lx: Int = 0, ly: Int = 0, rx: Int = 0, ry: Int = 0, l2: Int = 0, r2: Int = 0, power: Int = 0x10, code: Int = 102) =
    report(code, buttons, pad, lx, ly, rx, ry, l2, r2, power)
