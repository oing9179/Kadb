package com.oingdev.kadb.message.submessage.filesync

import com.oingdev.kadb.message.MessageParseException
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import java.util.function.Supplier

@Suppress("EXPERIMENTAL_API_USAGE")
interface FileSyncSubMessage {
    val command: Command

    fun toUByteArray(): UByteArray

    enum class Command(val text: String) {
        StatV1("STAT"),
        @Deprecated("Not implemented.", level = DeprecationLevel.ERROR)
        StatV2("STA2"),
        Send("SEND"),
        Receive("RECV"),
        Data("DATA"),
        Done("DONE"),
        OK("OKAY"),
        Fail("FAIL"),
        Quit("QUIT");

        val bytes: UByteArray
            get() = text
                    .toByteArray(Charsets.US_ASCII)
                    .toUByteArray()

        companion object {
            fun from(bytes: UByteArray): Command? {
                return Command.values().firstOrNull {
                    bytes.contentEquals(it.bytes)
                }
            }

            fun from(text: String): Command? {
                return values().firstOrNull { text == it.text }
            }
        }
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
object FileSyncSubMessageFactory {
    fun fromBytes(bytes: UByteArray): FileSyncSubMessage {
        val commandBytes = bytes.asByteArray().sliceArray(IntRange(0, 3))
        val command = FileSyncSubMessage.Command.from(commandBytes.asUByteArray())
                ?: throw MessageParseException("Unknown command: ${Arrays.toString(commandBytes)}")
        return when (command) {
            FileSyncSubMessage.Command.StatV1 -> {
                StatV1ResponseSubMessage.fromUByteArray(bytes)
            }
            FileSyncSubMessage.Command.Send -> TODO()
            FileSyncSubMessage.Command.Receive -> TODO()
            FileSyncSubMessage.Command.Data -> TODO()
            FileSyncSubMessage.Command.Done -> TODO()
            FileSyncSubMessage.Command.OK -> {
                OkSubMessage.from(bytes)
            }
            FileSyncSubMessage.Command.Fail -> {
                FailSubMessage.from(bytes)
            }
            FileSyncSubMessage.Command.Quit -> {
                QuitSubMessage.from(bytes)
            }
            else -> throw MessageParseException("Unknown command: $command")
        }
    }

    fun read4bytesCommand(buffer: ByteBuffer): FileSyncSubMessage.Command? {
        val commandBytes = ByteArray(4)
        buffer.get(commandBytes)
        return FileSyncSubMessage.Command.from(commandBytes.asUByteArray())
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
class FileSyncSubMessageTransformerStream : OutputStream(), Supplier<FileSyncSubMessage?> {
    private val bytesList = LinkedList<Byte>()
    private var isClosed = false

    override fun write(b: Int) {
        if (isClosed) throw IOException("closed")
        bytesList.add(b.toByte())
    }

    override fun get(): FileSyncSubMessage? {
        if (bytesList.size < 4) return null
        val command = FileSyncSubMessage.Command.from(
                byteArrayOf(bytesList[0], bytesList[1], bytesList[2], bytesList[3])
                        .asUByteArray()
        ) ?: throw MessageParseException("Unknown sub message type")

        return when (command) {
            FileSyncSubMessage.Command.StatV1 -> {
                if (bytesList.size < StatV1ResponseSubMessage.MESSAGE_LENGTH) {
                    null
                } else {
                    // Skip 4 bytes command
                    repeat(4) { bytesList.pollFirst() }
                    val buffer = ByteBuffer.wrap(ByteArray(4 * 3) { bytesList.pollFirst() })
                            .order(ByteOrder.LITTLE_ENDIAN)
                    StatV1ResponseSubMessage(FileTypeAndMode(buffer.int), buffer.int, buffer.int)
                }
            }
            FileSyncSubMessage.Command.Send,
            FileSyncSubMessage.Command.Data,
            FileSyncSubMessage.Command.OK,
            FileSyncSubMessage.Command.Fail,
            FileSyncSubMessage.Command.Quit -> {
                when {
                    // Null if insufficient header length
                    bytesList.size < 8 -> null
                    // Null if insufficient payload length
                    kotlin.run {
                        val bytes = byteArrayOf(
                                bytesList[4], bytesList[5], bytesList[6], bytesList[7]
                        )
                        val payloadLength = ByteBuffer.wrap(bytes)
                                .order(ByteOrder.LITTLE_ENDIAN).int
                        8 + payloadLength > bytesList.size
                    } -> null
                    else -> {
                        // Skip 4 bytes command
                        repeat(4) { bytesList.pollFirst() }
                        val payloadLength = ByteBuffer.wrap(ByteArray(4) { bytesList.pollFirst() })
                                .order(ByteOrder.LITTLE_ENDIAN).int
                        val payload = if (payloadLength > 0) {
                            ByteArray(payloadLength) { bytesList.pollFirst() }
                        } else {
                            null
                        }
                        val messageBytes = ByteBuffer.allocate(4 + 4 + payloadLength)
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .put(command.bytes.toByteArray())
                                .putInt(payloadLength)
                                .run {
                                    if (payloadLength > 0) put(payload)
                                    this
                                }.array().toUByteArray()
                        FileSyncSubMessageFactory.fromBytes(messageBytes)
                    }
                }
            }
            else -> throw MessageParseException("Unhandled command: $command")
        }
    }

    override fun close() {
        isClosed = true
        bytesList.clear()
    }
}
