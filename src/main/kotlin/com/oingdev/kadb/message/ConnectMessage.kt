package com.oingdev.kadb.message

@Suppress("MemberVisibilityCanBePrivate", "CanBeParameter")
@ExperimentalUnsignedTypes
class ConnectMessage(
        version: Int = CURRENT_VERSION,
        maxDataLength: Int = MAX_DATA_LENGTH_LEGACY,
        val systemType: SystemType = SystemType.Host,
        val serialNo: String = "",
        val banner: String = ""
) : Message(
        Command.Connect,
        version.toUInt(),
        maxDataLength.toUInt(),
        "${systemType.value}:$serialNo:$banner"
                .toByteArray(Charsets.US_ASCII).toUByteArray()
) {
    constructor(message: Message) : this(
            message.arg0.toInt(),
            message.arg1.toInt(),
            kotlin.run {
                val payload = message.payload!!.toByteArray()
                        .toString(Charsets.US_ASCII)
                        .split(":")
                        .toTypedArray()
                SystemType.fromValue(payload[0])
                        ?: throw MessageParseException("Unknown system type: ${payload[0]}")
            },
            kotlin.run {
                message.payload!!.toByteArray()
                        .toString(Charsets.US_ASCII)
                        .split(":")
                        .toTypedArray()[1]
            },
            kotlin.run {
                message.payload!!.toByteArray()
                        .toString(Charsets.US_ASCII)
                        .split(":")
                        .toTypedArray()[2]
            }
    )

    val version: UInt = super.arg0
    val maxDataLength: UInt = super.arg1

    companion object {
        const val CURRENT_VERSION: Int = 0x01_00_00_01
        const val MAX_DATA_LENGTH: Int = 256 * 1024
        const val MAX_DATA_LENGTH_LEGACY: Int = 4 * 1024
    }

    enum class SystemType(val value: String?) {
        BootLoader("bootloader"),
        Device("device"),
        Host("host");

        companion object {
            fun fromValue(value: String?): SystemType? {
                if (value == null) return null
                return SystemType.values().firstOrNull { value == it.value }
            }
        }
    }
}