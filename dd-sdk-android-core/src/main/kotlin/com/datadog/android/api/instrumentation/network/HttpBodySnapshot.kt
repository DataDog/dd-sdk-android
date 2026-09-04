/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */
package com.datadog.android.api.instrumentation.network

import java.nio.charset.Charset

/**
 * A size-capped copy of an HTTP request or response payload.
 *
 * Instances are produced by the [HttpRequestInfo.peekBody] and [HttpResponseInfo.peekBody]
 * extensions. Taking a snapshot never consumes the underlying payload: the request or response
 * stays fully readable by the application.
 *
 * Snapshot identity is intentionally retained instead of using data-class equality: Kotlin's
 * generated equality compares [ByteArray] properties by reference, which would imply misleading
 * value semantics for payloads.
 *
 * @property bytes the captured payload, holding at most the number of bytes requested from
 * [PeekableBodyInfo.peekBody]. A payload can be large, so this array is handed over as-is rather
 * than copied, both here and on every read. Treat it as read-only.
 * @property contentType the MIME type declared for the payload, including any `charset` parameter,
 * or null when the payload declared none.
 * @property isTruncated true when the payload was larger than the requested maximum and [bytes]
 * therefore only holds its beginning.
 */
class HttpBodySnapshot(
    val bytes: ByteArray,
    val contentType: String? = null,
    val isTruncated: Boolean = false
) {

    /**
     * Decodes [bytes] into a [String], using the charset declared in [contentType] and falling back
     * to UTF-8 when it declares none or declares one this device doesn't support.
     *
     * Note that when [isTruncated] is true the payload was cut at a byte boundary, so the last
     * character of the result may be garbled for multi-byte encodings.
     *
     * @return the decoded payload.
     */
    fun string(): String = String(bytes, resolveCharset(contentType))

    // The payload itself is deliberately left out: it may be large and may hold sensitive data.
    /** @inheritDoc */
    override fun toString(): String =
        "HttpBodySnapshot(size=${bytes.size}, contentType=$contentType, isTruncated=$isTruncated)"

    companion object {

        /**
         * The default and hard maximum number of bytes a body peek can capture: 512 KB.
         *
         * A snapshot is held in memory on top of the payload the networking library already holds,
         * so this cap keeps the duplication bounded for large downloads and uploads.
         */
        const val DEFAULT_MAX_BODY_BYTES: Long = 512L * 1024L

        private const val CHARSET_PARAMETER = "charset"

        private fun resolveCharset(contentType: String?): Charset {
            val charsetName = contentType?.charsetName() ?: return Charsets.UTF_8

            return try {
                @Suppress("UnsafeThirdPartyFunctionCall") // IllegalArgumentException is caught
                Charset.forName(charsetName)
            } catch (@Suppress("SwallowedException") _: IllegalArgumentException) {
                // covers both IllegalCharsetNameException and UnsupportedCharsetException
                Charsets.UTF_8
            }
        }

        private fun String.charsetName(): String? {
            var parameterStart = indexOf(';')
            while (parameterStart >= 0) {
                val nextParameter = indexOf(';', parameterStart + 1)
                val parameterEnd = if (nextParameter >= 0) nextParameter else length
                val nameStart = skipHttpWhitespace(parameterStart + 1, parameterEnd)
                val valueStart = charsetValueStart(nameStart, parameterEnd)
                if (valueStart != null) {
                    return charsetValue(valueStart, parameterEnd)
                }

                parameterStart = nextParameter
            }
            return null
        }

        @Suppress("UnsafeThirdPartyFunctionCall") // offsets are bounded by parameterEnd
        private fun String.charsetValueStart(nameStart: Int, parameterEnd: Int): Int? {
            if (!regionMatches(
                    nameStart,
                    CHARSET_PARAMETER,
                    0,
                    CHARSET_PARAMETER.length,
                    ignoreCase = true
                )
            ) {
                return null
            }

            val delimiter = skipHttpWhitespace(
                nameStart + CHARSET_PARAMETER.length,
                parameterEnd
            )
            return if (delimiter < parameterEnd && this[delimiter] == '=') delimiter + 1 else null
        }

        private fun String.charsetValue(start: Int, parameterEnd: Int): String {
            var valueStart = skipHttpWhitespace(start, parameterEnd)
            var valueEnd = skipHttpWhitespaceBackwards(valueStart, parameterEnd)
            if (valueEnd - valueStart >= 2 &&
                this[valueStart] == '"' &&
                this[valueEnd - 1] == '"'
            ) {
                valueStart++
                valueEnd--
            }

            @Suppress("UnsafeThirdPartyFunctionCall") // indexes are bounded by the string length
            return substring(valueStart, valueEnd)
        }

        private fun String.skipHttpWhitespace(start: Int, end: Int): Int {
            var index = start
            while (index < end && this[index].isHttpWhitespace()) index++
            return index
        }

        private fun String.skipHttpWhitespaceBackwards(start: Int, end: Int): Int {
            var index = end
            while (index > start && this[index - 1].isHttpWhitespace()) index--
            return index
        }

        private fun Char.isHttpWhitespace(): Boolean = this == ' ' || this == '\t'
    }
}
