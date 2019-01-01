package com.oingdev.kadb.util

import java.nio.ByteBuffer
import java.nio.ByteOrder

@ExperimentalUnsignedTypes
fun UInt.convertEndian(): UInt {
    return ByteBuffer.allocate(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putInt(this.toInt())
            .array()
            .toUByteArray()
            .toUInt()
}

@ExperimentalUnsignedTypes
fun Int.convertEndian(): Int {
    return this.toUInt().convertEndian().toInt()
}
