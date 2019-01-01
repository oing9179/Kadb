package com.oingdev.kadb.stream

import com.oingdev.kadb.message.*
import com.oingdev.kadb.message.submessage.filesync.*
import com.oingdev.kadb.util.tryIgnoreException
import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@ExperimentalUnsignedTypes
class PushFileStream(
        override val localId: UInt,
        override val maxDataLength: Int,
        /**
         * File path on remote device
         */
        override val destination: String,
        /**
         * Bytes as stream you want to send to remote device
         */
        val inputStream: InputStream,
        /**
         * How many bytes you would like to transfer to remote device
         */
        val streamLength: Long,
        private val onProgress: ((transferred: Long, fullLength: Long) -> Unit)? = null,
        /**
         * Transfer success if `exception` is null, other wise failed.
         */
        private val onDone: ((exception: Exception?) -> Unit)? = null
) : Stream {
    override var remoteId: UInt = UInt.MIN_VALUE
        private set
    override var isClosed: Boolean = false
        private set

    private val inputMessageQueue = LinkedBlockingQueue<Message>()
    private val outputMessageQueue = LinkedBlockingQueue<Message>()

    override fun read(): Message? {
        return outputMessageQueue.poll()
    }

    override fun write(message: Message) {
        inputMessageQueue.put(message)
    }

    override fun close() {
        isClosed = true
    }

    private var exception: Exception? = null
    override fun run() {
        if (!startSyncService() || !sendSendMessage()) {
            close()
            tryIgnoreException {
                onDone?.invoke(StreamException("Failed to start transfer service"))
            }
            return
        }
        try {
            doTransfer()
        } catch (e: Exception) {
            exception = e
        }
        shutdownStream()
        close()
        tryIgnoreException { onDone?.invoke(exception) }
    }

    private fun startSyncService(): Boolean {
        var message: Message = OpenMessage(localId, "sync:\u0000")
        outputMessageQueue.put(message)
        message = inputMessageQueue.poll(5000, TimeUnit.MILLISECONDS)
        return if (message is OkMessage) {
            remoteId = message.localId
            true
        } else {
            false
        }
    }

    private fun sendSendMessage(): Boolean {
        var message: Message = FileSyncWriteMessage(localId, remoteId, SendSubMessage(destination))
        outputMessageQueue.put(message)
        message = inputMessageQueue.poll(5000, TimeUnit.MILLISECONDS)
        return message is OkMessage
    }

    private val messageOrderChecker = MessageOrderChecker()
    private val transformerStream = FileSyncSubMessageTransformerStream()
    private fun doTransfer() {
        var remaining = streamLength

        while (!isClosed && remaining > 0) {
            val inboundMessage = inputMessageQueue.poll()
            messageOrderChecker.check(inboundMessage, {
                return@check when (it) {
                    is OkMessage -> true
                    is WriteMessage -> {
                        if (inboundMessage is OkMessage) return@check true

                        var isWorked = false
                        if (inboundMessage is WriteMessage && inboundMessage.payloadLength > 0) {
                            val payload = inboundMessage.payload!!.copyOf()
                            transformerStream.write(payload.asByteArray())
                            var subMessage: FileSyncSubMessage? = transformerStream.get()
                            if (subMessage != null) isWorked = true
                            while (subMessage != null) {
                                if (subMessage is FailSubMessage) {
                                    val text = subMessage.payload
                                            ?.toByteArray()?.toString(Charsets.US_ASCII)
                                    throw StreamException("Failed: $text")
                                }
                                subMessage = transformerStream.get()
                            }
                        }
                        isWorked
                    }
                    // This is never going to happen
                    else -> throw StreamException("Unexpected message: $inboundMessage")
                }
            }, {
                outputMessageQueue.put(OkMessage(localId, remoteId))
                true
            }, {
                if (isClosed) return@check false

                val payload = when {
                    remaining > DEFAULT_PAYLOAD_LENGTH -> {
                        val payload = inputStream.readNBytes(DEFAULT_PAYLOAD_LENGTH)
                        remaining -= DEFAULT_PAYLOAD_LENGTH
                        payload
                    }
                    remaining > 0 -> {
                        val payload = inputStream.readNBytes(remaining.toInt())
                        remaining = 0
                        payload
                    }
                    else -> null // This is never going to happen
                }?.asUByteArray() ?: return@check false
                val message = FileSyncWriteMessage(localId, remoteId, DataSubMessage(payload))
                outputMessageQueue.put(message)
                tryIgnoreException { onProgress?.invoke(streamLength - remaining, streamLength) }
                true
            })
        }
    }

    private fun shutdownStream() {
        var message: Message = FileSyncWriteMessage(
                localId, remoteId,
                DoneSubMessage((System.currentTimeMillis() * 0.001).toInt())
        )
        outputMessageQueue.put(message)
        // Ignore ok message
        inputMessageQueue.poll(5000, TimeUnit.MILLISECONDS)
        // A write message indicates ok or fail
        for (i in IntRange(1, 5)) {
            inputMessageQueue.poll(5000, TimeUnit.MILLISECONDS).run {
                if (this == null || payloadLength == 0) return@run
                transformerStream.write(payload!!.toByteArray())
            }
            val subMessage = transformerStream.get()
            if (subMessage is FailSubMessage) {
                val text = subMessage.payload!!
                        .toByteArray().toString(Charsets.US_ASCII)
                exception = StreamException("Failed: $text")
            }
            if (subMessage != null) {
                outputMessageQueue.put(OkMessage(localId, remoteId))
                break
            }
        }
        message = FileSyncWriteMessage(localId, remoteId, QuitSubMessage())
        outputMessageQueue.put(message)
        // Ignore ok message
        inputMessageQueue.poll(5000, TimeUnit.MILLISECONDS)
        message = CloseMessage(localId, remoteId)
        outputMessageQueue.put(message)
    }

    companion object {
        private const val DEFAULT_PAYLOAD_LENGTH = 1024
    }
}