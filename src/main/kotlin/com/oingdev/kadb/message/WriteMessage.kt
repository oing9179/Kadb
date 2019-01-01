package com.oingdev.kadb.message

@Suppress("EXPERIMENTAL_API_USAGE", "MemberVisibilityCanBePrivate", "CanBeParameter")
open class WriteMessage(val localId: UInt, val remoteId: UInt, payload: UByteArray?) :
        Message(Command.Write, localId, remoteId, payload) {

    constructor(message: Message) : this(message.arg0, message.arg1, message.payload)
}
