/*
 * Unless explicitly stated otherwise all files in this repository are licensed under the Apache License Version 2.0.
 * This product includes software developed at Datadog (https://www.datadoghq.com/).
 * Copyright 2016-Present Datadog, Inc.
 */

package com.datadog.android.core.configuration

import com.datadog.android.core.internal.utils.devLogger
import java.net.MalformedURLException
import java.net.URL
import java.util.Locale

internal class HostsSanitizer {

    internal fun sanitizeHosts(
        hosts: List<String>,
        warningMessageFormat: String,
        errorMessageFormat: String,
        errorMessageMalformedIpAddress: String
    ): List<String> {
        val validHostNameRegEx = Regex(VALID_HOSTNAME_REGEX)
        val validUrlRegex = Regex(URL_REGEX)
        return hosts.mapNotNull {
            if (it.matches(validUrlRegex)) {
                try {
                    val parsedUrl = URL(it)
                    devLogger.w(
                        warningMessageFormat.format(
                            Locale.US,
                            it,
                            parsedUrl.host
                        )
                    )
                    parsedUrl.host
                } catch (e: MalformedURLException) {
                    devLogger.e(errorMessageFormat.format(Locale.US, it), e)
                    null
                }
            } else if (it.matches(validHostNameRegEx)) {
                it
            } else if (it.toLowerCase(Locale.ENGLISH) == "localhost") {
                // special rule exception to accept `localhost` as a valid domain name
                it
            } else {
                devLogger.e(errorMessageMalformedIpAddress.format(Locale.US, it))
                null
            }
        }
    }

    companion object {
        private const val VALID_IP_REGEX =
            "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}" +
                "([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
        private const val VALID_DOMAIN_REGEX =
            "^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)+" +
                "([A-Za-z]|[A-Za-z][A-Za-z0-9-]*[A-Za-z0-9])$"
        private const val VALID_HOSTNAME_REGEX = "$VALID_IP_REGEX|$VALID_DOMAIN_REGEX"
        private const val URL_REGEX = "^(http|https)://(.*)"
    }
}
