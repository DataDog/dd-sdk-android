package com.datadog.android.core.internal.data.file

import java.io.File

internal fun File.readBytes(withPrefix: Char, withSuffix: Char): ByteArray =
    inputStream().use { input ->
        var offset = 1 // start from the prefix
        var remaining = this.length().also { length ->
            if (length > Int.MAX_VALUE) throw OutOfMemoryError(
                "File $this is too big ($length bytes) to fit in memory."
            )
        }.toInt()
        val result = ByteArray(remaining + 2)
        result[0]=withPrefix.toByte()
        while (remaining > 0) {
            val read = input.read(result, offset, remaining)
            if (read < 0) break
            remaining -= read
            offset += read
        }
        result[offset]=withSuffix.toByte()
        offset += 1 // adding the last character (suffix)
        if (remaining == 1) result else result.copyOf(offset)
    }
