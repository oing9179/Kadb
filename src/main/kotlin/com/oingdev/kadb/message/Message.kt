package com.oingdev.kadb.message

import com.oingdev.kadb.util.*
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*

@ExperimentalUnsignedTypes
open class Message constructor(
        val command: Command,
        val arg0: UInt,
        val arg1: UInt,
        val payload: UByteArray?
) {
    val payloadLength = payload?.size ?: 0
    /**
     * [This document](https://android.googlesource.com/platform/system/core/+/master/adb/protocol.txt)
     * says checksum is `crc32(payload)`, but it is not actually, so fuck you Google.
     */
    val payloadChecksum: UInt = kotlin.run {
        if (payloadLength == 0) {
            return@run UInt.MIN_VALUE
        }

        var checksum = 0
        for (b in payload!!.toByteArray()) {
            checksum += b
        }
        checksum.toUInt()
    }
    private val magic = command.value.xor(0xffffffff.toUInt())

    fun toUByteArray(): UByteArray {
        val buffer = ByteBuffer.allocate(HEADER_LENGTH + payloadLength).apply {
            this.order(ByteOrder.LITTLE_ENDIAN)
            // command should writen in big-endian, fuck you Google for document inconsistency.
            this.putInt(command.value.convertEndian().toInt())
            this.putInt(arg0.toInt())
            this.putInt(arg1.toUInt().toInt())
            if (payload != null) {
                this.putInt(payloadLength)
                this.putInt(payloadChecksum.toInt())
            } else {
                this.putInt(0)
                this.putInt(0)
            }
            this.put(magic.toUByteArray().toByteArray())
            if (payload != null) this.put(payload.toByteArray())
        }
        return buffer.array().toUByteArray()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is Message) return false
        return command == other.command &&
                arg0 == other.arg0 &&
                arg1 == other.arg1 &&
                payloadLength == other.payloadLength &&
                payloadChecksum == other.payloadChecksum
    }

    override fun hashCode(): Int {
        return Objects.hash(command.value, arg0, arg1, payloadLength, payloadChecksum)
    }

    override fun toString(): String {
        val commandText = command.text
        val arg0Hex = arg0.toString(16)
        val arg1Hex = arg1.toString(16)
        val len = payloadLength
        return "Message{$commandText, arg0=$arg0Hex, arg1=$arg1Hex, length=$len}"
    }

    companion object {
        /**
         * The header consists of 6 unsigned int.
         */
        const val HEADER_LENGTH = 24

        fun parse(bytes: UByteArray): Message {
            val inputStream = ByteArrayInputStream(bytes.toByteArray())
            val buffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)

            // Parse command
            inputStream.readNBytes(4).forEach { buffer.put(it) }
            buffer.flip()
            val commandUInt = buffer.array().toUByteArray().toUInt()
            val command = Command.forUInt(commandUInt) ?: throw MessageParseException(
                    "Unknown command: ${Arrays.toString(commandUInt.toInt().toByteArray())}"
            )
            buffer.clear()

            // Parse arg0
            inputStream.readNBytes(4).forEach { buffer.put(it) }
            buffer.flip()
            val arg0 = buffer.int.toUInt()
            buffer.clear()

            // Parse arg1
            inputStream.readNBytes(4).forEach { buffer.put(it) }
            buffer.flip()
            val arg1 = buffer.int.toUInt()
            buffer.clear()

            // Parse payloadLength
            inputStream.readNBytes(4).forEach { buffer.put(it) }
            buffer.flip()
            val payloadLength = buffer.int.toUInt()
            buffer.clear()

            // Parse payloadChecksum
            inputStream.readNBytes(4).forEach { buffer.put(it) }
            buffer.flip()
            val payloadChecksum = buffer.int.toUInt()
            buffer.clear()

            // Skip magic
            inputStream.skip(4)

            // Read payload
            val payload = inputStream.readNBytes(payloadLength.toInt())
            if (payloadLength != payload.size.toUInt()) {
                throw MessageParseException(
                        "Payload length mismatch, expect=$payloadLength, got=${payload.size}"
                )
            }
            inputStream.closeQuietly()

            val message = Message(command, arg0, arg1, payload.toUByteArray())
            if (payloadChecksum != message.payloadChecksum) {
                // Ignore checksum mismatch, adb did it, we do it too.
            }
            return message
        }
    }

    enum class Command(val text: String) {
        Connect("CNXN"),
        Auth("AUTH"),
        @Deprecated("No longer used.")
        Sync("SYNC"),
        Open("OPEN"),
        Write("WRTE"),
        OK("OKAY"),
        Close("CLSE");

        private val bytes: UByteArray = text
                .toByteArray(Charsets.US_ASCII)
                .toUByteArray()

        val value: UInt = ByteBuffer.allocate(4).run {
            bytes.forEach { this.put(it.toByte()) }
            this.flip().int.toUInt()
        }

        companion object {
            fun forUInt(value: UInt): Command? {
                return Command.values().firstOrNull { value == it.value }
            }
        }
    }
}

class MessageParseException(message: String) : KadbException(message)
