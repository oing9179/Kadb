package com.oingdev.kadb.message.submessage.filesync

import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("EXPERIMENTAL_API_USAGE")
class DataSubMessage(
        val payload: UByteArray?
) : FileSyncSubMessage {
    override val command: FileSyncSubMessage.Command = FileSyncSubMessage.Command.Data
    val payloadLength = payload?.size ?: 0

    override fun toUByteArray(): UByteArray {
        val buffer = ByteBuffer.allocate(4 + 4 + payloadLength)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(command.bytes.toByteArray())
                .putInt(payloadLength)
        if (payloadLength > 0) {
            buffer.put(payload!!.asByteArray())
        }
        return buffer.array().toUByteArray()
    }
}