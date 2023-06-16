/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import com.datadog.android.trace.TracingHeaderType
import okhttp3.HttpUrl
import java.util.Locale

/**
 * Default implementation of [FirstPartyHostHeaderTypeResolver].
 *
 * @param hosts [Map] of hosts and associated tracing header types to initialize instance with.
 */
class DefaultFirstPartyHostHeaderTypeResolver(
    hosts: Map<String, Set<TracingHeaderType>>
) : FirstPartyHostHeaderTypeResolver {

    internal var knownHosts = hosts.entries.associate { it.key.lowercase(Locale.US) to it.value }
        private set

    /** @inheritdoc */
    override fun isFirstPartyUrl(url: HttpUrl): Boolean {
        val host = url.host()
        return knownHosts.keys.any {
            it == "*" || host == it || host.endsWith(".$it")
        }
    }

    /** @inheritdoc */
    override fun isFirstPartyUrl(url: String): Boolean {
        val httpUrl = HttpUrl.parse(url) ?: return false
        return isFirstPartyUrl(httpUrl)
    }

    /** @inheritdoc */
    override fun headerTypesForUrl(url: String): Set<TracingHeaderType> {
        val httpUrl = HttpUrl.parse(url) ?: return emptySet()
        return headerTypesForUrl(httpUrl)
    }

    /** @inheritdoc */
    override fun headerTypesForUrl(url: HttpUrl): Set<TracingHeaderType> {
        val host = url.host()
        val filteredHosts = knownHosts.filter {
            it.key == "*" || it.key == host || host.endsWith(".${it.key}")
        }
        return filteredHosts.values.flatten().toSet()
    }

    /** @inheritdoc */
    override fun getAllHeaderTypes(): Set<TracingHeaderType> {
        return knownHosts.values.flatten().toSet()
    }

    /** @inheritdoc */
    override fun isEmpty(): Boolean {
        return knownHosts.isEmpty()
    }

    internal fun addKnownHosts(hosts: List<String>) {
        knownHosts = knownHosts + hosts.associate {
            it.lowercase(Locale.US) to setOf(TracingHeaderType.DATADOG)
        }
    }

    internal fun addKnownHostsWithHeaderTypes(
        hostsWithHeaderTypes: Map<String, Set<TracingHeaderType>>
    ) {
        knownHosts = knownHosts + hostsWithHeaderTypes.entries.associate {
            it.key.lowercase(Locale.US) to it.value
        }
    }
}
