package com.oingdev.kadb.message.submessage.filesync

import com.oingdev.kadb.message.WriteMessage

@Suppress("EXPERIMENTAL_API_USAGE")
class FileSyncWriteMessage : WriteMessage {
    constructor(localId: UInt, remoteId: UInt, subMessage: FileSyncSubMessage)
            : super(localId, remoteId, subMessage.toUByteArray())

    constructor(message: WriteMessage) : super(message)
}