/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.rum.internal.net

import com.datadog.android.api.InternalLogger
import com.datadog.android.rum.configuration.HeaderMatcher
import com.datadog.android.rum.configuration.ResourceHeadersConfiguration

/**
 * Extracts HTTP headers from network requests/responses according to the provided configuration.
 *
 * This class handles:
 * - Matching headers by exact name or regex pattern
 * - Filtering forbidden headers (sensitive data like Authorization, Cookie, etc.)
 * - Enforcing the size limit for captured headers
 * - Applying value transformations (mappers) when configured
 * - Normalizing header names to lowercase
 */
internal class ResourceHeadersExtractor(
    private val configuration: ResourceHeadersConfiguration,
    private val internalLogger: InternalLogger
) {

    /**
     * Extracts request headers from the provided headers map.
     *
     * @param headers Map of header names to their values (may have multiple values per header).
     * @param url Optional URL for URL-scoped rules matching.
     * @return Map of captured headers within the size limit, empty map if no headers to capture.
     */
    fun extractRequestHeaders(
        headers: Map<String, List<String>>,
        url: String? = null
    ): Map<String, String> {
        val matchers = configuration.getRequestMatchersForUrl(url)
        return extractHeaders(headers, matchers)
    }

    /**
     * Extracts response headers from the provided headers map.
     *
     * @param headers Map of header names to their values (may have multiple values per header).
     * @param url Optional URL for URL-scoped rules matching.
     * @return Map of captured headers within the size limit, empty map if no headers to capture.
     */
    fun extractResponseHeaders(
        headers: Map<String, List<String>>,
        url: String? = null
    ): Map<String, String> {
        val matchers = configuration.getResponseMatchersForUrl(url)
        return extractHeaders(headers, matchers)
    }

    private fun extractHeaders(
        headers: Map<String, List<String>>,
        matchers: List<HeaderMatcher>
    ): Map<String, String> {
        if (matchers.isEmpty()) {
            return emptyMap()
        }

        val result = mutableMapOf<String, String>()
        var currentSize = 0

        // Normalize input headers to lowercase for case-insensitive matching
        val normalizedHeaders = headers.mapKeys { it.key.lowercase() }

        // Process each matcher in order of priority
        for (matcher in matchers) {
            val matchedHeaders = findMatchingHeaders(normalizedHeaders, matcher)

            for ((headerName, headerValue) in matchedHeaders) {
                // Skip if already captured, forbidden, or would exceed size limit
                val shouldCapture = headerName !in result &&
                    headerName !in ResourceHeadersConfiguration.FORBIDDEN_HEADERS &&
                    canFitHeader(headerName, headerValue, matcher, currentSize)

                if (shouldCapture) {
                    val finalValue = matcher.valueMapper?.map(headerValue) ?: headerValue
                    val entrySize = headerName.length + 1 + finalValue.length
                    result[headerName] = finalValue
                    currentSize += entrySize
                }
            }
        }

        return result
    }

    private fun canFitHeader(
        headerName: String,
        headerValue: String,
        matcher: HeaderMatcher,
        currentSize: Int
    ): Boolean {
        val finalValue = matcher.valueMapper?.map(headerValue) ?: headerValue
        val entrySize = headerName.length + 1 + finalValue.length
        val fits = currentSize + entrySize <= ResourceHeadersConfiguration.HEADER_SIZE_LIMIT_BYTES
        if (!fits) {
            internalLogger.log(
                InternalLogger.Level.DEBUG,
                InternalLogger.Target.USER,
                { "Header '$headerName' skipped: would exceed size limit" }
            )
        }
        return fits
    }

    private fun findMatchingHeaders(
        normalizedHeaders: Map<String, List<String>>,
        matcher: HeaderMatcher
    ): List<Pair<String, String>> {
        return when (matcher) {
            is HeaderMatcher.Exact -> {
                val values = normalizedHeaders[matcher.name]
                if (values.isNullOrEmpty()) {
                    emptyList()
                } else {
                    listOf(matcher.name to values.joinToString(", "))
                }
            }
            is HeaderMatcher.Pattern -> {
                normalizedHeaders
                    .filter { (name, values) ->
                        matcher.pattern.matches(name) && values.isNotEmpty()
                    }
                    .map { (name, values) ->
                        name to values.joinToString(", ")
                    }
            }
        }
    }

    private fun ResourceHeadersConfiguration.getRequestMatchersForUrl(url: String?): List<HeaderMatcher> {
        return rules
            .filter { rule -> rule.urlMatcher == null || (url != null && rule.urlMatcher.matches(url)) }
            .flatMap { it.requestHeaders }
    }

    private fun ResourceHeadersConfiguration.getResponseMatchersForUrl(url: String?): List<HeaderMatcher> {
        return rules
            .filter { rule -> rule.urlMatcher == null || (url != null && rule.urlMatcher.matches(url)) }
            .flatMap { it.responseHeaders }
    }
}
