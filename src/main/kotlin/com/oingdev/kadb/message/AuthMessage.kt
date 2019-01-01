package com.oingdev.kadb.message

@ExperimentalUnsignedTypes
class AuthMessage private constructor() :
        Message(Command.Auth, 0.toUInt(), 0.toUInt(), null) {
    // TODO: Class implementation.

    companion object {
        enum class AuthType(val value: Int) {
            Token(1),
            Signature(2),
            RsaPublicKey(3);

            companion object {
                fun forValue(value: Int): AuthType? {
                    return AuthType.values().firstOrNull { value == it.value }
                }
            }
        }
    }
}