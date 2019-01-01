package com.oingdev.kadb.message

@Suppress("EXPERIMENTAL_API_USAGE", "MemberVisibilityCanBePrivate", "CanBeParameter")
class CloseMessage(val localId: UInt, val remoteId: UInt) :
        Message(Command.Close, localId, remoteId, null) {

    constructor(message: Message) : this(message.arg0, message.arg1)
}
