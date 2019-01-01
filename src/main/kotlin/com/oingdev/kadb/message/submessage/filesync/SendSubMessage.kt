package com.oingdev.kadb.message.submessage.filesync

import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("EXPERIMENTAL_API_USAGE")
class SendSubMessage(
        /**
         * File path on remote device
         */
        val path: String,
        val fileTypeAndMode: FileTypeAndMode = FileTypeAndMode.Builder()
                .isRegularFile()
                .ownerRead()
                .ownerWrite()
                .groupRead()
                .othersRead()
                .build()
) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.Send

    override fun toUByteArray(): UByteArray {
        // message structure: 4 bytes command + 4 bytes path.length + path as bytes
        val payload = "$path,${fileTypeAndMode.value}"
        return ByteBuffer.allocate(4 + 4 + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(command.bytes.toByteArray())
                .putInt(payload.length)
                .put(payload.toByteArray(Charsets.US_ASCII))
                .array().toUByteArray()
    }
}