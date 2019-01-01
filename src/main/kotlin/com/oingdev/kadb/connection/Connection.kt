package com.oingdev.kadb.connection

import com.oingdev.kadb.stream.Stream
import java.io.Closeable

@ExperimentalUnsignedTypes
interface Connection : Runnable, Closeable, AutoCloseable {
    val remoteAddress: String
    val remotePort: Int
    val connectionState: State
    var flush: Boolean
    val maxDataLength: Int

    fun generateStreamLocalId(): Int
    fun connectStream(stream: Stream)
    fun disconnectStream(stream: Stream)

    enum class State {
        Connecting,
        Connected,
        Disconnecting,
        Disconnected
    }
}