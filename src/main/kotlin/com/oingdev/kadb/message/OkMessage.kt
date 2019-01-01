package com.oingdev.kadb.message

@Suppress("EXPERIMENTAL_API_USAGE", "MemberVisibilityCanBePrivate", "CanBeParameter")
class OkMessage(val localId: UInt, val remoteId: UInt) :
        Message(Command.OK, localId, remoteId, null) {

    constructor(message: Message) : this(message.arg0, message.arg1)
}