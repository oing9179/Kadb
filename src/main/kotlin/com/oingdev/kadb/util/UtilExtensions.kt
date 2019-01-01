package com.oingdev.kadb.util

import java.io.Closeable

fun Closeable?.closeQuietly() {
    if (this == null) return
    try {
        this.close()
    } catch (ignore: Exception) {
    }
}

fun <T> tryIgnoreException(block: () -> T?): T? {
    return try {
        block()
    } catch (ignore: Exception) {
        return null
    }
}
