package com.oingdev.kadb.message

import com.oingdev.kadb.message.Message.Companion.HEADER_LENGTH
import com.oingdev.kadb.util.KadbException
import com.oingdev.kadb.util.convertEndian
import com.oingdev.kadb.util.toUInt
import java.io.InputStream

@ExperimentalUnsignedTypes
object MessageFactory {
    private val mapCommandToClass = hashMapOf(
            Pair(Message.Command.Connect, ConnectMessage::class.java),
            Pair(Message.Command.Auth, AuthMessage::class.java),
            Pair(Message.Command.Open, OpenMessage::class.java),
            Pair(Message.Command.Write, WriteMessage::class.java),
            Pair(Message.Command.OK, OkMessage::class.java),
            Pair(Message.Command.Close, CloseMessage::class.java)
    )

    fun from(inputStream: InputStream): Message? {
        val messageBytes = try {
            readMessageBytes(inputStream)
        } catch (ignore: InterruptedException) {
            null
        }
        if (messageBytes.isNullOrEmpty()) return null

        val message = Message.parse(messageBytes)
        val clazz = mapCommandToClass[message.command]
                ?: throw MessageParseException("There is no class for \"${message.command.text}\" command.")
        try {
            return clazz.getDeclaredConstructor(Message::class.java)
                    .newInstance(message)
        } catch (e: Exception) {
            throw KadbException("Can not create instance for command \"${message.command.text}\"", e)
        }
    }

    private fun readMessageBytes(inputStream: InputStream): UByteArray {
        /**
         * The [InputStream.readNBytes] will return in one of the three circumstances:
         *
         * 1. Return given length of bytes.
         * 2. Return 0 bytes which means there is no data to read for now.
         * 3. Return less bytes than given length which means end of stream.
         */

        val headerBytes = inputStream.readNBytes(HEADER_LENGTH).toUByteArray()
        if (headerBytes.size < HEADER_LENGTH) return ubyteArrayOf()
        val payloadLength: Int = UByteArray(4) { headerBytes[3 * 4 + it] }
                .toUInt()
                .convertEndian()
                .toInt()
        val payloadBytes = inputStream.readNBytes(payloadLength).asUByteArray()
        if (payloadBytes.size != payloadLength) {
            throw MessageParseException("Payload length mismatch")
            // This also indicates that the connection is closed.
        }
        return ubyteArrayOf(*headerBytes, *payloadBytes)
    }
}