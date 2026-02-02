/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.configuration

import com.datadog.android.core.internal.net.HttpSpec

/**
 * Configuration for capturing HTTP headers from network requests/responses
 * and adding them to RUM resource events.
 *
 * ## Overview
 * When header tracking is enabled, captured headers appear in RUM Explorer as:
 * - `@resource.request.headers.<header_name>` for request headers
 * - `@resource.response.headers.<header_name>` for response headers
 *
 * ## Usage
 * ```kotlin
 * // Capture default safe headers only (simplest usage)
 * RumConfiguration.Builder(applicationId)
 *     .trackResourceHeaders(ResourceHeadersConfiguration.Builder().build())
 *     .build()
 *
 * // Capture defaults + custom headers
 * RumConfiguration.Builder(applicationId)
 *     .trackResourceHeaders(
 *         ResourceHeadersConfiguration.Builder()
 *             .captureRequestHeaders(listOf("X-Request-ID"))
 *             .captureResponseHeaders(listOf("X-RateLimit-Remaining"))
 *             .build()
 *     )
 *     .build()
 *
 * // Capture defaults but exclude specific ones
 * RumConfiguration.Builder(applicationId)
 *     .trackResourceHeaders(
 *         ResourceHeadersConfiguration.Builder()
 *             .excludeResponseHeaders(listOf("Content-Length", "Vary"))
 *             .build()
 *     )
 *     .build()
 *
 * // Capture only custom headers (no defaults)
 * RumConfiguration.Builder(applicationId)
 *     .trackResourceHeaders(
 *         ResourceHeadersConfiguration.Builder()
 *             .excludeDefaultHeaders()
 *             .captureRequestHeaders(listOf("X-Request-ID"))
 *             .captureResponseHeaders(listOf("X-RateLimit-Remaining"))
 *             .build()
 *     )
 *     .build()
 *
 * // Advanced: URL-specific rules with regex matching
 * RumConfiguration.Builder(applicationId)
 *     .trackResourceHeaders(
 *         ResourceHeadersConfiguration.Builder()
 *             .addRule(
 *                 HeaderCaptureRule.Builder()
 *                     .matchUrl(UrlMatcher.prefix("https://api.example.com"))
 *                     .captureRequestHeaders(listOf(HeaderMatcher.exact("X-Custom-Auth")))
 *                     .build()
 *             )
 *             .build()
 *     )
 *     .build()
 * ```
 *
 * ## Default Headers
 * By default, these safe headers are automatically captured (when available):
 *
 * **Request:** Cache-Control, Content-Type, Accept, Accept-Encoding
 *
 * **Response:** Cache-Control, ETag, Age, Expires, Content-Type, Content-Encoding,
 * Vary, Content-Length, Server-Timing, X-Cache
 *
 * ## Forbidden Headers
 * Certain security-sensitive headers are **never captured**, regardless of configuration:
 * Authorization, Proxy-Authorization, Cookie, Set-Cookie, X-API-Key, X-Access-Token,
 * X-Auth-Token, X-Session-Token, X-Forwarded-For, X-Real-IP, CF-Connecting-IP,
 * True-Client-IP, X-CSRF-Token, X-XSRF-Token, X-Security-Token.
 *
 * ## Size Limit
 * Total captured headers are limited to [HEADER_SIZE_LIMIT_BYTES] (2KB).
 */
class ResourceHeadersConfiguration internal constructor(
    internal val rules: List<HeaderCaptureRule>
) {

    /**
     * Returns flattened list of request headers to capture (for simple use cases).
     */
    internal val requestHeadersToCapture: List<HeaderMatcher>
        get() = rules.flatMap { it.requestHeaders }

    /**
     * Returns flattened list of response headers to capture (for simple use cases).
     */
    internal val responseHeadersToCapture: List<HeaderMatcher>
        get() = rules.flatMap { it.responseHeaders }

    /**
     * Builder for creating [ResourceHeadersConfiguration] instances.
     *
     * By default, [DEFAULT_REQUEST_HEADERS] and [DEFAULT_RESPONSE_HEADERS] are captured.
     * Use [excludeDefaultHeaders] to disable them, or [excludeRequestHeaders]/[excludeResponseHeaders]
     * to exclude specific headers.
     */
    class Builder {
        private var includeDefaults: Boolean = true
        private val rules = mutableListOf<HeaderCaptureRule>()
        private var customRequestHeaders: List<String> = emptyList()
        private var customResponseHeaders: List<String> = emptyList()
        private var excludedRequestHeaders: Set<String> = emptySet()
        private var excludedResponseHeaders: Set<String> = emptySet()

        /**
         * Disables capturing of default safe headers.
         *
         * By default, [DEFAULT_REQUEST_HEADERS] and [DEFAULT_RESPONSE_HEADERS] are captured.
         * Call this method to disable them entirely and only capture custom headers.
         *
         * To exclude only specific default headers while keeping the rest, use
         * [excludeRequestHeaders] or [excludeResponseHeaders] instead.
         *
         * @return This builder for chaining.
         * @see DEFAULT_REQUEST_HEADERS
         * @see DEFAULT_RESPONSE_HEADERS
         */
        fun excludeDefaultHeaders(): Builder {
            includeDefaults = false
            return this
        }

        /**
         * Specifies additional request headers to capture.
         *
         * These headers are captured in addition to [DEFAULT_REQUEST_HEADERS] (which are
         * included by default unless [excludeDefaultHeaders] is called).
         *
         * Headers matching [FORBIDDEN_HEADERS] are automatically filtered out.
         *
         * @param headers List of header names (case-insensitive).
         * @return This builder for chaining.
         */
        fun captureRequestHeaders(headers: List<String>): Builder {
            customRequestHeaders = headers.map { it.lowercase() }
            return this
        }

        /**
         * Specifies additional response headers to capture.
         *
         * These headers are captured in addition to [DEFAULT_RESPONSE_HEADERS] (which are
         * included by default unless [excludeDefaultHeaders] is called).
         *
         * Headers matching [FORBIDDEN_HEADERS] are automatically filtered out.
         *
         * @param headers List of header names (case-insensitive).
         * @return This builder for chaining.
         */
        fun captureResponseHeaders(headers: List<String>): Builder {
            customResponseHeaders = headers.map { it.lowercase() }
            return this
        }

        /**
         * Excludes specific request headers from capture.
         *
         * Use this to exclude certain [DEFAULT_REQUEST_HEADERS], or to exclude headers
         * that might be added by [addRule].
         *
         * @param headers List of header names to exclude (case-insensitive).
         * @return This builder for chaining.
         */
        fun excludeRequestHeaders(headers: List<String>): Builder {
            excludedRequestHeaders = headers.map { it.lowercase() }.toSet()
            return this
        }

        /**
         * Excludes specific response headers from capture.
         *
         * Use this to exclude certain [DEFAULT_RESPONSE_HEADERS], or to exclude headers
         * that might be added by [addRule].
         *
         * @param headers List of header names to exclude (case-insensitive).
         * @return This builder for chaining.
         */
        fun excludeResponseHeaders(headers: List<String>): Builder {
            excludedResponseHeaders = headers.map { it.lowercase() }.toSet()
            return this
        }

        /**
         * Adds an advanced header capture rule with URL matching and regex support.
         *
         * @param rule The capture rule to add.
         * @return This builder for chaining.
         * @see HeaderCaptureRule
         */
        fun addRule(rule: HeaderCaptureRule): Builder {
            rules.add(rule)
            return this
        }

        /**
         * Builds the [ResourceHeadersConfiguration] instance.
         *
         * @return A new configuration instance.
         */
        fun build(): ResourceHeadersConfiguration {
            val defaultRequestMatchers = if (includeDefaults) {
                DEFAULT_REQUEST_HEADERS
                    .filter { it !in excludedRequestHeaders }
                    .map { HeaderMatcher.exact(it) }
            } else {
                emptyList()
            }
            val defaultResponseMatchers = if (includeDefaults) {
                DEFAULT_RESPONSE_HEADERS
                    .filter { it !in excludedResponseHeaders }
                    .map { HeaderMatcher.exact(it) }
            } else {
                emptyList()
            }

            val customRequestMatchers = customRequestHeaders
                .filter { it !in FORBIDDEN_HEADERS }
                .filter { it !in excludedRequestHeaders }
                .map { HeaderMatcher.exact(it) }
            val customResponseMatchers = customResponseHeaders
                .filter { it !in FORBIDDEN_HEADERS }
                .filter { it !in excludedResponseHeaders }
                .map { HeaderMatcher.exact(it) }

            // Combine custom headers + defaults into a global rule (no URL filter)
            // Custom headers first (higher priority), then defaults
            val combinedRequestMatchers = (customRequestMatchers + defaultRequestMatchers).distinctBy {
                (it as? HeaderMatcher.Exact)?.name
            }
            val combinedResponseMatchers = (customResponseMatchers + defaultResponseMatchers).distinctBy {
                (it as? HeaderMatcher.Exact)?.name
            }

            val allRules = if (combinedRequestMatchers.isNotEmpty() || combinedResponseMatchers.isNotEmpty()) {
                listOf(
                    HeaderCaptureRule(
                        urlMatcher = null,
                        requestHeaders = combinedRequestMatchers,
                        responseHeaders = combinedResponseMatchers
                    )
                ) + rules
            } else {
                rules
            }

            return ResourceHeadersConfiguration(allRules)
        }
    }

    companion object {
        /**
         * Default request headers captured automatically (unless [Builder.excludeDefaultHeaders]
         * is called).
         *
         * - **Cache-Control**: Caching directives sent by the client
         * - **Content-Type**: Media type of the request body
         * - **Accept**: Media types the client can process
         * - **Accept-Encoding**: Compression algorithms the client supports
         */
        val DEFAULT_REQUEST_HEADERS: List<String> = listOf(
            HttpSpec.Headers.CACHE_CONTROL,
            HttpSpec.Headers.CONTENT_TYPE,
            HttpSpec.Headers.ACCEPT,
            HttpSpec.Headers.ACCEPT_ENCODING
        ).map { it.lowercase() }

        /**
         * Default response headers captured automatically (unless [Builder.excludeDefaultHeaders]
         * is called).
         *
         * Useful for:
         * - **CDN debugging**: Cache-Control, X-Cache, Age, Expires
         * - **Compression issues**: Content-Encoding, Vary
         * - **Asset validation**: ETag, Content-Type
         * - **Backend performance**: Server-Timing
         * - **Payload analysis**: Content-Length
         */
        val DEFAULT_RESPONSE_HEADERS: List<String> = listOf(
            HttpSpec.Headers.CACHE_CONTROL,
            HttpSpec.Headers.ETAG,
            HttpSpec.Headers.AGE,
            HttpSpec.Headers.EXPIRES,
            HttpSpec.Headers.CONTENT_TYPE,
            HttpSpec.Headers.CONTENT_ENCODING,
            HttpSpec.Headers.VARY,
            HttpSpec.Headers.CONTENT_LENGTH,
            HttpSpec.Headers.SERVER_TIMING,
            HttpSpec.Headers.X_CACHE
        ).map { it.lowercase() }

        /**
         * Headers that are **never** captured due to security/privacy concerns.
         *
         * Even if explicitly specified, these headers are automatically filtered out:
         * - **Authentication**: Authorization, Proxy-Authorization, X-API-Key, etc.
         * - **Cookies**: Cookie, Set-Cookie
         * - **Client IP**: X-Forwarded-For, X-Real-IP, CF-Connecting-IP, True-Client-IP
         * - **Security tokens**: X-CSRF-Token, X-XSRF-Token, X-Security-Token
         */
        val FORBIDDEN_HEADERS: Set<String> = setOf(
            // Authentication headers
            "authorization",
            "proxy-authorization",
            "x-api-key",
            "x-access-token",
            "x-auth-token",
            "x-session-token",
            // Cookie headers
            "cookie",
            "set-cookie",
            // IP/Client identification headers
            "x-forwarded-for",
            "x-real-ip",
            "cf-connecting-ip",
            "true-client-ip",
            // Security tokens
            "x-csrf-token",
            "x-xsrf-token",
            "x-security-token"
        )

        /**
         * Maximum total size in bytes for all captured headers combined (2KB).
         */
        const val HEADER_SIZE_LIMIT_BYTES: Int = 2048
    }
}

/**
 * A rule for capturing headers, optionally scoped to specific URLs.
 *
 * @param urlMatcher Optional URL matcher to scope this rule. `null` means all URLs.
 * @param requestHeaders Headers to capture from requests.
 * @param responseHeaders Headers to capture from responses.
 */
data class HeaderCaptureRule(
    val urlMatcher: UrlMatcher? = null,
    val requestHeaders: List<HeaderMatcher> = emptyList(),
    val responseHeaders: List<HeaderMatcher> = emptyList()
) {
    /**
     * Builder for creating [HeaderCaptureRule] instances.
     */
    class Builder {
        private var urlMatcher: UrlMatcher? = null
        private var requestHeaders: List<HeaderMatcher> = emptyList()
        private var responseHeaders: List<HeaderMatcher> = emptyList()

        /**
         * Builds the [HeaderCaptureRule] instance.
         *
         * @return A new rule instance.
         */
        fun build(): HeaderCaptureRule = HeaderCaptureRule(
            urlMatcher = urlMatcher,
            requestHeaders = requestHeaders,
            responseHeaders = responseHeaders
        )
    }
}

/**
 * Matches URLs for scoped header capture rules.
 */
sealed class UrlMatcher {
    /**
     * Checks if the given URL matches this matcher.
     *
     * @param url The URL to check.
     * @return `true` if the URL matches, `false` otherwise.
     */
    abstract fun matches(url: String): Boolean

    /**
     * Matches URLs exactly.
     *
     * @param url The exact URL to match.
     */
    data class Exact(val url: String) : UrlMatcher() {
        override fun matches(url: String): Boolean = this.url == url
    }

    /**
     * Matches URLs against a regular expression.
     *
     * @param pattern The regex pattern.
     */
    data class Pattern(val pattern: Regex) : UrlMatcher() {
        override fun matches(url: String): Boolean = pattern.matches(url)
    }

    /**
     * Matches URLs using a custom predicate function.
     *
     * @param predicate Function that returns `true` if the URL matches.
     */
    data class Predicate(val predicate: (String) -> Boolean) : UrlMatcher() {
        override fun matches(url: String): Boolean = predicate(url)
    }

    companion object {
        /** Creates an exact URL matcher. */
        fun exact(url: String): UrlMatcher = Exact(url)

        /** Creates a regex URL matcher. */
        fun regex(pattern: String): UrlMatcher = Pattern(pattern.toRegex())

        /** Creates a regex URL matcher with options. */
        fun regex(pattern: Regex): UrlMatcher = Pattern(pattern)

        /** Creates a predicate URL matcher. */
        fun predicate(test: (String) -> Boolean): UrlMatcher = Predicate(test)
    }
}

/**
 * Transforms header values before capture.
 * Can be used for partial redaction (e.g., masking tokens).
 */
fun interface HeaderValueMapper {
    /**
     * Transforms the header value.
     * @param value The original header value.
     * @return The transformed value to be captured.
     */
    fun map(value: String): String
}

/**
 * Matches header names for capture.
 */
sealed class HeaderMatcher {
    /**
     * Optional transformation function for header values.
     * Can be used for partial redaction (e.g., masking tokens).
     */
    abstract val valueMapper: HeaderValueMapper?

    /**
     * Matches headers by exact name (case-insensitive).
     *
     * @param name The header name to match (lowercase).
     * @param valueMapper Optional function to transform the header value.
     */
    data class Exact(
        val name: String,
        override val valueMapper: HeaderValueMapper? = null
    ) : HeaderMatcher()

    /**
     * Matches headers by regex pattern.
     *
     * @param pattern The regex pattern to match header names.
     * @param valueMapper Optional function to transform the header value.
     */
    data class Pattern(
        val pattern: Regex,
        override val valueMapper: HeaderValueMapper? = null
    ) : HeaderMatcher()

    companion object {
        /**
         * Creates an exact header name matcher.
         *
         * @param name Header name (case-insensitive).
         * @param valueMapper Optional value transformation.
         * @return A new [Exact] matcher.
         */
        fun exact(
            name: String,
            valueMapper: HeaderValueMapper? = null
        ): HeaderMatcher = Exact(name.lowercase(), valueMapper)

        /**
         * Creates a regex header name matcher.
         *
         * @param pattern Regex pattern for matching header names.
         * @param valueMapper Optional value transformation.
         * @return A new [Pattern] matcher.
         */
        fun regex(
            pattern: String,
            valueMapper: HeaderValueMapper? = null
        ): HeaderMatcher = Pattern(pattern.toRegex(RegexOption.IGNORE_CASE), valueMapper)

        /**
         * Creates a regex header name matcher.
         *
         * @param pattern Regex pattern for matching header names.
         * @param valueMapper Optional value transformation.
         * @return A new [Pattern] matcher.
         */
        fun regex(
            pattern: Regex,
            valueMapper: HeaderValueMapper? = null
        ): HeaderMatcher = Pattern(pattern, valueMapper)
    }
}
