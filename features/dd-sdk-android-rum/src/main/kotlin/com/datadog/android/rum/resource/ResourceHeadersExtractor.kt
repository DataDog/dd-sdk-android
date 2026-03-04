/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.resource

import com.datadog.android.api.InternalLogger
import com.datadog.android.lint.InternalApi
import com.datadog.android.rum.internal.utils.truncateToUtf8ByteSize
import com.datadog.android.rum.resource.ResourceHeadersExtractor.Companion.SECURITY_PATTERN
import java.util.Locale

/**
 * Extracts allowed HTTP headers from network requests and responses.
 *
 *  Headers are captured from network requests and included in the resource event payload.
 *  Headers matching the [SECURITY_PATTERN] are automatically filtered out.
 *
 *  @see Builder
 */
class ResourceHeadersExtractor private constructor(
    internal val requestHeaders: List<String>,
    internal val responseHeaders: List<String>
) {

    /**
     * Builder for [ResourceHeadersExtractor].
     *
     * @param includeDefaults When `true` (default), the built extractor will include
     * [DEFAULT_REQUEST_HEADERS] and [DEFAULT_RESPONSE_HEADERS] in addition to any custom
     * headers specified via [captureHeaders]. When `false`, only custom headers are captured.
     */
    class Builder(private val includeDefaults: Boolean = true) {

        private val logger = InternalLogger.UNBOUND
        private val customHeaders = mutableListOf<String>()

        /**
         * Specifies additional header names to capture from both requests and responses.
         * Headers matching the [SECURITY_PATTERN] are silently filtered out.
         *
         * @param headers the header names to capture (case-insensitive).
         * @return this builder for chaining.
         */
        fun captureHeaders(vararg headers: String): Builder = apply {
            customHeaders.addAll(headers.map { it.lowercase(Locale.US) })
        }

        /**
         * Builds the [ResourceHeadersExtractor].
         *
         * @return a new [ResourceHeadersExtractor] instance.
         */
        fun build(): ResourceHeadersExtractor {
            val (sensitiveRequested, filteredCustom) = customHeaders.partition(SECURITY_PATTERN::containsMatchIn)

            if (sensitiveRequested.isNotEmpty()) {
                logger.log(
                    level = InternalLogger.Level.WARN,
                    target = InternalLogger.Target.USER,
                    messageBuilder = {
                        SENSITIVE_HEADER_WARNING.format(Locale.US, sensitiveRequested.joinToString(", "))
                    }
                )
            }

            val requestHeaders = if (includeDefaults) {
                (DEFAULT_REQUEST_HEADERS + filteredCustom).distinct()
            } else {
                filteredCustom.distinct()
            }

            val responseHeaders = if (includeDefaults) {
                (DEFAULT_RESPONSE_HEADERS + filteredCustom).distinct()
            } else {
                filteredCustom.distinct()
            }

            if (requestHeaders.isEmpty() && responseHeaders.isEmpty()) {
                logger.log(
                    level = InternalLogger.Level.WARN,
                    target = InternalLogger.Target.USER,
                    messageBuilder = { NO_HEADERS_WARNING }
                )
            }

            return ResourceHeadersExtractor(
                requestHeaders = requestHeaders,
                responseHeaders = responseHeaders
            )
        }
    }

    /**
     * Extracts the allowed request headers from the given [headers] map.
     * @param headers the raw request headers (key to list of values).
     * @param internalLogger logger for debug messages.
     * @return a map of allowed header names to their joined values.
     */
    @InternalApi
    fun extractRequestHeaders(
        headers: Map<String, List<String>>,
        internalLogger: InternalLogger
    ): Map<String, String> {
        return extractHeaders(headers, requestHeaders, internalLogger)
    }

    /**
     * Extracts the allowed response headers from the given [headers] map.
     * @param headers the raw response headers (key to list of values).
     * @param internalLogger logger for debug messages.
     * @return a map of allowed header names to their joined values.
     */
    @InternalApi
    fun extractResponseHeaders(
        headers: Map<String, List<String>>,
        internalLogger: InternalLogger
    ): Map<String, String> {
        return extractHeaders(headers, responseHeaders, internalLogger)
    }

    private fun extractHeaders(
        headers: Map<String, List<String>>,
        allowedHeaders: List<String>,
        internalLogger: InternalLogger
    ): Map<String, String> {
        val normalizedHeaders = headers.mapKeys { it.key.lowercase(Locale.US) }
        val result = mutableMapOf<String, String>()
        var currentSize = 0

        for (headerName in allowedHeaders) {
            if (result.size >= MAX_HEADERS_COUNT) break

            normalizedHeaders[headerName]?.let { values ->
                val (value, valueByteSize) = values.joinToString(", ")
                    .truncateToUtf8ByteSize(MAX_HEADER_VALUE_BYTES)
                val entrySize = headerName.length + 1 + valueByteSize

                if (currentSize + entrySize > HEADER_SIZE_LIMIT_BYTES) {
                    internalLogger.log(
                        InternalLogger.Level.DEBUG,
                        InternalLogger.Target.USER,
                        {
                            "Skipping header '$headerName': " +
                                "adding it would exceed the $HEADER_SIZE_LIMIT_BYTES byte limit."
                        }
                    )
                } else {
                    result[headerName] = value
                    currentSize += entrySize
                }
            }
        }

        return result
    }

    internal companion object {

        /**
         * Default request headers captured when `includeDefaults` is `true`.
         */
        val DEFAULT_REQUEST_HEADERS: List<String> = listOf(
            "cache-control",
            "content-type"
        )

        /**
         * Default response headers captured when `includeDefaults` is `true`.
         */
        val DEFAULT_RESPONSE_HEADERS: List<String> = listOf(
            "cache-control",
            "etag",
            "age",
            "expires",
            "content-type",
            "content-encoding",
            "vary",
            "content-length",
            "server-timing",
            "x-cache"
        )

        /**
         * Regex pattern used to detect sensitive headers. Any header name matching this
         * pattern is automatically filtered out, even if explicitly requested.
         * Catches headers related to authentication, cookies, tokens, secrets, credentials,
         * API keys, and client IP addresses.
         */
        val SECURITY_PATTERN: Regex = Regex(
            pattern = "(token|cookie|secret|authorization|password|credential|bearer" +
                "|(api|secret|access|app).?key|forwarded|real.?ip|connecting.?ip|client.?ip)",
            option = RegexOption.IGNORE_CASE
        )

        /**
         * Maximum total size in bytes for all captured headers (names + values) per
         * request or response direction.
         */
        const val HEADER_SIZE_LIMIT_BYTES: Int = 2048

        /** Maximum size in bytes for a single header value. Values exceeding this are truncated. */
        const val MAX_HEADER_VALUE_BYTES = 128

        /** Maximum number of headers captured per request or response direction. */
        const val MAX_HEADERS_COUNT = 100

        const val NO_HEADERS_WARNING =
            "ResourceHeadersExtractor was built with no headers to capture." +
                " Did you mean to use includeDefaults = true or call captureHeaders()?"

        const val SENSITIVE_HEADER_WARNING =
            "The following headers were requested but match the security pattern" +
                " and will not be captured: %s." +
                " See ResourceHeadersExtractor.SECURITY_PATTERN for details."
    }
}
