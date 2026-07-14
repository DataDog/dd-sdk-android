/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.lint.InternalApi
import com.datadog.android.trace.TracingHeaderType
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

private const val WILDCARD = '*'
private const val LABEL_SEPARATOR = "."

// What a "*" expands to: one or more host characters, spanning subdomain labels, so "*.example.com"
// matches both "api.example.com" and "a.b.example.com".
private const val WILDCARD_LABEL_SEGMENT = "[a-z0-9.-]+"

// Regex metacharacters (other than '*') that are escaped so any pattern yields a valid regex.
private const val REGEX_METACHARACTERS = "\\^$.|?+()[]{}"

/**
 * Default implementation of [FirstPartyHostHeaderTypeResolver].
 *
 * @param hosts [Map] of hosts and associated tracing header types to initialize instance with.
 */
@InternalApi
class DefaultFirstPartyHostHeaderTypeResolver(
    hosts: Map<String, Set<TracingHeaderType>>
) : FirstPartyHostHeaderTypeResolver {

    internal var knownHosts: Map<String, Set<TracingHeaderType>> = emptyMap()
        private set

    private var wildcardMatchers: Map<String, Regex> = emptyMap()

    init {
        updateKnownHosts(hosts.entries.associate { it.key.lowercase(Locale.US) to it.value })
    }

    /** @inheritdoc */
    override fun isFirstPartyUrl(url: HttpUrl): Boolean {
        val host = url.host
        return knownHosts.keys.any { matchesHost(host, it) }
    }

    /** @inheritdoc */
    override fun isFirstPartyUrl(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return isFirstPartyUrl(httpUrl)
    }

    /** @inheritdoc */
    override fun headerTypesForUrl(url: String): Set<TracingHeaderType> {
        val httpUrl = url.toHttpUrlOrNull() ?: return emptySet()
        return headerTypesForUrl(httpUrl)
    }

    /** @inheritdoc */
    override fun headerTypesForUrl(url: HttpUrl): Set<TracingHeaderType> {
        val host = url.host

        // Exact host wins; otherwise the most-specific match (longest matching key) wins, so a
        // more-specific host or pattern can override a broader one for its subtree.
        return knownHosts[host]
            ?: knownHosts.entries
                .filter { matchesHost(host, it.key) }
                .maxByOrNull { it.key.length }
                ?.value
            ?: emptySet()
    }

    /** @inheritdoc */
    override fun getAllHeaderTypes(): Set<TracingHeaderType> {
        return knownHosts.values.flatten().toSet()
    }

    /** @inheritdoc */
    override fun isEmpty(): Boolean {
        return knownHosts.isEmpty()
    }

    internal fun addKnownHostsWithHeaderTypes(
        hostsWithHeaderTypes: Map<String, Set<TracingHeaderType>>
    ) {
        updateKnownHosts(
            knownHosts + hostsWithHeaderTypes.entries.associate {
                it.key.lowercase(Locale.US) to it.value
            }
        )
    }

    private fun updateKnownHosts(newHosts: Map<String, Set<TracingHeaderType>>) {
        knownHosts = newHosts
        wildcardMatchers = newHosts.keys
            .filter { it.isWildcardPattern() }
            .associateWith { it.toHostMatchingRegex() }
    }

    // A wildcard pattern has a single "*" at a label boundary (followed by "."). Only these get a
    // matcher; a bare "*" is excluded so it is never treated as match-all.
    private fun String.isWildcardPattern(): Boolean =
        count { it == WILDCARD } == 1 && substringAfter(WILDCARD).startsWith(LABEL_SEPARATOR)

    // A wildcard pattern matches via its precompiled regex; anything else matches itself or any of
    // its subdomains.
    private fun matchesHost(host: String, pattern: String): Boolean {
        val matcher = wildcardMatchers[pattern]
        return if (matcher != null) {
            matcher.matchEntire(host) != null
        } else {
            host == pattern || host.endsWith(".$pattern")
        }
    }

    // Turns a wildcard pattern into a regex where "*" matches one or more host characters and every
    // other metacharacter is escaped, so "*.example.com" matches "api.example.com" but not
    // "example.com" and any input yields a valid regex.
    private fun String.toHostMatchingRegex(): Regex {
        val pattern = this
        return buildString {
            for (char in pattern) {
                when (char) {
                    WILDCARD -> append(WILDCARD_LABEL_SEGMENT)
                    in REGEX_METACHARACTERS -> append('\\').append(char)
                    else -> append(char)
                }
            }
        }.toRegex()
    }
}
