@file:Suppress("EXPERIMENTAL_API_USAGE")

package com.oingdev.kadb.message.submessage.filesync

import com.oingdev.kadb.message.MessageParseException
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OkSubMessage(val payload: UByteArray?) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.OK
    val payloadLength = payload?.size ?: 0

    override fun toUByteArray(): UByteArray = subMessageToUByteArray(command, payload)

    companion object {
        fun from(bytes: UByteArray): OkSubMessage {
            val buffer = ByteBuffer.wrap(bytes.toByteArray())
                    .order(ByteOrder.LITTLE_ENDIAN)
            val command = FileSyncSubMessageFactory.read4bytesCommand(buffer)
            if (command != FileSyncSubMessage.Command.OK)
                throw MessageParseException("This command $command is not ${FileSyncSubMessage.Command.OK}")
            val payloadLength = buffer.int
            return if (payloadLength == 0) {
                OkSubMessage(null)
            } else {
                val payload = ByteArray(payloadLength)
                buffer.get(payload)
                OkSubMessage(payload.toUByteArray())
            }
        }
    }
}

class FailSubMessage(val payload: UByteArray?) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.Fail
    val payloadLength = payload?.size ?: 0

    override fun toUByteArray(): UByteArray = subMessageToUByteArray(command, payload)

    companion object {
        fun from(bytes: UByteArray): FailSubMessage {
            val buffer = ByteBuffer.wrap(bytes.toByteArray())
                    .order(ByteOrder.LITTLE_ENDIAN)
            val command = FileSyncSubMessageFactory.read4bytesCommand(buffer)
            if (command != FileSyncSubMessage.Command.Fail)
                throw MessageParseException("This command $command is not ${FileSyncSubMessage.Command.Fail}")
            val payloadLength = buffer.int
            return if (payloadLength == 0) {
                FailSubMessage(null)
            } else {
                val payload = ByteArray(payloadLength)
                buffer.get(payload)
                FailSubMessage(payload.toUByteArray())
            }
        }
    }
}

class DoneSubMessage(val timestamp: Int) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.Done

    override fun toUByteArray(): UByteArray {
        return ByteBuffer.allocate(4 + 4)
                .put(command.bytes.toByteArray())
                .putInt(timestamp)
                .array().toUByteArray()
    }
}

class QuitSubMessage(val payload: UByteArray? = null) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.Quit
    val payloadLength = payload?.size ?: 0

    override fun toUByteArray(): UByteArray = subMessageToUByteArray(command, payload)

    companion object {
        fun from(bytes: UByteArray): QuitSubMessage {
            val buffer = ByteBuffer.wrap(bytes.toByteArray())
                    .order(ByteOrder.LITTLE_ENDIAN)
            val command = FileSyncSubMessageFactory.read4bytesCommand(buffer)
            if (command != FileSyncSubMessage.Command.Quit)
                throw MessageParseException("This command $command is not ${FileSyncSubMessage.Command.Quit}")
            val payloadLength = buffer.int
            return if (payloadLength == 0) {
                QuitSubMessage(null)
            } else {
                val payload = ByteArray(payloadLength)
                buffer.get(payload)
                QuitSubMessage(payload.toUByteArray())
            }
        }
    }
}

private fun subMessageToUByteArray(
        command: FileSyncSubMessage.Command, payload: UByteArray?
): UByteArray {
    val payloadLength = payload?.size ?: 0
    val buffer = ByteBuffer.allocate(4 + 4 + payloadLength)
            .order(ByteOrder.LITTLE_ENDIAN)
            .put(command.bytes.toByteArray())
            .putInt(payloadLength)
    if (payloadLength > 0) {
        buffer.put(payload!!.toByteArray())
    }
    return buffer.array().toUByteArray()
}
