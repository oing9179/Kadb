package com.oingdev.kadb.util

import java.nio.ByteBuffer

fun Int.toByteArray(): ByteArray {
    return ByteBuffer.allocate(4)
            .putInt(this)
            .array()
}

fun ByteArray.toInt(): Int {
    return ByteBuffer.wrap(this).int
}

@ExperimentalUnsignedTypes
fun UByteArray.toUInt(): UInt {
    return ByteBuffer.allocate(4)
            .put(this.toByteArray())
            .flip()
            .int
            .toUInt()
}

@ExperimentalUnsignedTypes
fun UInt.toUByteArray(): UByteArray {
    return this.toInt().toByteArray().toUByteArray()
}
