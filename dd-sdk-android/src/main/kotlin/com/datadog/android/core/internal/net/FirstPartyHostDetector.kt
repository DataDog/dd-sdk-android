/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.internal.net

import java.util.Locale
import okhttp3.HttpUrl

internal class FirstPartyHostDetector(
    hosts: List<String>
) {
    // As per
    private val knownHosts = hosts.map { it.toLowerCase(Locale.US) }

    fun isFirstPartyUrl(url: HttpUrl): Boolean {
        val host = url.host()
        return knownHosts.any {
            host == it || host.endsWith(".$it")
        }
    }

    fun isFirstPartyUrl(url: String): Boolean {
        val httpUrl = HttpUrl.parse(url) ?: return false
        return isFirstPartyUrl(httpUrl)
    }
}
