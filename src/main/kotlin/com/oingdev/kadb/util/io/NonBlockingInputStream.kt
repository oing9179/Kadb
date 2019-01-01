package com.oingdev.kadb.util.io

import com.oingdev.kadb.util.closeQuietly
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class NonBlockingInputStream(
        private val inputStream: InputStream,
        bufferSize: Int = DEFAULT_BUFFER_SIZE,
        threadName: String = "NonBlockingInputStreamThread-${sequenceGenerator.incrementAndGet()}"
) : InputStream() {
    private val bufferQueue = LinkedBlockingQueue<Byte>(bufferSize)
    private var exception: IOException? = null
    private val thread = Thread(Runnable {
        try {
            this.run()
        } catch (ignore: InterruptedException) {
        }
    }, threadName)

    init {
        thread.start()
    }

    override fun read(): Int {
        return bufferQueue.take().toInt()
    }

    fun read(timeout: Long): Byte? {
        return bufferQueue.poll(timeout, TimeUnit.MILLISECONDS)
    }

    override fun read(b: ByteArray): Int {
        return read(b, DEFAULT_READ_TIMEOUT)
    }

    fun read(b: ByteArray, timeout: Long): Int {
        val bytes = readNBytes(b.size, timeout)
        bytes.forEachIndexed { index, byte -> b[index] = byte }
        return bytes.size
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        return read(b, off, len, DEFAULT_READ_TIMEOUT)
    }

    fun read(b: ByteArray, off: Int, len: Int, timeout: Long): Int {
        return readNBytes(b, off, len, timeout)
    }

    override fun readNBytes(len: Int): ByteArray {
        return readNBytes(len, DEFAULT_READ_TIMEOUT)
    }

    fun readNBytes(len: Int, timeout: Long): ByteArray {
        val buffer = ByteBuffer.allocate(len)
        var byte: Byte?
        do {
            byte = read(timeout)
            if (byte != null) {
                buffer.put(byte)
            }
        } while (byte != null)
        val bytes = ByteArray(buffer.flip().remaining())
        buffer.get(bytes)
        return bytes
    }

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int {
        return readNBytes(b, off, len, DEFAULT_READ_TIMEOUT)
    }

    fun readNBytes(b: ByteArray, off: Int, len: Int, timeout: Long): Int {
        val bytes = readNBytes(len, timeout)
        bytes.forEachIndexed { index, byte -> b[off + index] = byte }
        return bytes.size
    }

    private fun run() {
        while (!Thread.currentThread().isInterrupted) {
            val byte = try {
                inputStream.read().toByte()
            } catch (e: IOException) {
                exception = e
                (-1).toByte()
            }
            bufferQueue.put(byte)
            if (byte == EOF) {
                Thread.currentThread().interrupt()
                break
            }
        }
        inputStream.closeQuietly()
    }

    override fun close() {
        thread.interrupt()
        super.close()
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 8192
        private const val EOF = (-1).toByte()
        const val DEFAULT_READ_TIMEOUT = 10L
        private val sequenceGenerator: AtomicInteger = AtomicInteger()
    }
}