package com.oingdev.kadb.message.submessage.filesync

import com.oingdev.kadb.message.MessageParseException
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Suppress("EXPERIMENTAL_API_USAGE")
class StatV1RequestSubMessage(
        /**
         * File or directory path on remote device, should be less than or equal to 1024 chars.
         */
        val path: String
) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.StatV1

    override fun toUByteArray(): UByteArray {
        // message structure: 4 bytes command + 4 bytes path length + path as bytes
        return ByteBuffer.allocate(4 + 4 + path.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(command.bytes.toByteArray())
                .putInt(path.length)
                .put(path.toByteArray(Charsets.US_ASCII))
                .array().toUByteArray()
    }
}

@Suppress("EXPERIMENTAL_API_USAGE")
class StatV1ResponseSubMessage(
        val mode: FileTypeAndMode,
        /**
         * File size in bytes
         */
        val size: Int,
        /**
         * Timestamp in seconds
         */
        val timestamp: Int
) : FileSyncSubMessage {
    override val command = FileSyncSubMessage.Command.StatV1

    override fun toUByteArray(): UByteArray {
        return ByteBuffer.allocate(MESSAGE_LENGTH)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(command.bytes.toByteArray())
                .putInt(mode.value)
                .putInt(size)
                .putInt(timestamp)
                .array().toUByteArray()
    }

    companion object {
        // length = 4 bytes command + 4 bytes mode + 4 bytes size + 4 bytes timestamp
        const val MESSAGE_LENGTH = 4 + 4 + 4 + 4

        fun fromUByteArray(bytes: UByteArray): StatV1ResponseSubMessage {
            val buffer = ByteBuffer.wrap(bytes.asByteArray())
            val command = FileSyncSubMessageFactory.read4bytesCommand(buffer)
            if (command != FileSyncSubMessage.Command.StatV1)
                throw MessageParseException("This command $command is not ${FileSyncSubMessage.Command.StatV1}")
            return StatV1ResponseSubMessage(FileTypeAndMode(buffer.int), buffer.int, buffer.int)
        }
    }
}

class FileTypeAndMode(val value: Int) {
    val isSocket: Boolean = value.and(Type.Socket.mask) == 1
    val isSymbolicLink: Boolean = value.and(Type.SymbolicLink.mask) == 1
    val isRegularFile: Boolean = value.and(Type.RegularFile.mask) == 1
    val isBlockDevice: Boolean = value.and(Type.BlockDevice.mask) == 1
    val isDirectory: Boolean = value.and(Type.Directory.mask) == 1
    val isCharacterDevice: Boolean = value.and(Type.CharacterDevice.mask) == 1
    val isFIFO: Boolean = value.and(Type.FIFO.mask) == 1

    val isSetUID: Boolean = value.and(Type.SetGID.mask) == 1
    val isSetGID: Boolean = value.and(Type.SetUID.mask) == 1
    val isSticky: Boolean = value.and(Type.Sticky.mask) == 1

    val ownerRead: Boolean = value.and(Mode.OwnerRead.mask) == 1
    val ownerWrite: Boolean = value.and(Mode.OwnerWrite.mask) == 1
    val ownerExec: Boolean = value.and(Mode.OwnerExec.mask) == 1
    val groupRead: Boolean = value.and(Mode.GroupRead.mask) == 1
    val groupWrite: Boolean = value.and(Mode.GroupWrite.mask) == 1
    val groupExec: Boolean = value.and(Mode.GroupExec.mask) == 1
    val othersRead: Boolean = value.and(Mode.OthersRead.mask) == 1
    val othersWrite: Boolean = value.and(Mode.OthersWrite.mask) == 1
    val othersExec: Boolean = value.and(Mode.OthersExec.mask) == 1

    enum class Type(val mask: Int) {
        Socket(Integer.parseInt("140000", 8)),
        SymbolicLink(Integer.parseInt("120000", 8)),
        RegularFile(Integer.parseInt("100000", 8)),
        BlockDevice(Integer.parseInt("060000", 8)),
        Directory(Integer.parseInt("040000", 8)),
        CharacterDevice(Integer.parseInt("020000", 8)),
        FIFO(Integer.parseInt("010000", 8)),

        SetUID(Integer.parseInt("004000", 8)),
        SetGID(Integer.parseInt("002000", 8)),
        Sticky(Integer.parseInt("001000", 8))
    }

    enum class Mode(val mask: Int) {
        OwnerRead(Integer.parseInt("400", 8)),
        OwnerWrite(Integer.parseInt("200", 8)),
        OwnerExec(Integer.parseInt("100", 8)),

        GroupRead(Integer.parseInt("040", 8)),
        GroupWrite(Integer.parseInt("020", 8)),
        GroupExec(Integer.parseInt("010", 8)),

        OthersRead(Integer.parseInt("004", 8)),
        OthersWrite(Integer.parseInt("002", 8)),
        OthersExec(Integer.parseInt("001", 8))
    }

    class Builder {
        private var mode = 0

        /*
         * File type
         */

        fun isSocket(): Builder {
            mode = mode.xor(Type.Socket.mask)
            return this
        }

        fun isSymbolicLink(): Builder {
            mode = mode.xor(Type.SymbolicLink.mask)
            return this
        }

        fun isRegularFile(): Builder {
            mode = mode.xor(Type.RegularFile.mask)
            return this
        }

        fun isBlockDevice(): Builder {
            mode = mode.xor(Type.BlockDevice.mask)
            return this
        }

        fun isDirectory(): Builder {
            mode = mode.xor(Type.Directory.mask)
            return this
        }

        fun isCharacterDevice(): Builder {
            mode = mode.xor(Type.CharacterDevice.mask)
            return this
        }

        fun isFIFO(): Builder {
            mode = mode.xor(Type.FIFO.mask)
            return this
        }

        fun isSetUID(): Builder {
            mode = mode.xor(Type.SetUID.mask)
            return this
        }

        fun isSetGID(): Builder {
            mode = mode.xor(Type.SetGID.mask)
            return this
        }

        fun isSticky(): Builder {
            mode = mode.xor(Type.Sticky.mask)
            return this
        }

        /*
         * File modes
         */

        fun ownerRead(): Builder {
            mode = mode.xor(Mode.OwnerRead.mask)
            return this
        }

        fun ownerWrite(): Builder {
            mode = mode.xor(Mode.OwnerWrite.mask)
            return this
        }

        fun ownerExec(): Builder {
            mode = mode.xor(Mode.OwnerExec.mask)
            return this
        }

        fun groupRead(): Builder {
            mode = mode.xor(Mode.GroupRead.mask)
            return this
        }

        fun groupWrite(): Builder {
            mode = mode.xor(Mode.GroupWrite.mask)
            return this
        }

        fun groupExec(): Builder {
            mode = mode.xor(Mode.GroupExec.mask)
            return this
        }

        fun othersRead(): Builder {
            mode = mode.xor(Mode.OthersRead.mask)
            return this
        }

        fun othersWrite(): Builder {
            mode = mode.xor(Mode.OthersWrite.mask)
            return this
        }

        fun othersExec(): Builder {
            mode = mode.xor(Mode.OthersExec.mask)
            return this
        }

        fun build() = FileTypeAndMode(mode)
    }
}
