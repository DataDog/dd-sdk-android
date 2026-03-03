/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.utils

import com.datadog.android.api.InternalLogger
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CoderMalfunctionError
import java.nio.charset.StandardCharsets

/**
 * Truncates this string so that its UTF-8 encoded form does not exceed [maxBytes] bytes.
 * Uses a [java.nio.charset.CharsetEncoder] to safely avoid splitting multibyte characters.
 *
 * @param maxBytes the maximum number of UTF-8 bytes allowed.
 * @param internalLogger optional logger for reporting encoding failures.
 *
 * @return a [Pair] of the (possibly truncated) string and its UTF-8 byte size.
 */
@Suppress("ReturnCount", "SwallowedException")
internal fun String.truncateToUtf8ByteSize(
    maxBytes: Int,
    internalLogger: InternalLogger? = null
): Pair<String, Int> {
    val encoder =
        // will not throw UnsupportedOperationException
        @Suppress("UnsafeThirdPartyFunctionCall")
        StandardCharsets.UTF_8.newEncoder()

    // will not throw IllegalArgumentException
    @Suppress("UnsafeThirdPartyFunctionCall")
    val dst = ByteBuffer.allocate(maxBytes)

    // will not throw IndexOutOfBoundsException
    @Suppress("UnsafeThirdPartyFunctionCall")
    val src = CharBuffer.wrap(this)

    @Suppress("TooGenericExceptionCaught")
    try {
        // Encode as much as fits. The encoder will not consume a character
        // if doing so would overflow the byte buffer.
        encoder.encode(src, dst, true)
    } catch (e: IllegalStateException) {
        logTruncationFailure(internalLogger, e)
        return Pair("", 0)
    } catch (e: CoderMalfunctionError) {
        logTruncationFailure(internalLogger, e)
        return Pair("", 0)
    } catch (e: NullPointerException) {
        logTruncationFailure(internalLogger, e)
        return Pair("", 0)
    }

    // will not throw IndexOutOfBoundsException
    @Suppress("UnsafeThirdPartyFunctionCall")
    val truncated = substring(0, src.position())
    return Pair(truncated, dst.position())
}

private fun logTruncationFailure(internalLogger: InternalLogger?, e: Throwable) {
    internalLogger?.log(
        level = InternalLogger.Level.ERROR,
        target = InternalLogger.Target.MAINTAINER,
        messageBuilder = { "Failed to truncate string to UTF-8 byte limit" },
        throwable = e
    )
}
