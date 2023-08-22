/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.data.upload

import okhttp3.Dns
import java.net.InetAddress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

internal class RotatingDnsResolver(
    private val delegate: Dns = Dns.SYSTEM,
    private val ttl: Duration = TTL_30_MIN
) : Dns {

    data class ResolvedHost(
        val hostname: String,
        val addresses: MutableList<InetAddress>
    ) {
        private val resolutionTimestamp: Long = System.nanoTime()

        fun getAge(): Duration {
            return (System.nanoTime() - resolutionTimestamp).nanoseconds
        }

        fun rotate() {
            val first = addresses.removeFirstOrNull()
            if (first != null) {
                addresses.add(first)
            }
        }
    }

    private val knownHosts = mutableMapOf<String, ResolvedHost>()

    // region Dns

    override fun lookup(hostname: String): List<InetAddress> {
        val knownHost = knownHosts[hostname]

        return if (knownHost != null && isValid(knownHost)) {
            knownHost.rotate()
            knownHost.addresses
        } else {
            @Suppress("UnsafeThirdPartyFunctionCall") // handled by caller
            val result = delegate.lookup(hostname)
            knownHosts[hostname] = ResolvedHost(hostname, result.toMutableList())
            result
        }
    }

    // endregion

    // region Internal

    private fun isValid(knownHost: ResolvedHost): Boolean {
        return knownHost.getAge() < ttl && knownHost.addresses.isNotEmpty()
    }

    // endregion

    companion object {
        val TTL_30_MIN: Duration = 30.minutes
    }
}
