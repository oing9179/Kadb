package com.oingdev.kadb.message.submessage.shell

import com.oingdev.kadb.message.WriteMessage

@Suppress("EXPERIMENTAL_API_USAGE")
class ShellWriteMessage : WriteMessage {
    constructor(localId: UInt, remoteId: UInt, subMessage: ShellSubMessage)
            : super(localId, remoteId, subMessage.toUByteArray())

    constructor(message: WriteMessage) : super(message)
}