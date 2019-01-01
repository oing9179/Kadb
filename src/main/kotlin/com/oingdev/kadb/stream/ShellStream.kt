package com.oingdev.kadb.stream

import com.oingdev.kadb.message.*
import com.oingdev.kadb.message.submessage.shell.ShellSubMessage
import com.oingdev.kadb.message.submessage.shell.ShellSubMessageTransformerStream
import com.oingdev.kadb.message.submessage.shell.ShellWriteMessage
import com.oingdev.kadb.util.closeQuietly
import com.oingdev.kadb.util.io.NonBlockingInputStream
import com.oingdev.kadb.util.tryIgnoreException
import java.io.InputStream
import java.io.OutputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.concurrent.LinkedBlockingQueue

@ExperimentalUnsignedTypes
class ShellStream(
        override val localId: UInt,
        override val maxDataLength: Int
) : Stream {
    override var remoteId: UInt = UInt.MIN_VALUE
        private set
    /**
     * `destination` should be `null` byte terminated.
     */
    override val destination: String = "shell,v2:\u0000"

    @Volatile
    override var isClosed: Boolean = false
        private set
    var exitCode: UInt = 0.toUInt()
        private set
    private val outputMessageQueue = LinkedBlockingQueue<Message>()
    private val inputMessageQueue = LinkedBlockingQueue<Message>()

    @Suppress("MemberVisibilityCanBePrivate")
    // input stream in consumer's perspective.
    val inputStream: InputStream
    @Suppress("MemberVisibilityCanBePrivate")
    // output stream in consumer's perspective.
    val outputStream: OutputStream
    private val internalInputStream: InputStream
    private val internalOutputStream: OutputStream

    init {
        val pipedIn = PipedInputStream()
        inputStream = pipedIn
        internalOutputStream = PipedOutputStream(pipedIn)

        val pipedOut = PipedOutputStream()
        outputStream = pipedOut
        internalInputStream = NonBlockingInputStream(PipedInputStream(pipedOut))
    }

    override fun read(): Message? {
        return outputMessageQueue.poll()
    }

    override fun write(message: Message) {
        inputMessageQueue.put(message)
    }

    override fun close() {
        tryIgnoreException { internalOutputStream.write(-1)/*EOF*/ }
        internalInputStream.closeQuietly()
        internalOutputStream.closeQuietly()
        inputStream.closeQuietly()
        outputStream.closeQuietly()
        isClosed = true
    }

    override fun run() {
        // Open stream
        var message: Message? = OpenMessage(localId.toUInt(), destination)
        outputMessageQueue.put(message)
        message = inputMessageQueue.take()
        if (message !is OkMessage) {
            close()
            throw StreamException("Stream disconnected unexpectedly")
        }
        remoteId = message.localId
        // Stream opened

        while (!Thread.currentThread().isInterrupted && !isClosed) {
            message = inputMessageQueue.poll()
            if (message is CloseMessage) {
                // Remote is asking us to close stream
                break
            }
            if (!processMessageQueue(message)) Thread.sleep(10)
        }

        // Close streams
        close()
        // Respond with Close message
        outputMessageQueue.put(CloseMessage(localId, remoteId))
        while (outputMessageQueue.size > 0) {
            // Wait until Close message sent
            Thread.sleep(10)
        }
        /**
         * Remote will sends us a Close message and it can be safely ignored,
         * all messages from queue is ignored from now.
         */
        Thread.interrupted()
    }

    private val messageOrderChecker = MessageOrderChecker()
    private val transformerStream = ShellSubMessageTransformerStream()
    private fun processMessageQueue(inboundMessage: Message?): Boolean {
        return messageOrderChecker.check(inboundMessage, {
            if (inboundMessage is OkMessage) return@check true

            var isWorked = false
            if (inboundMessage is WriteMessage && inboundMessage.payloadLength > 0) {
                val payload = inboundMessage.payload!!.copyOf()
                transformerStream.write(payload.asByteArray())
                var subMessage: ShellSubMessage? = transformerStream.get()
                if (subMessage != null) isWorked = true
                while (subMessage != null) {
                    processShellSubmessage(subMessage)
                    subMessage = transformerStream.get()
                }
            }
            isWorked
        }, {
            outputMessageQueue.put(OkMessage(localId, remoteId))
            true
        }, {
            if (isClosed) return@check false

            val ttyOutputBytes = internalInputStream.readNBytes(OUTBOUND_MESSAGE_PAYLOAD_LENGTH)
            if (ttyOutputBytes.isEmpty()) return@check false
            val message = ShellWriteMessage(
                    localId, remoteId,
                    ShellSubMessage(ShellSubMessage.Type.StdIn, ttyOutputBytes.toUByteArray())
            )
            outputMessageQueue.put(message)
            true
        })
    }

    private fun processShellSubmessage(subMessage: ShellSubMessage) {
        when (subMessage.type) {
            ShellSubMessage.Type.StdOut,
            ShellSubMessage.Type.StdErr -> {
                if (!isClosed) {
                    internalOutputStream.run {
                        write(subMessage.payload!!.toByteArray())
                        flush()
                    }
                }
            }
            ShellSubMessage.Type.WindowSizeChange -> {
                // Ignored
            }
            ShellSubMessage.Type.Exit -> {
                exitCode = if (subMessage.payloadLength == 1) {
                    subMessage.payload!![0].toUInt()
                } else {
                    0xff.toByte().toUInt()
                }
            }
            ShellSubMessage.Type.CloseStdIn -> {
                close()
            }
            else -> {
                throw StreamException("Unhandled sub message: $subMessage")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        return Stream.streamEquals(this, other)
    }

    override fun hashCode(): Int {
        return Stream.streamHashcode(this)
    }

    companion object {
        private const val OUTBOUND_MESSAGE_PAYLOAD_LENGTH = 1024
    }
}
