package com.oingdev.kadb.connection

import com.oingdev.kadb.message.ConnectMessage
import com.oingdev.kadb.message.Message
import com.oingdev.kadb.message.MessageFactory
import com.oingdev.kadb.stream.HandshakeStream
import com.oingdev.kadb.stream.Stream
import com.oingdev.kadb.util.closeQuietly
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger

@ExperimentalUnsignedTypes
class TcpConnection : Connection {
    constructor(address: String, port: Int = 5555) {
        connectionState = Connection.State.Connecting
        socket = Socket(address, port)
        connectionState = Connection.State.Connected
    }

    constructor(socket: Socket) {
        connectionState = Connection.State.Connecting
        this.socket = socket
        connectionState = Connection.State.Connected
    }

    private val socket: Socket
    override val remoteAddress: String
        get() = (socket.remoteSocketAddress as InetSocketAddress)
                .address.hostAddress
    override val remotePort: Int
        get() = socket.port
    @Volatile
    override var connectionState: Connection.State = Connection.State.Connecting
        private set
    override var flush: Boolean = true

    private val streamThreads = HashMap<UInt, StreamThread>()
    @Volatile
    override var maxDataLength: Int = ConnectMessage.MAX_DATA_LENGTH_LEGACY
        private set
    private val generatedStreamLocalId: AtomicInteger = AtomicInteger()

    private fun read(): Message? {
        return MessageFactory.from(socket.getInputStream())
    }

    private fun write(message: Message, flush: Boolean = false) {
        socket.getOutputStream().apply {
            this.write(message.toUByteArray().toByteArray())
            if (flush) flush()
        }
    }

    override fun generateStreamLocalId(): Int {
        return generatedStreamLocalId.incrementAndGet()
    }

    override fun connectStream(stream: Stream) {
        val thread = StreamThread(stream)
        thread.start()
        synchronized(streamThreads) {
            streamThreads.put(thread.stream.localId, thread)
        }
    }

    override fun disconnectStream(stream: Stream) {
        synchronized(streamThreads) {
            val localId = stream.localId
            val streamThread = streamThreads[localId]
            if (streamThread != null) streamThreads.remove(localId)
        }
    }

    override fun close() {
        connectionState = Connection.State.Disconnecting
        streamThreads.forEach { it.value.stream.closeQuietly() }
        socket.closeQuietly()
        connectionState = Connection.State.Disconnected
    }

    @Deprecated("This method should be called by super class only.", level = DeprecationLevel.HIDDEN)
    override fun run() {
        var canLoop = !Thread.currentThread().isInterrupted
                && connectionState != Connection.State.Disconnected
        while (canLoop) {
            if (!socket.isConnected) {
                Thread.sleep(50)
                continue
            }
            try {
                val processedIn = processInboundMessage()
                val processedOut = processOutboundMessage()
                if (!processedIn && !processedOut) Thread.sleep(10)
                // clearClosedStreams()
            } catch (ignore: InterruptedException) {
            } catch (e: Exception) {
                e.printStackTrace()
            }
            canLoop = !Thread.currentThread().isInterrupted
                    && connectionState != Connection.State.Disconnected
        }
    }

    private fun processInboundMessage(): Boolean {
        if (socket.getInputStream().available() < Message.HEADER_LENGTH) {
            return false
        }

        val message = read() ?: return false
        val filteredStreams = if (!isConnOrAuthCommand(message.command)) {
            streamThreads.filter { it.value.stream !is HandshakeStream }
        } else {
            streamThreads.filter { it.value.stream is HandshakeStream }
        }
        filteredStreams.forEach { it.value.stream.write(message) }
        return filteredStreams.isNotEmpty()
    }

    private fun processOutboundMessage(): Boolean {
        var processed = false
        streamThreads.forEach {
            val message = it.value.stream.read() ?: return@forEach
            processed = true
            write(message, this.flush)
        }
        return processed
    }

    private fun clearClosedStreams() {
        streamThreads.filter { it.value.stream.isClosed }.forEach { streamThreads.remove(it.key) }
    }

    companion object {
        private fun isConnOrAuthCommand(command: Message.Command): Boolean {
            return command == Message.Command.Connect ||
                    command == Message.Command.Auth
        }
    }
}

@ExperimentalUnsignedTypes
private class StreamThread(val stream: Stream) :
        Thread(stream, "${stream::class.java.simpleName}Thread")