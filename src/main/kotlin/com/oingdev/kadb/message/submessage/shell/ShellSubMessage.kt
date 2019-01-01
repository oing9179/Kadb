package com.oingdev.kadb.message.submessage.shell

import com.oingdev.kadb.message.MessageParseException
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.function.Supplier

@Suppress("EXPERIMENTAL_API_USAGE")
class ShellSubMessage(
        val type: Type,
        val payload: UByteArray?
) {
    val payloadLength: Int = payload?.size ?: 0

    fun toUByteArray(): UByteArray {
        return toUByteArray(this)
    }

    override fun toString(): String {
        return "ShellSubMessage{${type.value}, $payloadLength}"
    }

    enum class Type(val value: Byte) {
        StdIn(0x00),
        StdOut(0x01),
        StdErr(0x02),
        Exit(0x03),
        CloseStdIn(0x04),
        WindowSizeChange(0x05),
        Invalid(0xff.toByte());

        companion object {
            fun fromByte(byte: Byte): Type? {
                return Type.values().firstOrNull { byte == it.value }
            }
        }
    }

    companion object {
        // Header: 1 byte type + 4 bytes length
        const val HEADER_LENGTH = 1 + 4

        fun toUByteArray(message: ShellSubMessage): UByteArray {
            val buffer = ByteBuffer.allocate(HEADER_LENGTH + message.payloadLength)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.put(message.type.value)
            buffer.putInt(message.payloadLength)
            if (message.payloadLength > 0) {
                buffer.put(message.payload!!.toByteArray())
            }
            return buffer.array().toUByteArray()
        }
    }
}

class ShellSubMessageTransformerStream : OutputStream(), Supplier<ShellSubMessage?> {
    private val bytesList = LinkedList<Byte>()
    private var isClosed = false

    override fun write(b: Int) {
        if (isClosed) throw IOException("closed")
        bytesList.add(b.toByte())
    }

    override fun get(): ShellSubMessage? {
        if (bytesList.size < 5) return null
        val type = ShellSubMessage.Type.fromByte(bytesList[0])
                ?: throw MessageParseException("Unknown sub message type: ${bytesList[0]}")
        val payloadLength = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(bytesList[1])
                .put(bytesList[2])
                .put(bytesList[3])
                .put(bytesList[4])
                .flip().int
        if ((ShellSubMessage.HEADER_LENGTH + payloadLength) > bytesList.size) return null
        // Skip first 5 bytes
        repeat(5) { bytesList.pollFirst() }
        @Suppress("EXPERIMENTAL_API_USAGE")
        val payloadBytes = UByteArray(payloadLength) {
            bytesList.pollFirst().toUByte()
        }
        return ShellSubMessage(type, payloadBytes)
    }

    override fun close() {
        isClosed = true
        bytesList.clear()
    }
}
