package com.oingdev.kadb.stream

import com.oingdev.kadb.message.Message
import com.oingdev.kadb.util.KadbException
import java.io.Closeable
import java.util.*

@ExperimentalUnsignedTypes
interface Stream : Runnable, Closeable, AutoCloseable {
    val localId: UInt
    val remoteId: UInt
    /**
     * A `destination` with parameter(s) must ends with `\u0000`.
     */
    val destination: String
    /**
     * The largest length of message's payload.
     */
    val maxDataLength: Int
    val isClosed: Boolean

    fun read(): Message?
    fun write(message: Message)

    companion object {
        fun streamEquals(one: Stream, other: Any?): Boolean {
            if (other == null || other !is Stream) return false
            return one.localId == other.localId &&
                    one.remoteId == other.remoteId &&
                    one.destination == other.destination
        }

        fun streamHashcode(stream: Stream): Int {
            return Objects.hash(stream.localId, stream.remoteId, stream.destination)
        }
    }
}

class StreamException(message: String? = null, cause: Throwable? = null) : KadbException(message, cause)
