package com.datadog.android.core.internal.data.file

import com.datadog.android.log.internal.utils.sdkLogger
import java.io.File

internal fun File.readBytes(withPrefix: Char, withSuffix: Char): ByteArray =
    inputStream().use { input ->
        val length = this.length()
        if (length > Int.MAX_VALUE) {
            sdkLogger.i("We could not read the file $this because it was too big to fit in memory")
            return@use ByteArray(0)
        }
        var offset = 1 // start from the prefix
        var remaining = length.toInt()
        val result = ByteArray(remaining + 2)
        result[0] = withPrefix.toByte()
        while (remaining > 0) {
            val read = input.read(result, offset, remaining)
            if (read < 0) break
            remaining -= read
            offset += read
        }
        result[offset] = withSuffix.toByte()
        offset += 1 // adding the last character (suffix)
        if (remaining == 1) result else result.copyOf(offset)
    }
