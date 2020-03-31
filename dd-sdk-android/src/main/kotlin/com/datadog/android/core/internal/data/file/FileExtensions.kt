/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.file

import com.datadog.android.core.internal.utils.sdkLogger
import java.io.File

internal fun File.readBytes(withPrefix: CharSequence, withSuffix: CharSequence): ByteArray =
    inputStream().use { input ->
        val length = this.length()
        if (length > Int.MAX_VALUE) {
            sdkLogger.i("We could not read the file $this because it was too big to fit in memory")
            return ByteArray(0)
        }
        var offset = withPrefix.length // start from the prefix
        var remaining = length.toInt()
        val result = ByteArray(remaining + withPrefix.length + withSuffix.length)
        for (i in 0 until offset) {
            result[i] = withPrefix[i].toByte()
        }
        while (remaining > 0) {
            val read = input.read(result, offset, remaining)
            if (read < 0) break
            remaining -= read
            offset += read
        }
        for (j in 0 until withSuffix.length) {
            result[j + offset] = withSuffix[j].toByte()
        }
        offset += withSuffix.length // adding the last character (suffix)
        if (remaining == 0) result else result.copyOf(offset)
    }
