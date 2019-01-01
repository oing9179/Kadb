package com.oingdev.kadb.message

@Suppress("EXPERIMENTAL_API_USAGE", "MemberVisibilityCanBePrivate", "CanBeParameter")
class OpenMessage(
        val localId: UInt,
        val destination: String
) : Message(
        Command.Open, localId, 0.toUInt(),
        destination.toByteArray(Charsets.US_ASCII).asUByteArray()
)