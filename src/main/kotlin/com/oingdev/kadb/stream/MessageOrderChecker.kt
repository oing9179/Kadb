package com.oingdev.kadb.stream

import com.oingdev.kadb.message.CloseMessage
import com.oingdev.kadb.message.Message
import com.oingdev.kadb.message.OkMessage
import com.oingdev.kadb.message.WriteMessage

@Suppress("EXPERIMENTAL_API_USAGE")
/**
 * According to [protocol.txt](https://android.googlesource.com/platform/system/core/+/master/adb/protocol.txt#157),
 *
 * 1. We have to wait for an Ok message before we send a Write message.
 * 2. We must send an Ok message after we got a Write message;
 * 3. Packets out of order may result stream closed unexpectedly.
 */
class MessageOrderChecker {
    @Volatile
    var isClosed: Boolean = false
        private set
    private var lastInboundMessage: Message? = null

    private var isLocalWaitingForOkResponse: Boolean = false
    private var haveToRespondRemoteWithOkMessage: Boolean = false
    private var isLocalAcceptWriteMessage: Boolean = true
    /**
     * Check if inbound or outbound message can be safely consumed,
     * safely = No packets out of order situations.
     *
     * @param inboundMessage
     * You have to pass a Write message for the first time you invoke this method,
     * next time will be an Ok message, then Write message, and so on.
     * @throws StreamException It will be one of the following reasons:
     * 1. Inbound message a CloseMessage
     * 2. This instance is closed and can not be used anymore
     * 3. Packets out of order
     */
    fun check(
            inboundMessage: Message?,
            doConsume: ((inboundMessage: Message) -> Boolean)?,
            doSendOk: (() -> Boolean)?,
            doSendWrite: (() -> Boolean)?
    ): Boolean {
        if (isClosed) throw StreamException("Closed")

        var isWorked = false
        // Check inbound message
        when {
            isLocalAcceptWriteMessage && inboundMessage is WriteMessage -> {
                if (doConsume?.invoke(inboundMessage) == true) {
                    isLocalAcceptWriteMessage = false
                    haveToRespondRemoteWithOkMessage = true
                    isWorked = true
                }
            }
            isLocalWaitingForOkResponse && inboundMessage is OkMessage -> {
                if (doConsume?.invoke(inboundMessage) == true) {
                    isLocalWaitingForOkResponse = false
                    isLocalAcceptWriteMessage = true
                    isWorked = true
                }
            }
            inboundMessage == null -> {
                // Invoker wants to send write message for the first time.
            }
            inboundMessage is CloseMessage -> {
                close()
                throw StreamException("Stream is going to close")
            }
            else -> {
                close()
                throw StreamException("Unexpected message: $inboundMessage")
            }
        }

        // Check and perform outbound behaviours
        if (haveToRespondRemoteWithOkMessage) {
            if (doSendOk?.invoke() == true) {
                haveToRespondRemoteWithOkMessage = false
                isLocalAcceptWriteMessage = true
                isWorked = true
            }
        }
        if (!isLocalWaitingForOkResponse && !haveToRespondRemoteWithOkMessage) {
            if (doSendWrite?.invoke() == true) {
                isLocalWaitingForOkResponse = true
                isWorked = true
            }
        }

        return isWorked
    }

    /**
     * Mark this instance as closed an can not be used anymore.
     */
    fun close() {
        this.isClosed = true
    }
}
