/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.api.InternalLogger
import java.util.Locale

/**
 * Configuration for capturing HTTP request and response headers in RUM Resource events.
 *
 * Headers are captured from network requests and included in the resource event payload.
 * Headers matching a security pattern (containing sensitive data like authorization tokens,
 * cookies, secrets, or IP addresses) are automatically filtered out.
 *
 * @see Builder
 */
class ResourceHeadersConfiguration internal constructor(
    internal val requestHeaders: List<String>,
    internal val responseHeaders: List<String>
) {

    /**
     * Builder for [ResourceHeadersConfiguration].
     *
     * @param includeDefaults When `true` (default), the built configuration will include
     * [DEFAULT_REQUEST_HEADERS] and [DEFAULT_RESPONSE_HEADERS] in addition to any custom
     * headers specified via [captureHeaders]. When `false`, only custom headers are captured.
     */
    class Builder(private val includeDefaults: Boolean = true) {

        internal var logger: InternalLogger = InternalLogger.UNBOUND

        private var customHeaders: List<String> = emptyList()

        /**
         * Specifies additional header names to capture from both requests and responses.
         * Headers matching the [SECURITY_PATTERN] are silently filtered out.
         *
         * @param headers the list of header names to capture (case-insensitive).
         * @return this builder for chaining.
         */
        fun captureHeaders(headers: List<String>): Builder = apply {
            customHeaders += headers.map { it.lowercase(Locale.US) }
        }

        /**
         * Builds the [ResourceHeadersConfiguration].
         *
         * @return a new [ResourceHeadersConfiguration] instance.
         */
        fun build(): ResourceHeadersConfiguration {
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

            return ResourceHeadersConfiguration(
                requestHeaders = requestHeaders,
                responseHeaders = responseHeaders
            )
        }
    }

    companion object {

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

        internal const val NO_HEADERS_WARNING =
            "ResourceHeadersConfiguration was built with no headers to capture." +
                " Did you mean to use includeDefaults = true or call captureHeaders()?"

        internal const val SENSITIVE_HEADER_WARNING =
            "The following headers were requested but match the security pattern" +
                " and will not be captured: %s." +
                " See ResourceHeadersConfiguration.SECURITY_PATTERN for details."
    }
}
