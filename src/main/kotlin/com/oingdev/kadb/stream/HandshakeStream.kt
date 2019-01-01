package com.oingdev.kadb.stream

import com.oingdev.kadb.message.AdbKeyPair
import com.oingdev.kadb.message.ConnectMessage
import com.oingdev.kadb.message.Message
import java.util.*
import java.util.concurrent.LinkedBlockingQueue

@ExperimentalUnsignedTypes
class HandshakeStream(
        private val systemType: ConnectMessage.SystemType = ConnectMessage.SystemType.Host,
        val adbKeyPair: AdbKeyPair = AdbKeyPair.generateAdbKeyPair(),
        private val onAuthResult: (Boolean) -> Unit
) : Stream {
    override val localId: UInt = LOCAL_ID
    override val remoteId: UInt = REMOTE_ID
    override val destination: String = ""
    override val maxDataLength: Int = ConnectMessage.MAX_DATA_LENGTH_LEGACY
    @Volatile
    override var isClosed: Boolean = false
        private set

    private val outputMessageQueue = LinkedBlockingQueue<Message>()
    private val inputMessageQueue = LinkedBlockingQueue<Message>()

    override fun read(): Message? {
        return outputMessageQueue.poll()
    }

    override fun write(message: Message) {
        inputMessageQueue.put(message)
    }

    override fun run() {
        // send connect request
        var message: Message = ConnectMessage(systemType = systemType)
        outputMessageQueue.put(message)

        // handle response
        message = inputMessageQueue.take()
        if (message.command == Message.Command.Connect) {
            // Device accepts connection without authentication.
            onAuthResult(true)
            this.close()
            return
        }
        // handle auth request
        val authorized = handleAuthRequestType2() || handleAuthRequestType3()
        onAuthResult(authorized)
        this.close()
    }

    override fun close() {
        isClosed = true
    }

    override fun equals(other: Any?): Boolean {
        return Stream.streamEquals(this, other)
    }

    override fun hashCode(): Int {
        return Objects.hash(
                systemType.value, localId, remoteId,
                Objects.hashCode(adbKeyPair), destination, maxDataLength
        )
    }

    private fun handleAuthRequestType2(): Boolean {
        TODO()
    }

    private fun handleAuthRequestType3(): Boolean {
        TODO()
    }

    companion object {
        val LOCAL_ID = (-1).toUInt()
        val REMOTE_ID = (-1).toUInt()
    }
}
