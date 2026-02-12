/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.configuration.ResourceHeadersConfiguration
import java.util.Locale

/**
 * Extracts allowed HTTP headers from network requests and responses
 * based on [ResourceHeadersConfiguration].
 */
// TODO RUM-14784 mark this class as internal when trackResourceHeaders moves to
//  RumNetworkInstrumentation and remove the @Suppress("PackageNameVisibility") annotation
@Suppress("PackageNameVisibility")
class ResourceHeadersExtractor(private val configuration: ResourceHeadersConfiguration) {

    /**
     * Extracts the allowed request headers from the given [headers] map.
     * @param headers the raw request headers (key to list of values).
     * @param internalLogger logger for debug messages.
     * @return a map of allowed header names to their joined values.
     */
    fun extractRequestHeaders(
        headers: Map<String, List<String>>,
        internalLogger: InternalLogger
    ): Map<String, String> {
        return extractHeaders(headers, configuration.requestHeaders, internalLogger)
    }

    /**
     * Extracts the allowed response headers from the given [headers] map.
     * @param headers the raw response headers (key to list of values).
     * @param internalLogger logger for debug messages.
     * @return a map of allowed header names to their joined values.
     */
    fun extractResponseHeaders(
        headers: Map<String, List<String>>,
        internalLogger: InternalLogger
    ): Map<String, String> {
        return extractHeaders(headers, configuration.responseHeaders, internalLogger)
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
                val value = truncateToByteLimit(values.joinToString(", "), MAX_HEADER_VALUE_BYTES)
                val entrySize = headerName.length + 1 + value.toByteArray(Charsets.UTF_8).size

                if (currentSize + entrySize > ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES) {
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

    companion object {
        private const val MAX_HEADER_VALUE_BYTES = 128
        private const val HEADER_SIZE_LIMIT_BYTES = ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES
        private const val MAX_HEADERS_COUNT = 100

        internal fun truncateToByteLimit(value: String, maxBytes: Int): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            if (bytes.size <= maxBytes) return value
            // Decode back to string to avoid splitting multibyte characters
            return String(bytes, 0, maxBytes, Charsets.UTF_8).trimEnd('\uFFFD')
        }
    }
}
